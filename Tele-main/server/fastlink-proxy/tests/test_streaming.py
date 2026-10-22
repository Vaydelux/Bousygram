import unittest

from streaming import parse_range


class ParseRangeTests(unittest.TestCase):
    def test_closed_open_ended_and_suffix_ranges(self):
        self.assertEqual(parse_range("bytes=10-19", 100), (10, 19))
        self.assertEqual(parse_range("bytes=90-", 100), (90, 99))
        self.assertEqual(parse_range("bytes=-10", 100), (90, 99))
        self.assertEqual(parse_range("bytes=-200", 100), (0, 99))

    def test_end_is_clamped_to_file_size(self):
        self.assertEqual(parse_range("bytes=90-999", 100), (90, 99))

    def test_rejects_invalid_ranges(self):
        for value in (
            "bytes=100-", "bytes=10-2", "bytes=-0", "bytes=-", "bytes=",
            "items=0-2", "bytes=1-2,4-5", "bytes=abc-def", "bytes=١-٢",
            "bytes=" + ("9" * 5000) + "-",
        ):
            with self.subTest(value=value):
                self.assertIsNone(parse_range(value, 100))

    def test_rejects_nonpositive_file_size(self):
        self.assertIsNone(parse_range("bytes=0-1", 0))
        self.assertIsNone(parse_range("bytes=0-1", -1))


if __name__ == "__main__":
    unittest.main()
