"""Strict HTTP Range request helpers for proxied Telegram CDN streams."""

import re
from typing import Optional

_RANGE_PATTERN = re.compile(r"^bytes=([0-9]*)-([0-9]*)$", re.IGNORECASE)


def parse_range(range_header: str, size: int) -> Optional[tuple[int, int]]:
    """Return an inclusive (start, end) byte window, or None if it is invalid.

    Supports a single closed, open-ended, or suffix byte range. Multi-range and
    malformed requests are rejected instead of being mistaken for full-file GETs.
    """
    if size <= 0:
        return None
    value = range_header.strip()
    if "," in value:
        return None
    match = _RANGE_PATTERN.fullmatch(value)
    if not match:
        return None
    start_text, end_text = match.groups()
    if not start_text and not end_text:
        return None
    # Reject absurdly long integer fields before int() can spend time parsing them.
    if len(start_text) > 20 or len(end_text) > 20:
        return None

    if not start_text:
        suffix_length = int(end_text)
        if suffix_length <= 0:
            return None
        return max(size - suffix_length, 0), size - 1

    start = int(start_text)
    if start >= size:
        return None
    if not end_text:
        return start, size - 1

    end = int(end_text)
    if end < start:
        return None
    return start, min(end, size - 1)
