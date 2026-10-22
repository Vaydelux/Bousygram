"""Fail-closed configuration checks; no network or real credentials are used."""

import os
import unittest
from unittest.mock import patch

os.environ["BOT_TOKEN"] = "123456:mock-bot-token"
os.environ["SIGNING_KEY"] = "mock-signing-key-long-enough-to-be-at-least-32-characters"
os.environ["SERVER_URL"] = "https://mock.example.test"
os.environ["ALLOWED_USER_IDS"] = "12345"

import config  # noqa: E402


class RuntimeConfigValidationTests(unittest.TestCase):
    def setUp(self):
        self.safe_config = patch.multiple(
            config,
            BOT_TOKEN="123456:mock-bot-token",
            SIGNING_KEY="mock-signing-key-long-enough-to-be-at-least-32-characters",
            SERVER_URL="https://mock.example.test",
            TOKEN_TTL=3600,
            ALLOWED_USER_IDS={12345},
            _INVALID_ALLOWED_USER_IDS=[],
        )
        self.safe_config.start()
        self.addCleanup(self.safe_config.stop)

    def test_accepts_complete_https_config_with_allow_list(self):
        config.validate_runtime_config()

    def test_rejects_empty_trusted_user_allow_list(self):
        with patch.object(config, "ALLOWED_USER_IDS", set()):
            with self.assertRaisesRegex(RuntimeError, "at least one trusted Telegram user ID"):
                config.validate_runtime_config()

    def test_rejects_public_http_server_url(self):
        with patch.object(config, "SERVER_URL", "http://proxy.example.test"):
            with self.assertRaisesRegex(RuntimeError, "must use HTTPS"):
                config.validate_runtime_config()


if __name__ == "__main__":
    unittest.main(verbosity=2)
