#!/usr/bin/env bash
# Minimal Android OpenSSL static build for Bousygram (arm64-v8a + armeabi-v7a).
# Adapted from TDLib example/android/build-openssl.sh without full TDLib tool checks.
set -euo pipefail

ANDROID_NDK_ROOT="${1:?Android NDK root required}"
OPENSSL_INSTALL_DIR="${2:?OpenSSL install dir required}"
OPENSSL_VERSION="${3:-openssl-3.3.2}"
ABIS="${4:-arm64-v8a armeabi-v7a}"

if [[ ! -d "$ANDROID_NDK_ROOT" ]]; then
  echo "Error: NDK directory \"$ANDROID_NDK_ROOT\" does not exist."
  exit 1
fi
if [[ -e "$OPENSSL_INSTALL_DIR" ]]; then
  echo "Error: \"$OPENSSL_INSTALL_DIR\" already exists. Delete it manually to proceed."
  exit 1
fi

for TOOL_NAME in make perl curl sed tar clang; do
  if ! command -v "$TOOL_NAME" >/dev/null 2>&1; then
    echo "Error: required tool '$TOOL_NAME' is not on PATH."
    exit 1
  fi
done

if [[ "$(command -v make)" == *" "* ]]; then
  echo "Error: full path to make must not contain spaces: $(command -v make)"
  exit 1
fi

mkdir -p "$OPENSSL_INSTALL_DIR"

# OpenSSL Configure runs under Perl and breaks on Windows backslashes
# (e.g. "\prerequisites" is parsed as a Unicode property). Keep MSYS paths.
to_unix_path() {
  local p="$1"
  if command -v cygpath >/dev/null 2>&1; then
    cygpath -u "$p"
  else
    echo "$p" | sed -e 's#\\#/#g' -e 's#^\([A-Za-z]\):#/\L\1#'
  fi
}

ANDROID_NDK_ROOT="$(to_unix_path "$(cd "$ANDROID_NDK_ROOT" && pwd)")"
OPENSSL_INSTALL_DIR="$(to_unix_path "$(cd "$OPENSSL_INSTALL_DIR" && pwd)")"
echo "ANDROID_NDK_ROOT=$ANDROID_NDK_ROOT"
echo "OPENSSL_INSTALL_DIR=$OPENSSL_INSTALL_DIR"

# Keep build scratch under a short path so Windows path conversion cannot
# introduce sequences like "\tdlib" that confuse OpenSSL's Perl Configure.
WORK_DIR="${TDLIB_OPENSSL_WORK_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../build-tools/tdlib" && pwd)/openssl-src}"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

echo "Downloading OpenSSL sources ($OPENSSL_VERSION)..."
curl -sfLO "https://github.com/openssl/openssl/archive/refs/tags/${OPENSSL_VERSION}.tar.gz"
tar xzf "${OPENSSL_VERSION}.tar.gz"
cd "openssl-${OPENSSL_VERSION}"

# OpenSSL 3 reads ANDROID_NDK_ROOT; 1.1.1 reads ANDROID_NDK_HOME.
export ANDROID_NDK_ROOT
export ANDROID_NDK_HOME="$ANDROID_NDK_ROOT"
HOST_ARCH="windows-x86_64"
export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$HOST_ARCH/bin:$PATH"

if ! clang --help >/dev/null 2>&1; then
  echo "Error: failed to run clang from Android NDK."
  exit 1
fi

ANDROID_API32=21
ANDROID_API64=21

for ABI in $ABIS; do
  echo "=== Building OpenSSL for $ABI ==="
  if [[ "$ABI" == "armeabi-v7a" ]]; then
    ./Configure android-arm no-shared -U__ANDROID_API__ -D__ANDROID_API__=$ANDROID_API32 -D__ARM_MAX_ARCH__=8
  elif [[ "$ABI" == "arm64-v8a" ]]; then
    LDFLAGS=-Wl,-z,max-page-size=16384 ./Configure android-arm64 no-shared -U__ANDROID_API__ -D__ANDROID_API__=$ANDROID_API64
  else
    echo "Unsupported ABI: $ABI"
    exit 1
  fi

  sed -i.bak 's/-O3/-O3 -ffunction-sections -fdata-sections/g' Makefile
  make depend -s
  make -j4 -s

  mkdir -p "$OPENSSL_INSTALL_DIR/$ABI/lib"
  cp libcrypto.a libssl.a "$OPENSSL_INSTALL_DIR/$ABI/lib/"
  cp -r include "$OPENSSL_INSTALL_DIR/$ABI/"
  make distclean
done

cd /
rm -rf "$WORK_DIR"
echo "OpenSSL installed under $OPENSSL_INSTALL_DIR"
