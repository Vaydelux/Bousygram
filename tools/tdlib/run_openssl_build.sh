#!/usr/bin/env bash
set -euo pipefail

# Use short paths without backslash+letter sequences that break OpenSSL's Perl
# Configure on Windows (e.g. "\prerequisites" -> Unicode property "r").
NDK="/c/ndk-r27d"
NDK_MAKE="$NDK/prebuilt/windows-x86_64/bin"
NDK_LLVM="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin"
OPENSSL_OUT="/c/tdlib-build/openssl"
SCRIPT="/c/Users/Jericho.Mosqueda/Downloads/Tele-main--1-/tools/tdlib/build_openssl_android.sh"

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
