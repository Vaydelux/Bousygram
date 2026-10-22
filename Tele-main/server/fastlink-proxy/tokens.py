"""Short-lived HMAC-signed stream tokens mapped to Telegram Bot file paths.

The proxy never stores media; it only keeps an in-memory table of tokens that
carry enough metadata (file path, MIME type, size, expiry) to authorize a
streaming request and validate its signature.
"""

import hashlib
import hmac
import secrets
import time
from typing import Optional

from config import SIGNING_KEY, TOKEN_TTL


class TokenStore:
    """In-memory store of signed, expiring stream tokens."""

    MAX_ACTIVE_TOKENS = 10_000

    def __init__(self) -> None:
        self._tokens: dict[str, dict] = {}

    def create(self, file_path: str, mime: str, size: int) -> str:
        if len(self._tokens) >= self.MAX_ACTIVE_TOKENS:
            self.cleanup_expired()
            if len(self._tokens) >= self.MAX_ACTIVE_TOKENS:
                raise RuntimeError("Active stream limit reached; wait for older links to expire")
        expires_at = int(time.time()) + TOKEN_TTL
        token = secrets.token_urlsafe(24)
        self._tokens[token] = {
            "file_path": file_path,
            "expires_at": expires_at,
            "sig": self._sign(token, expires_at),
            "mime": mime,
            "size": size,
        }
        return token

    def resolve(self, token: str) -> Optional[dict]:
        entry = self._tokens.get(token)
        if not entry:
            return None
        if time.time() > entry["expires_at"]:
            del self._tokens[token]
            return None
        expected = self._sign(token, int(entry["expires_at"]))
        if not hmac.compare_digest(entry["sig"], expected):
            return None
        return entry

    def cleanup_expired(self) -> None:
        now = time.time()
        for token, entry in list(self._tokens.items()):
            if now > entry["expires_at"]:
                del self._tokens[token]

    def revoke(self, token: str) -> bool:
        """Remove a token; returns True when a token actually existed."""
        return self._tokens.pop(token, None) is not None

    @property
    def active_count(self) -> int:
        return len(self._tokens)

    @staticmethod
    def _sign(token: str, expires_at: int) -> str:
        message = f"{token}:{expires_at}".encode()
        return hmac.new(SIGNING_KEY.encode(), message, hashlib.sha256).hexdigest()


token_store = TokenStore()


def create_token(file_path: str, mime: str, size: int) -> str:
    return token_store.create(file_path, mime, size)


def resolve_token(token: str) -> Optional[dict]:
    return token_store.resolve(token)


def cleanup_expired_tokens() -> None:
    token_store.cleanup_expired()
