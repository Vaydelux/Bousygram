"""Telegram Bot handler that turns forwarded videos into signed stream links."""

import asyncio
from typing import Optional

from telegram import Update
from telegram.ext import ContextTypes

from config import ALLOWED_USER_IDS, BOT_MESSAGE_CLEANUP_SECONDS, SERVER_URL, TOKEN_TTL
from tokens import create_token


async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
    message = update.message
    if not message:
        return

    # Stream URLs are capabilities: never post them into a group where other members could use them.
    if getattr(message.chat, "type", None) != "private":
        return

    user_id = message.from_user.id if message.from_user else None
    # Defence in depth: never become a public media-link service if configuration is empty.
    if not ALLOWED_USER_IDS or user_id not in ALLOWED_USER_IDS:
        return

    media = message.video or message.document
    if not media:
        await message.reply_text("No video or document found in this message.")
        return

    status_message = await message.reply_text("Generating stream link...")

    try:
        token = await _create_stream_token(context, media)
        link_message = await message.reply_text(
            f"Download Link: {SERVER_URL}/stream/{token}\n"
            f"Valid for {TOKEN_TTL // 60} minutes."
        )
        context.application.create_task(
            delete_bot_chat_messages_later(
                context,
                message.chat_id,
                [message.message_id, status_message.message_id, link_message.message_id],
                BOT_MESSAGE_CLEANUP_SECONDS,
            )
        )
    except Exception as error:
        await message.reply_text(f"Error: {error}")


async def _create_stream_token(
    context: ContextTypes.DEFAULT_TYPE, media: object
) -> str:
    telegram_file = await context.bot.get_file(media.file_id)
    mime = getattr(media, "mime_type", None) or "video/mp4"
    size = getattr(media, "file_size", 0) or 0
    return create_token(telegram_file.file_path, mime, size)


async def delete_bot_chat_messages_later(
    context: ContextTypes.DEFAULT_TYPE,
    chat_id: int,
    message_ids: list[int],
    delay_seconds: int,
) -> None:
    """Delete the bot's own replies after a delay so links do not linger."""
    if delay_seconds <= 0:
        return

    await asyncio.sleep(delay_seconds)
    for message_id in message_ids:
        await _try_delete_message(context, chat_id, message_id)


async def _try_delete_message(
    context: ContextTypes.DEFAULT_TYPE, chat_id: int, message_id: int
) -> Optional[bool]:
    try:
        return await context.bot.delete_message(chat_id=chat_id, message_id=message_id)
    except Exception:
        # Deletion is best-effort (message may already be gone or lack permissions).
        return None
