#!/usr/bin/env bash
set -euo pipefail

ANDROID_API="${ANDROID_API:-37}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
LIST_FILE="${SDK_SETUP_LIST_FILE:-}"
SKIP_INSTALL="${SDK_SETUP_SKIP_INSTALL:-0}"
SKIP_GRADLE="${SDK_SETUP_SKIP_GRADLE:-0}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

[[ -n "$SDK_ROOT" ]] || { echo 'ANDROID_HOME or ANDROID_SDK_ROOT is required' >&2; exit 2; }
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
[[ -x "$SDKMANAGER" ]] || SDKMANAGER="$(command -v sdkmanager || true)"
if [[ "$SKIP_INSTALL" != 1 ]]; then
  [[ -n "$SDKMANAGER" ]] || { echo "sdkmanager was not found under $SDK_ROOT" >&2; exit 2; }
fi

if [[ -z "$LIST_FILE" ]]; then
  [[ -n "$SDKMANAGER" ]] || { echo 'sdkmanager is required to query available packages' >&2; exit 2; }
  LIST_FILE="$(mktemp)"
  trap 'rm -f "$LIST_FILE"' EXIT
  "$SDKMANAGER" --list --channel=3 >"$LIST_FILE" 2>&1
fi

platform_package="$(awk -F'|' -v api="$ANDROID_API" '
  /^[[:space:]]*platforms;android-/ {
    package=$1; gsub(/^[[:space:]]+|[[:space:]]+$/, "", package)
    if (package ~ ("^platforms;android-" api "(\\.|$)")) print package
  }
' "$LIST_FILE" | sort -V | head -1)"
[[ -n "$platform_package" ]] || {
  echo "No compatible Android API $ANDROID_API platform was advertised by sdkmanager --list --channel=3." >&2
  echo 'Available platform entries:' >&2
  awk -F'|' '/^[[:space:]]*platforms;android-/ {print $1}' "$LIST_FILE" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | sort -u >&2
  exit 1
}

build_tools_package="build-tools;$BUILD_TOOLS_VERSION"
grep -Eq "^[[:space:]]*${build_tools_package//./\\.}[[:space:]]*\\|" "$LIST_FILE" || {
  echo "Build Tools $BUILD_TOOLS_VERSION was not advertised by sdkmanager --list --channel=3." >&2
  exit 1
}

if [[ "$SKIP_INSTALL" != 1 ]]; then
  set +o pipefail
  yes | "$SDKMANAGER" --licenses >/tmp/bousygram-sdk-licenses.log 2>&1
  sdk_status="${PIPESTATUS[1]}"
  set -o pipefail
  cat /tmp/bousygram-sdk-licenses.log
  (( sdk_status == 0 )) || exit "$sdk_status"
  "$SDKMANAGER" "$platform_package" "$build_tools_package"
fi

platform_dir="$SDK_ROOT/${platform_package/platforms;/platforms/}"
platform_props="$platform_dir/source.properties"
[[ -d "$platform_dir" && -f "$platform_props" ]] || {
  echo "sdkmanager reported success but platform metadata is missing: $platform_props" >&2
  exit 1
}
grep -Eq '^AndroidVersion.ApiLevel[[:space:]]*=[[:space:]]*37(\.0)?$' "$platform_props" || {
  echo "Installed platform does not report Android API $ANDROID_API: $platform_props" >&2
  sed -n '1,80p' "$platform_props" >&2
  exit 1
}
build_tools_dir="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
[[ -x "$build_tools_dir/aapt2" || -x "$build_tools_dir/aapt2.exe" ]] || {
  echo "Required aapt2 executable is missing: $build_tools_dir" >&2
  exit 1
}
[[ -x "$build_tools_dir/apksigner" || -f "$build_tools_dir/apksigner.bat" ]] || {
  echo "Required Build Tools executables are missing: $build_tools_dir" >&2
  exit 1
}

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "ANDROID_PLATFORM_PACKAGE=$platform_package"
    echo "ANDROID_PLATFORM_DIR=$platform_dir"
    echo "ANDROID_BUILD_TOOLS_DIR=$build_tools_dir"
  } >> "$GITHUB_ENV"
fi
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo '## Android SDK'
    echo "Platform package: \`$platform_package\`"
    echo "Platform API: $(sed -n 's/^AndroidVersion.ApiLevel[[:space:]]*=[[:space:]]*//p' "$platform_props")"
    echo "Build Tools: \`$BUILD_TOOLS_VERSION\`"
  } >> "$GITHUB_STEP_SUMMARY"
fi

if [[ "$SKIP_GRADLE" != 1 ]]; then
  # This probe isolates AGP/platform resolution. The required verification command
  # below still runs every TDLib and product gate without exclusions.
  "$ROOT/gradlew" --no-daemon --console=plain :app:preDebugBuild \
    -x verifyTdlibAbiCoverage -x verifyTdlibBindingCompatibility \
    -x testTdlibBindingCompatibilityFixtures
fi
echo "Android SDK verified: $platform_package, API $ANDROID_API, Build Tools $BUILD_TOOLS_VERSION"
