#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APK_DIR="${1:-$ROOT/app/build/outputs/apk/debug}"
TEST_DIR="${2:-$ROOT/app/build/outputs/apk/androidTest/debug}"
OUT_DIR="${3:-$ROOT/ci-artifacts/debug}"

mapfile -t APKS < <(find "$APK_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort)
mapfile -t TEST_APKS < <(find "$TEST_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort)
[[ ${#APKS[@]} -eq 1 ]] || { echo "Expected exactly one debug app APK, found ${#APKS[@]} in $APK_DIR" >&2; exit 1; }
[[ ${#TEST_APKS[@]} -eq 1 ]] || { echo "Expected exactly one debug instrumentation APK, found ${#TEST_APKS[@]} in $TEST_DIR" >&2; exit 1; }

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp "${APKS[0]}" "$OUT_DIR/Bousygram-debug.apk"
cp "${TEST_APKS[0]}" "$OUT_DIR/Bousygram-debug-androidTest.apk"
sha256sum "$OUT_DIR"/*.apk > "$OUT_DIR/SHA256SUMS.txt"
printf 'app_apk=%s\ntest_apk=%s\n' "$OUT_DIR/Bousygram-debug.apk" "$OUT_DIR/Bousygram-debug-androidTest.apk"
