"""Route-level Fast-Link tests with a fake Telegram CDN; no real bot/network used."""

import asyncio
import importlib
import os
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import patch

# Use synthetic credentials so these tests never depend on deployment secrets.
# The app lifespan is not started, so a bot connection is never attempted.
os.environ["BOT_TOKEN"] = "123456:mock-bot-token"
os.environ["SIGNING_KEY"] = "mock-signing-key-long-enough-to-be-at-least-32-characters"
os.environ["SERVER_URL"] = "https://mock.example.test"
os.environ["ALLOWED_USER_IDS"] = "12345"

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

# Some lightweight development environments do not install the optional Telegram bot
# dependency. Stub only its import-time symbols; the real FastAPI/httpx route code is tested.
try:
    import telegram  # noqa: F401
except ModuleNotFoundError:
    telegram = types.ModuleType("telegram")

    class Update:
        ALL_TYPES = []

    telegram.Update = Update
    telegram_ext = types.ModuleType("telegram.ext")

    class Application:
        pass

    class MessageHandler:
        def __init__(self, *args, **kwargs):
            pass

    class Filters:
        ALL = object()

    class ContextTypes:
        DEFAULT_TYPE = object

    telegram_ext.Application = Application
    telegram_ext.MessageHandler = MessageHandler
    telegram_ext.filters = Filters()
    telegram_ext.ContextTypes = ContextTypes
    telegram.ext = telegram_ext
    sys.modules["telegram"] = telegram
    sys.modules["telegram.ext"] = telegram_ext

import httpx as real_httpx  # noqa: E402

server = importlib.import_module("server")


class FakeUpstream:
    def __init__(self, status_code, headers, chunks=(b"abcde",)):
        self.status_code = status_code
        self.headers = headers
        self.chunks = chunks
        self.closed = False

    async def aclose(self):
        self.closed = True

    async def aiter_bytes(self, chunk_size=65536):
        for chunk in self.chunks:
            yield chunk


class FakeRequest:
    def __init__(self, method, url, headers):
        self.method = method
        self.url = url
        self.headers = headers


class FakeAsyncClient:
    next_response = None
    last_request = None
    closed = False

    def __init__(self, *args, **kwargs):
        self.closed = False

    def build_request(self, method, url, headers):
        request = FakeRequest(method, url, headers)
        FakeAsyncClient.last_request = request
        return request

    async def send(self, request, stream=True):
        return FakeAsyncClient.next_response

    async def aclose(self):
        self.closed = True
        FakeAsyncClient.closed = True


class FastLinkRouteMockTests(unittest.TestCase):
    def setUp(self):
        FakeAsyncClient.last_request = None
        FakeAsyncClient.next_response = None
        FakeAsyncClient.closed = False
        self.transport = real_httpx.ASGITransport(
            app=server.fastapi_app, raise_app_exceptions=False
        )

    def request(self, path, headers=None, upstream=None):
        FakeAsyncClient.next_response = upstream

        async def execute():
            async with real_httpx.AsyncClient(
                transport=self.transport, base_url="http://mock.test"
            ) as client:
                # Only replace the upstream client created by the application route.
                with patch.object(server.httpx, "AsyncClient", FakeAsyncClient):
                    return await client.get(path, headers=headers or {})

        return asyncio.run(execute())

    def test_unknown_token_returns_404_without_upstream_request(self):
        response = self.request("/stream/token-that-does-not-exist")
        self.assertEqual(response.status_code, 404)
        self.assertIsNone(FakeAsyncClient.last_request)

    def test_invalid_range_returns_416_without_upstream_request(self):
        token = server.create_token("videos/mock.mp4", "video/mp4", 5)
        response = self.request(f"/stream/{token}", {"Range": "bytes=5-"})
        self.assertEqual(response.status_code, 416)
        self.assertEqual(response.headers.get("content-range"), "bytes */5")
        self.assertIsNone(FakeAsyncClient.last_request)

    def test_valid_range_returns_partial_content(self):
        token = server.create_token("videos/mock.mp4", "video/mp4", 5)
        upstream = FakeUpstream(
            206,
            {"Content-Range": "bytes 0-1/5", "Content-Length": "2"},
            (b"ab",),
        )
        response = self.request(
            f"/stream/{token}", {"Range": "bytes=0-1"}, upstream
        )
        self.assertEqual(response.status_code, 206)
        self.assertEqual(response.content, b"ab")
        self.assertEqual(response.headers.get("content-range"), "bytes 0-1/5")
        self.assertEqual(FakeAsyncClient.last_request.headers.get("Range"), "bytes=0-1")

    def test_mismatched_upstream_range_is_rejected(self):
        token = server.create_token("videos/mock.mp4", "video/mp4", 5)
        upstream = FakeUpstream(
            206,
            {"Content-Range": "bytes 1-2/5", "Content-Length": "2"},
            (b"bc",),
        )
        response = self.request(
            f"/stream/{token}", {"Range": "bytes=0-1"}, upstream
        )
        self.assertEqual(response.status_code, 502)
        self.assertIn("mismatched byte range", response.text.lower())


if __name__ == "__main__":
    unittest.main(verbosity=2)
