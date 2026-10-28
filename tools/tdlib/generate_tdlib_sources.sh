#!/usr/bin/env bash
# Host-side TDLib source generation required before Android cross-compile.
set -euo pipefail

REPO="/c/Users/Jericho.Mosqueda/Downloads/Tele-main--1-"
ANDROID_EXAMPLE="$REPO/build-tools/tdlib/src/td/example/android"
CMAKE_BIN="$REPO/prerequisites/cmake-4.5.0-rc1-windows-x86_64/bin"
NINJA_BIN="$REPO/prerequisites/ninja-win"
NDK_MAKE="/c/ndk-r27d/prebuilt/windows-x86_64/bin"
GPERF_BIN="$REPO/prerequisites/gperf"

export PATH="$CMAKE_BIN:$NINJA_BIN:$NDK_MAKE:$GPERF_BIN:/usr/bin:$PATH"
hash -r

echo "cmake=$(command -v cmake)"
echo "ninja=$(command -v ninja)"
echo "make=$(command -v make)"
echo "gperf=$(command -v gperf || true)"
echo "php=$(command -v php || true)"

cd "$ANDROID_EXAMPLE"
rm -rf build-native-Java
mkdir -p build-native-Java
cd build-native-Java

cmake -G Ninja -DTD_GENERATE_SOURCE_FILES=ON -DTD_ENABLE_JNI=ON ..
cmake --build . --parallel

echo "Native TDLib source generation finished."
