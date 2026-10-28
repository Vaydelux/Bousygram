#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! "$1" =~ ^[0-9a-fA-F]{40}$ ]]; then
  echo "Usage: $0 <40-character TDLib commit>" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$ROOT/build-tools/tdlib/src/td"
EXAMPLE="$SRC/example/android"
BUILD="$ROOT/build-tools/tdlib/host-java-generation"
mkdir -p "$(dirname "$SRC")"
if [[ ! -d "$SRC/.git" ]]; then git clone https://github.com/tdlib/td.git "$SRC"; fi
git -C "$SRC" fetch --tags origin "$1"
git -C "$SRC" checkout --detach "$1"
[[ "$(git -C "$SRC" rev-parse HEAD)" == "${1,,}" ]] || { echo "TDLib commit resolution failed" >&2; exit 1; }

command -v cmake >/dev/null || { echo "cmake is required" >&2; exit 1; }
command -v ninja >/dev/null || { echo "ninja is required" >&2; exit 1; }
rm -rf "$BUILD"
cmake -G Ninja -S "$EXAMPLE" -B "$BUILD" -DTD_GENERATE_SOURCE_FILES=ON -DTD_ENABLE_JNI=ON -DTD_ANDROID_JSON=OFF -DTD_ANDROID_JSON_JAVA=OFF
cmake --build "$BUILD" --target tl_generate_java --parallel
test -s "$EXAMPLE/org/drinkless/tdlib/TdApi.java"
printf '%s\n' "${1,,}" > "$ROOT/build-tools/tdlib/RESOLVED_COMMIT.txt"
echo "Generated TdApi.java from ${1,,} via tl_generate_java"
