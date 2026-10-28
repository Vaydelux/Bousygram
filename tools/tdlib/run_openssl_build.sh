#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" || ! -d "$NDK" ]]; then
  echo "Set ANDROID_NDK_HOME or ANDROID_NDK_ROOT to an Android NDK directory." >&2
  exit 2
fi
NDK_MAKE="$NDK/prebuilt/windows-x86_64/bin"
NDK_LLVM="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
OPENSSL_OUT="${TDLIB_OPENSSL_OUT:-$ROOT/build-tools/tdlib/openssl}"
SCRIPT="$ROOT/tools/tdlib/build_openssl_android.sh"

# Clear any inherited Windows NDK env vars before exporting Unix paths.
unset ANDROID_NDK_HOME ANDROID_NDK_ROOT ANDROID_SDK_ROOT || true

# Prefer Git Bash (MSYS) perl so OpenSSL's android config detects the NDK correctly.
export PATH="/usr/bin:$NDK_LLVM:$NDK_MAKE:$PATH"
hash -r

echo "perl=$(command -v perl)"
echo "make=$(command -v make)"
echo "clang=$(command -v clang)"
perl -MExtUtils::MakeMaker -e 'print "MakeMaker OK\n"'

rm -rf "$OPENSSL_OUT"
mkdir -p "$(dirname "$OPENSSL_OUT")"

chmod +x "$SCRIPT"
"$SCRIPT" "$NDK" "$OPENSSL_OUT"
