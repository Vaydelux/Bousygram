#!/usr/bin/env python3
import tempfile
import unittest
import warnings
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

from apk_native_inventory import ABIS, REQUIRED, inspect_apk, inventory


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def make_apk(self, name, abis, omit=None, extra=None):
        path = self.root / name
        with ZipFile(path, "w", ZIP_DEFLATED) as apk:
            for abi in abis:
                for lib in REQUIRED:
                    if (abi, lib) != omit:
                        apk.writestr(f"lib/{abi}/{lib}", b"fixture" * 16)
            if extra:
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    apk.writestr(*extra)
        return path

    def test_three_variants_and_accounting(self):
        paths = [self.make_apk("32.apk", {"armeabi-v7a"}),
                 self.make_apk("64.apk", {"arm64-v8a"}),
                 self.make_apk("all.apk", ABIS)]
        reports = inventory(paths)["apks"]
        self.assertEqual({report["kind"] for report in reports}, ABIS | {"universal"})
        self.assertEqual(8 * 112, next(r for r in reports if r["kind"] == "universal")["nativeBytes"])

    def test_missing_engine_rejected(self):
        path = self.make_apk("bad.apk", ABIS, omit=("armeabi-v7a", "libvlc.so"))
        with self.assertRaisesRegex(ValueError, "missing libvlc.so"):
            inspect_apk(path)

    def test_unexpected_abi_rejected(self):
        path = self.make_apk("bad.apk", ABIS, extra=("lib/x86/libmpv.so", b"x"))
        with self.assertRaisesRegex(ValueError, "Unexpected native entry"):
            inspect_apk(path)

    def test_duplicate_zip_entry_rejected(self):
        path = self.make_apk("bad.apk", ABIS, extra=("lib/arm64-v8a/libmpv.so", b"x"))
        with self.assertRaisesRegex(ValueError, "Duplicate ZIP entry"):
            inspect_apk(path)

    def test_empty_native_rejected(self):
        path = self.make_apk("bad.apk", ABIS, extra=("lib/arm64-v8a/libempty.so", b""))
        with self.assertRaisesRegex(ValueError, "Empty native library"):
            inspect_apk(path)

    def test_missing_variant_rejected(self):
        path = self.make_apk("only.apk", ABIS)
        with self.assertRaisesRegex(ValueError, "Expected one"):
            inventory([path, path, path])


if __name__ == "__main__":
    unittest.main()
