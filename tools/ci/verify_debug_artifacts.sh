#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APK_DIR="${1:-$ROOT/app/build/outputs/apk/debug}"
TEST_DIR="${2:-$ROOT/app/build/outputs/apk/androidTest/debug}"
OUT_DIR="${3:-$ROOT/ci-artifacts/debug}"

[[ -d "$APK_DIR" && -d "$TEST_DIR" ]] || { echo "Debug output directories are missing" >&2; exit 1; }
mapfile -t APKS < <(find "$APK_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort)
mapfile -t TEST_APKS < <(find "$TEST_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort)
[[ ${#APKS[@]} -gt 0 ]] || { echo "No debug app APKs found in $APK_DIR" >&2; exit 1; }
[[ ${#TEST_APKS[@]} -eq 1 ]] || { echo "Expected exactly one debug instrumentation APK, found ${#TEST_APKS[@]} in $TEST_DIR" >&2; exit 1; }
command -v zipinfo >/dev/null || { echo 'zipinfo is required for APK validation' >&2; exit 1; }
zipinfo -t "${TEST_APKS[0]}" >/dev/null
UNIVERSAL_APK=""
for apk in "${APKS[@]}"; do
  zipinfo -t "$apk" >/dev/null || { echo "Invalid APK archive: $apk" >&2; exit 1; }
  entries="$(zipinfo -1 "$apk")"
  if grep -Fqx 'lib/arm64-v8a/libtdjni.so' <<<"$entries" && grep -Fqx 'lib/armeabi-v7a/libtdjni.so' <<<"$entries"; then
    [[ -z "$UNIVERSAL_APK" ]] || { echo "Multiple debug APKs contain both supported TDLib ABIs" >&2; exit 1; }
    UNIVERSAL_APK="$apk"
  fi
done
[[ -n "$UNIVERSAL_APK" ]] || { echo 'No debug APK contains both ARM64 and ARM32 TDLib JNI' >&2; exit 1; }

rm -rf -- "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp -- "${UNIVERSAL_APK}" "$OUT_DIR/Bousygram-debug.apk"
cp -- "${TEST_APKS[0]}" "$OUT_DIR/Bousygram-debug-androidTest.apk"
sha256sum "$OUT_DIR"/*.apk > "$OUT_DIR/SHA256SUMS.txt"
printf 'app_apk=%s\ntest_apk=%s\n' "$OUT_DIR/Bousygram-debug.apk" "$OUT_DIR/Bousygram-debug-androidTest.apk"
