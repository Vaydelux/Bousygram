import os
import unittest

# TokenStore is a pure in-memory helper; provide a stable test secret before importing it.
os.environ.setdefault("SIGNING_KEY", "test-only-signing-key-with-more-than-32-characters")
from tokens import TokenStore  # noqa: E402


class TokenStoreTests(unittest.TestCase):
    def setUp(self):
        self.store = TokenStore()

    def test_created_token_resolves_to_expected_resource(self):
        token = self.store.create("videos/test.mp4", "video/mp4", 1234)
        entry = self.store.resolve(token)
        self.assertIsNotNone(entry)
        self.assertEqual(entry["file_path"], "videos/test.mp4")
        self.assertEqual(entry["size"], 1234)

    def test_random_tokens_are_distinct_and_revocable(self):
        first = self.store.create("a.mp4", "video/mp4", 1)
        second = self.store.create("b.mp4", "video/mp4", 1)
        self.assertNotEqual(first, second)
        self.assertTrue(self.store.revoke(first))
        self.assertIsNone(self.store.resolve(first))
        self.assertIsNotNone(self.store.resolve(second))

    def test_unknown_token_does_not_resolve(self):
        self.assertIsNone(self.store.resolve("not-a-token"))


if __name__ == "__main__":
    unittest.main()
