#!/usr/bin/env python3
"""Fail-closed validation of externally supplied Android TDLib JSON binaries."""
import hashlib
import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ABIS = {
    "arm64-v8a": (183, 2, 32373496, "bfb529e06e88e17be62258b58d626b1a3d693cc90b2d017f6b336392ff294131"),
    "armeabi-v7a": (40, 1, 23776232, "1dd3318a00b02c5a3c0273e1e6c2c4b84c99242ece1353e6e7cf708ad00523bc"),
    "x86_64": (62, 2, 33902296, "8c6ecf6f5f5ca59023f8d800c2bc4438d7478a11c291105e946c47887cf4e61e"),
}
EXPORTS = ("td_create_client_id", "td_send", "td_receive", "td_execute")

def check(abi, spec):
    machine, elf_class, expected_size, expected_hash = spec
    path = ROOT / "app/src/main/jniLibs" / abi / "libtdjson.so"
    if not path.is_file():
        raise ValueError(f"{abi}: missing {path}")
    blob = path.read_bytes()
    if len(blob) != expected_size or hashlib.sha256(blob).hexdigest() != expected_hash:
        raise ValueError(f"{abi}: size/hash does not match pinned input")
    if blob[:4] != b"\x7fELF" or blob[4] != elf_class or blob[5] != 1:
        raise ValueError(f"{abi}: ELF header/class/endianness mismatch")
    if struct.unpack_from("<H", blob, 18)[0] != machine:
        raise ValueError(f"{abi}: ELF machine mismatch")
    # Export checks require an ELF-aware reader, not a byte-string search.
    try:
        from elftools.elf.elffile import ELFFile
    except ImportError as exc:
        raise RuntimeError("Install pinned pyelftools to verify ELF exported symbols") from exc
    with path.open("rb") as f:
        elf = ELFFile(f)
        table = elf.get_section_by_name(".dynsym")
        if table is None:
            raise ValueError(f"{abi}: ELF dynamic symbol table missing")
        exported = {
            sym.name for sym in table.iter_symbols()
            if sym["st_shndx"] != "SHN_UNDEF"
            and sym["st_info"]["bind"] in ("STB_GLOBAL", "STB_WEAK")
            and sym["st_info"]["type"] in ("STT_FUNC", "STT_NOTYPE")
        }
        missing = set(EXPORTS) - exported
        if missing:
            raise ValueError(f"{abi}: missing TDLib exports: {sorted(missing)}")
    print(f"OK {abi}: SHA-256, ELF and JSON exports verified")

if __name__ == "__main__":
    try:
        for abi, spec in ABIS.items():
            check(abi, spec)
    except Exception as e:
        print(f"BLOCKED: {e}", file=sys.stderr)
        sys.exit(1)
