"""
Bousygram Fast-Link Proxy Server.

The server does not store media. It keeps short-lived signed tokens that map
to Telegram Bot API file paths, then proxies bytes from Telegram's CDN to the
player while preserving HTTP Range requests for seeking.

Layout:
    config.py   - environment-driven settings
    tokens.py   - signed token store (create / resolve / cleanup / revoke)
    streaming.py- HTTP Range parsing helpers
    bot.py      - Telegram Bot message handler producing stream links
    server.py   - FastAPI app wiring the pieces together (this module)
"""

import asyncio
import os
import re
from contextlib import asynccontextmanager
from typing import Optional

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import StreamingResponse
from telegram import Update
from telegram.ext import Application, MessageHandler, filters

from bot import handle_message
from config import (
    BOT_TOKEN, STREAM_CHUNK_BYTES, TELEGRAM_CDN_BASE, TOKEN_CLEANUP_INTERVAL_SECONDS,
    validate_runtime_config,
)
from streaming import parse_range
from tokens import cleanup_expired_tokens, create_token, resolve_token, token_store

__all__ = [
    "fastapi_app",
    "create_token",
    "resolve_token",
    "cleanup_expired_tokens",
    "parse_range",
    "token_store",
]

_cleanup_task: Optional[asyncio.Task] = None


async def _cleanup_expired_tokens_forever() -> None:
    while True:
        cleanup_expired_tokens()
        await asyncio.sleep(TOKEN_CLEANUP_INTERVAL_SECONDS)


def _telegram_file_url(file_path: str) -> str:
    return f"{TELEGRAM_CDN_BASE}{BOT_TOKEN}/{file_path}"


def _upstream_headers(request: Request) -> dict[str, str]:
    """Forward only the conditional/range headers that matter for seeking."""
    headers = {}
    if range_header := request.headers.get("Range"):
        headers["Range"] = range_header
    if if_range := request.headers.get("If-Range"):
        headers["If-Range"] = if_range
    return headers


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _cleanup_task
    validate_runtime_config()
    bot_app = Application.builder().token(BOT_TOKEN).build()
    bot_app.add_handler(MessageHandler(filters.ALL, handle_message))

    async with bot_app:
        _cleanup_task = asyncio.create_task(_cleanup_expired_tokens_forever())
        await bot_app.start()
        await bot_app.updater.start_polling(allowed_updates=Update.ALL_TYPES)
        try:
            yield
        finally:
            if _cleanup_task:
                _cleanup_task.cancel()
            await bot_app.updater.stop()
            await bot_app.stop()


fastapi_app = FastAPI(lifespan=lifespan)


@fastapi_app.get("/stream/{token}")
async def stream_video(token: str, request: Request):
    entry = resolve_token(token)
    if not entry:
        raise HTTPException(status_code=404, detail="Link expired or invalid.")

    cleanup_expired_tokens()
    size = int(entry.get("size") or 0)
    range_header = request.headers.get("Range")
    range_pair = None
    if range_header:
        range_pair = parse_range(range_header, size)
        if range_pair is None:
            raise HTTPException(
                status_code=416,
                detail="Requested byte range is not satisfiable.",
                headers={"Accept-Ranges": "bytes", "Content-Range": f"bytes */{max(size, 0)}"},
            )

    timeout = httpx.Timeout(connect=10.0, read=60.0, write=15.0, pool=10.0)
    client = httpx.AsyncClient(timeout=timeout, follow_redirects=True)
    try:
        upstream_request = client.build_request(
            "GET", _telegram_file_url(entry["file_path"]), headers=_upstream_headers(request)
        )
        upstream = await client.send(upstream_request, stream=True)
    except httpx.TimeoutException as error:
        await client.aclose()
        raise HTTPException(status_code=504, detail="Telegram CDN request timed out.") from error
    except httpx.RequestError as error:
        await client.aclose()
        raise HTTPException(status_code=502, detail="Could not connect to Telegram CDN.") from error
    except Exception:
        await client.aclose()
        raise

    expected_status = 206 if range_pair else 200
    if upstream.status_code != expected_status:
        upstream_status = upstream.status_code
        await upstream.aclose()
        await client.aclose()
        raise HTTPException(
            status_code=502,
            detail=f"Telegram CDN returned unexpected status {upstream_status} for this request.",
        )

    response_headers = {
        "Accept-Ranges": "bytes",
        "Content-Type": entry.get("mime") or "application/octet-stream",
        "Cache-Control": "no-store",
        "X-Content-Type-Options": "nosniff",
    }
    if range_pair:
        upstream_range = upstream.headers.get("Content-Range", "")
        match = re.fullmatch(r"bytes ([0-9]+)-([0-9]+)/([0-9]+|\*)", upstream_range.strip())
        if not match:
            await upstream.aclose()
            await client.aclose()
            raise HTTPException(status_code=502, detail="Telegram CDN returned an invalid Content-Range.")
        actual_start, actual_end = int(match.group(1)), int(match.group(2))
        requested_start, requested_end = range_pair
        if actual_start != requested_start or actual_end < actual_start or actual_end > requested_end:
            await upstream.aclose()
            await client.aclose()
            raise HTTPException(status_code=502, detail="Telegram CDN returned a mismatched byte range.")
        response_headers["Content-Range"] = upstream_range
        actual_length = actual_end - actual_start + 1
        content_length = upstream.headers.get("Content-Length")
        if content_length and content_length.isdigit() and int(content_length) != actual_length:
            await upstream.aclose()
            await client.aclose()
            raise HTTPException(status_code=502, detail="Telegram CDN returned an inconsistent range length.")
        response_headers["Content-Length"] = content_length or str(actual_length)
    else:
        content_length = upstream.headers.get("Content-Length")
        if content_length:
            response_headers["Content-Length"] = content_length
        elif size > 0:
            response_headers["Content-Length"] = str(size)

    async def stream_bytes():
        try:
            async for chunk in upstream.aiter_bytes(chunk_size=STREAM_CHUNK_BYTES):
                yield chunk
        finally:
            await upstream.aclose()
            await client.aclose()

    return StreamingResponse(stream_bytes(), status_code=expected_status, headers=response_headers)


@fastapi_app.post("/revoke/{token}")
async def revoke_token(token: str):
    return {
        "status": "revoked" if token_store.revoke(token) else "not_found",
        "tokens_active": token_store.active_count,
    }


@fastapi_app.get("/health")
async def health():
    return {"status": "ok", "tokens_active": token_store.active_count}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "server:fastapi_app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", "8080")),
        log_level="info",
    )
