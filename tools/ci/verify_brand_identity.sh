#!/usr/bin/env bash
set -Eeuo pipefail

# The approved Chow Chow and paper-plane WebP is the single full-color logo.
# A launcher badge must never silently diverge from the favicon source.
cmp brand/bousygram-mascot.webp app/src/main/res/drawable-nodpi/ic_bousygram_mascot.webp
grep -Fq '@drawable/ic_bousygram_mascot' app/src/main/res/mipmap-anydpi/ic_launcher.xml
grep -Fq '@drawable/ic_bousygram_mascot' app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
grep -Fq 'R.drawable.ic_bousygram_mascot' app/src/main/java/com/vaydelux/bousygram/presentation/components/BousygramBrandMark.kt
grep -Fq 'BousygramBrandMark(size' app/src/main/java/com/vaydelux/bousygram/presentation/components/EnterpriseComponents.kt
grep -Fq '@drawable/ic_banner_v2' app/src/main/AndroidManifest.xml

# Verify the favicon is actually a 16/32px multi-resolution Windows ICO,
# not a stale text placeholder. Pixel-level equivalence needs visual review
# because favicon downsampling and Android launcher masks differ.
python3 - <<'PY'
from pathlib import Path
from struct import unpack_from

blob = Path("brand/favicon.ico").read_bytes()
if len(blob) < 22:
    raise SystemExit("Favicon missing or too small")
reserved, image_type, count = unpack_from("<HHH", blob)
if reserved != 0 or image_type != 1 or count < 2:
    raise SystemExit("Favicon is not a multi-resolution ICO")
sizes = set()
for index in range(count):
    entry = 6 + index * 16
    if entry + 16 > len(blob):
        raise SystemExit("Truncated ICO directory")
    width, height, _, _, _, _, size, offset = unpack_from("<BBBBHHII", blob, entry)
    width = width or 256
    height = height or 256
    if width != height or size == 0 or offset + size > len(blob):
        raise SystemExit("Invalid ICO image directory")
    sizes.add(width)
if not {16, 32}.issubset(sizes):
    raise SystemExit(f"Favicon lacks 16px and 32px variants: {sorted(sizes)}")
print("Canonical mascot matches launcher sources, and favicon has valid 16/32px frames.")
PY
