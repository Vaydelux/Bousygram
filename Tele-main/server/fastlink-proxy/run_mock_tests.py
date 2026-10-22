"""Run the Fast-Link mock/unit suite from any working directory."""

from pathlib import Path
import os
import sys
import unittest

ROOT = Path(__file__).resolve().parent
TESTS = ROOT / "tests"
os.chdir(ROOT)
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Synthetic local-only settings; the tests never start the Telegram bot or call Telegram.
os.environ["BOT_TOKEN"] = "123456:mock-bot-token"
os.environ["SIGNING_KEY"] = "mock-signing-key-long-enough-to-be-at-least-32-characters"
os.environ["SERVER_URL"] = "https://mock.example.test"
os.environ["ALLOWED_USER_IDS"] = "12345"

suite = unittest.defaultTestLoader.discover(start_dir=str(TESTS), pattern="test_*.py")
result = unittest.TextTestRunner(verbosity=2).run(suite)
sys.exit(0 if result.wasSuccessful() else 1)
