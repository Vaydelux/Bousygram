"""
Bousygram Fast-Link Proxy Server.

The server does not store media. It keeps short-lived signed tokens that map
to Telegram Bot API file paths, then proxies bytes from Telegram's CDN to the
player while preserving HTTP Range requests for seeking.
"""

import hashlib
import hmac
import os
import re
import secrets
import time
from contextlib import asynccontextmanager
from typing import Optional

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse
from telegram import Update
from telegram.ext import Application, ContextTypes, MessageHandler, filters

BOT_TOKEN = os.getenv("BOT_TOKEN", "YOUR_BOT_TOKEN_HERE")
SIGNING_KEY = os.getenv("SIGNING_KEY", secrets.token_hex(32))
SERVER_URL = os.getenv("SERVER_URL", "https://your-server.com").rstrip("/")
TOKEN_TTL = int(os.getenv("TOKEN_TTL", "3600"))

ALLOWED_USER_IDS: set[int] = {
    int(value)
    for value in os.getenv("ALLOWED_USER_IDS", "").split(",")
    if value.strip()
}

_token_store: dict[str, dict] = {}
_range_pattern = re.compile(r"bytes=(\d+)-(\d*)$")


def _sign_token(token: str, expires_at: int) -> str:
    message = f"{token}:{expires_at}".encode()
    return hmac.new(SIGNING_KEY.encode(), message, hashlib.sha256).hexdigest()


def create_token(file_path: str, mime: str, size: int) -> str:
    expires_at = int(time.time()) + TOKEN_TTL
    token = secrets.token_urlsafe(24)
    _token_store[token] = {
        "file_path": file_path,
        "expires_at": expires_at,
        "sig": _sign_token(token, expires_at),
        "mime": mime,
        "size": size,
    }
    return token


def resolve_token(token: str) -> Optional[dict]:
    entry = _token_store.get(token)
    if not entry:
        return None
    if time.time() > entry["expires_at"]:
        del _token_store[token]
        return None
    expected = _sign_token(token, int(entry["expires_at"]))
    if not hmac.compare_digest(entry["sig"], expected):
        return None
    return entry


def cleanup_expired_tokens() -> None:
    now = time.time()
    for token, entry in list(_token_store.items()):
        if now > entry["expires_at"]:
            del _token_store[token]


def parse_range(range_header: str, size: int) -> Optional[tuple[int, int]]:
    if size <= 0:
        return None
    match = _range_pattern.match(range_header.strip())
    if not match:
        return None
    start = int(match.group(1))
    end_text = match.group(2)
    end = int(end_text) if end_text else size - 1
    if start >= size or end < start:
        return None
    return start, min(end, size - 1)


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    message = update.message
    if not message:
        return

    user_id = message.from_user.id if message.from_user else None
    if ALLOWED_USER_IDS and user_id not in ALLOWED_USER_IDS:
        await message.reply_text("Unauthorized.")
        return

    media = message.video or message.document
    if not media:
        await message.reply_text("No video or document found in this message.")
        return

    await message.reply_text("Generating stream link...")

    try:
        telegram_file = await context.bot.get_file(media.file_id)
        mime = getattr(media, "mime_type", None) or "video/mp4"
        size = getattr(media, "file_size", 0) or 0
        token = create_token(telegram_file.file_path, mime, size)
        await message.reply_text(
            f"Download Link: {SERVER_URL}/stream/{token}\n"
            f"Valid for {TOKEN_TTL // 60} minutes."
        )
    except Exception as error:
        await message.reply_text(f"Error: {error}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    bot_app = Application.builder().token(BOT_TOKEN).build()
    bot_app.add_handler(MessageHandler(filters.ALL, handle_message))

    async with bot_app:
        await bot_app.start()
        await bot_app.updater.start_polling(allowed_updates=Update.ALL_TYPES)
        yield
        await bot_app.updater.stop()
        await bot_app.stop()


fastapi_app = FastAPI(lifespan=lifespan)


@fastapi_app.get("/stream/{token}")
async def stream_video(token: str, request: Request):
    entry = resolve_token(token)
    if not entry:
        raise HTTPException(status_code=404, detail="Link expired or invalid.")

    cleanup_expired_tokens()

    telegram_url = f"https://api.telegram.org/file/bot{BOT_TOKEN}/{entry['file_path']}"
    range_header = request.headers.get("Range")
    range_pair = parse_range(range_header, entry["size"]) if range_header else None

    upstream_headers = {}
    if range_header:
        upstream_headers["Range"] = range_header
    if request.headers.get("If-Range"):
        upstream_headers["If-Range"] = request.headers["If-Range"]

    async def stream_bytes():
        async with httpx.AsyncClient(timeout=None, follow_redirects=True) as client:
            async with client.stream("GET", telegram_url, headers=upstream_headers) as response:
                if response.status_code not in (200, 206):
                    raise HTTPException(status_code=response.status_code, detail="Telegram CDN request failed.")
                async for chunk in response.aiter_bytes(chunk_size=256 * 1024):
                    yield chunk

    response_headers = {
        "Accept-Ranges": "bytes",
        "Content-Type": entry["mime"],
        "Cache-Control": "no-store",
    }
    status_code = 206 if range_header else 200

    if range_pair:
        start, end = range_pair
        response_headers["Content-Range"] = f"bytes {start}-{end}/{entry['size']}"
        response_headers["Content-Length"] = str(end - start + 1)
    elif entry["size"] and not range_header:
        response_headers["Content-Length"] = str(entry["size"])

    return StreamingResponse(
        stream_bytes(),
        status_code=status_code,
        headers=response_headers,
        media_type=entry["mime"],
    )


@fastapi_app.get("/health")
async def health():
    return {"status": "ok", "tokens_active": len(_token_store)}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("server:fastapi_app", host="0.0.0.0", port=8080, log_level="info")
