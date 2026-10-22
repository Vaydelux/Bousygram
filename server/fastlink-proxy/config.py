"""Environment-driven configuration for the Bousygram Fast-Link proxy."""

import os
from urllib.parse import urlparse


def _int_env(name: str, default: int) -> int:
    """Read an integer environment variable, falling back to the default."""
    raw = os.getenv(name, "")
    try:
        return int(raw) if raw.strip() else default
    except ValueError:
        return default


BOT_TOKEN = os.getenv("BOT_TOKEN", "").strip()
SIGNING_KEY = os.getenv("SIGNING_KEY", "").strip()
SERVER_URL = os.getenv("SERVER_URL", "").strip().rstrip("/")
TOKEN_TTL = _int_env("TOKEN_TTL", 3600)
BOT_MESSAGE_CLEANUP_SECONDS = _int_env("BOT_MESSAGE_CLEANUP_SECONDS", 60)

def _parse_allowed_user_ids(raw: str) -> tuple[set[int], list[str]]:
    user_ids: set[int] = set()
    invalid: list[str] = []
    for value in raw.split(","):
        candidate = value.strip()
        if not candidate:
            continue
        try:
            user_ids.add(int(candidate))
        except ValueError:
            invalid.append(candidate)
    return user_ids, invalid


ALLOWED_USER_IDS, _INVALID_ALLOWED_USER_IDS = _parse_allowed_user_ids(
    os.getenv("ALLOWED_USER_IDS", "")
)

# Telegram Bot API CDN base used to pull file bytes for range-proxying.
TELEGRAM_CDN_BASE = "https://api.telegram.org/file/bot"

# Chunk size (bytes) forwarded to players while streaming from the Telegram CDN.
STREAM_CHUNK_BYTES = 256 * 1024

# Interval between background sweeps that drop expired stream tokens.
TOKEN_CLEANUP_INTERVAL_SECONDS = 60


def validate_runtime_config() -> None:
    """Fail closed on placeholder credentials or a publicly accessible untrusted bot."""
    problems: list[str] = []
    if not BOT_TOKEN or "replace" in BOT_TOKEN.lower() or "your_bot" in BOT_TOKEN.lower():
        problems.append("BOT_TOKEN must be set to a real BotFather token")
    if len(SIGNING_KEY) < 32 or "replace" in SIGNING_KEY.lower():
        problems.append("SIGNING_KEY must be a stable secret of at least 32 characters")
    if _INVALID_ALLOWED_USER_IDS:
        problems.append("ALLOWED_USER_IDS must contain only numeric Telegram user IDs")
    if not ALLOWED_USER_IDS:
        problems.append("ALLOWED_USER_IDS must contain at least one trusted Telegram user ID")
    parsed_url = urlparse(SERVER_URL)
    if not parsed_url.hostname or "your-domain" in SERVER_URL.lower() or "your-server" in SERVER_URL.lower():
        problems.append("SERVER_URL must be the public base URL of this deployment")
    elif parsed_url.scheme != "https" and parsed_url.hostname not in {"localhost", "127.0.0.1"}:
        problems.append("SERVER_URL must use HTTPS outside localhost")
    if not 60 <= TOKEN_TTL <= 24 * 60 * 60:
        problems.append("TOKEN_TTL must be between 60 and 86400 seconds")
    if TOKEN_CLEANUP_INTERVAL_SECONDS < 5:
        problems.append("TOKEN_CLEANUP_INTERVAL_SECONDS must be at least 5 seconds")
    if problems:
        raise RuntimeError("Invalid Fast-Link configuration: " + "; ".join(problems))
