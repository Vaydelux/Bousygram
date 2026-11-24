#!/usr/bin/env python3
"""Read-only APK footprint inventory. Not a signature or runtime verifier."""
import argparse
import json
from pathlib import Path
from zipfile import ZipFile

ABIS = {"arm64-v8a", "armeabi-v7a"}
REQUIRED = {"libtdjni.so", "libmpv.so", "libvlc.so", "libc++_shared.so"}


def inspect_apk(path):
    path = Path(path)
    with ZipFile(path) as apk:
        names = set()
        libraries = []
        for entry in apk.infolist():
            if entry.filename in names:
                raise ValueError(f"Duplicate ZIP entry in {path.name}: {entry.filename}")
            names.add(entry.filename)
            if entry.is_dir() or not entry.filename.startswith("lib/"):
                continue
            parts = entry.filename.split("/")
            if len(parts) != 3 or parts[1] not in ABIS or not parts[2].endswith(".so"):
                raise ValueError(f"Unexpected native entry in {path.name}: {entry.filename}")
            if entry.file_size <= 0:
                raise ValueError(f"Empty native library in {path.name}: {entry.filename}")
            libraries.append({
                "abi": parts[1], "name": parts[2],
                "bytes": entry.file_size, "compressedBytes": entry.compress_size,
                "crc32": f"{entry.CRC:08x}",
            })
        abis = {lib["abi"] for lib in libraries}
        if not abis:
            raise ValueError(f"No supported native libraries in {path.name}")
        for abi in abis:
            missing = REQUIRED - {lib["name"] for lib in libraries if lib["abi"] == abi}
            if missing:
                raise ValueError(f"{path.name} {abi}: missing {', '.join(sorted(missing))}")
        return {
            "apk": path.name, "apkBytes": path.stat().st_size,
            "kind": "universal" if abis == ABIS else next(iter(abis)),
            "nativeBytes": sum(lib["bytes"] for lib in libraries),
            "nativeCompressedBytes": sum(lib["compressedBytes"] for lib in libraries),
            "libraries": sorted(libraries, key=lambda lib: (-lib["bytes"], lib["abi"], lib["name"])),
        }


def inventory(paths):
    reports = [inspect_apk(path) for path in paths]
    if sorted(report["kind"] for report in reports) != sorted(ABIS | {"universal"}):
        raise ValueError("Expected one ARM32, one ARM64 and one universal APK")
    return {"schemaVersion": 1, "apks": sorted(reports, key=lambda report: report["kind"])}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apks", nargs=3, type=Path)
    args = parser.parse_args()
    print(json.dumps(inventory(args.apks), indent=2, sort_keys=True))
