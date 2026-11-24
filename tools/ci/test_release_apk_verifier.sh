#!/usr/bin/env bash
# Synthetic archives and mock SDK tools: checks shell policy, not Android signatures.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
mkdir -p "$temp/sdk" "$temp/apks" "$temp/broken"
export MOCK_CERT
MOCK_CERT="$(printf '%064d' 1)"
export RELEASE_VERSION=1.0.0
cat > "$temp/sdk/apksigner" <<'MOCK'
#!/usr/bin/env bash
set -eu
if [[ "$2" == --print-certs ]]; then
  printf 'Signer #1 certificate SHA-256 digest: %s\n' "$MOCK_CERT"
  if [[ "${MOCK_MULTIPLE:-0}" == 1 ]]; then
    printf 'Signer #2 certificate SHA-256 digest: %064d\n' 2
  fi
fi
MOCK
cat > "$temp/sdk/zipalign" <<'MOCK'
#!/usr/bin/env bash
exit 0
MOCK
cat > "$temp/sdk/aapt2" <<'MOCK'
#!/usr/bin/env bash
printf "package: name='%s' versionCode='1' versionName='%s'\n" "${MOCK_PACKAGE:-com.vaydelux.bousygram}" "${MOCK_VERSION:-1.0.0}"
printf "sdkVersion:'26'\ntargetSdkVersion:'37'\n"
MOCK
chmod +x "$temp/sdk/"*
python3 - "$temp" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import sys
root = Path(sys.argv[1])
matrix = {
    "32": ["armeabi-v7a"],
    "64": ["arm64-v8a"],
    "all": ["armeabi-v7a", "arm64-v8a"],
}
for folder in ("apks", "broken"):
    for kind, abis in matrix.items():
        with ZipFile(root / folder / (kind + ".apk"), "w") as apk:
            for abi in abis:
                for lib in ("libtdjni.so", "libmpv.so", "libvlc.so", "libc++_shared.so"):
                    if folder == "broken" and kind == "all" and abi == "armeabi-v7a" and lib == "libvlc.so":
                        continue
                    apk.writestr(f"lib/{abi}/{lib}", b"fixture")
PY
verify() {
  bash "$root/tools/ci/verify_release_apks.sh" "$temp/$1" "$temp/$2" "$MOCK_CERT" "$temp/sdk" 1.0.0 1
}
reject() {
  if verify "$@" >/dev/null 2>&1; then
    echo "Unexpected APK qualification success: $*" >&2
    exit 1
  fi
}
verify apks valid >/dev/null
[[ "$(find "$temp/valid" -name '*.apk' | wc -l)" -eq 3 ]]
reject broken missing-vlc
[[ ! -e "$temp/missing-vlc" ]]
export MOCK_MULTIPLE=1
reject apks multiple-signers
unset MOCK_MULTIPLE
export MOCK_PACKAGE=comXvaydeluxXbousygram
reject apks bad-package
unset MOCK_PACKAGE
export MOCK_VERSION=1x0x0
reject apks bad-version
unset MOCK_VERSION
mkdir "$temp/occupied"
touch "$temp/occupied/keep"
reject apks occupied
[[ -f "$temp/occupied/keep" ]]
ln -s "$temp/occupied" "$temp/link"
reject apks link
echo "PASS: 7 signed-release policy fixtures (mock SDK tools)"
