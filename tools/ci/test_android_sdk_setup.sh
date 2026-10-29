#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/platforms/android-37.0" "$TMP/build-tools/35.0.0"
printf 'Pkg.Desc = Android SDK Platform 37\nAndroidVersion.ApiLevel = 37\n' > "$TMP/platforms/android-37.0/source.properties"
printf '#!/usr/bin/env bash\nexit 0\n' > "$TMP/build-tools/35.0.0/aapt2"
printf '#!/usr/bin/env bash\nexit 0\n' > "$TMP/build-tools/35.0.0/apksigner"
chmod +x "$TMP/build-tools/35.0.0/aapt2" "$TMP/build-tools/35.0.0/apksigner"
cat > "$TMP/list.txt" <<'EOF'
Available Packages:
  platforms;android-37.0 | 1 | Android SDK Platform 37 | platforms/android-37.0
  build-tools;35.0.0 | 1 | Android SDK Build-Tools 35 | build-tools/35.0.0
EOF
ANDROID_HOME="$TMP" SDK_SETUP_LIST_FILE="$TMP/list.txt" SDK_SETUP_SKIP_INSTALL=1 SDK_SETUP_SKIP_GRADLE=1 \
  bash "$ROOT/tools/ci/setup_android_sdk.sh" >/dev/null
mkdir -p "$TMP/missing"
if ANDROID_HOME="$TMP/missing" SDK_SETUP_LIST_FILE="$TMP/list.txt" SDK_SETUP_SKIP_INSTALL=1 SDK_SETUP_SKIP_GRADLE=1 \
  bash "$ROOT/tools/ci/setup_android_sdk.sh" >/dev/null 2>&1; then
  echo 'missing-platform fixture unexpectedly passed' >&2
  exit 1
fi
echo 'Android SDK setup fixtures passed'
