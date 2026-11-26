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
cat > "$TMP/list.txt" <<'LIST'
Available Packages:
  platforms;android-37.0 | 1 | Android SDK Platform 37 | platforms/android-37.0
  build-tools;35.0.0 | 1 | Android SDK Build-Tools 35 | build-tools/35.0.0
LIST
# Never let a synthetic SDK overwrite the real runner's exported paths or summary.
fixture_env="$TMP/github-env"
fixture_summary="$TMP/github-summary"
: > "$fixture_env"
: > "$fixture_summary"
run_setup() {
  GITHUB_ENV="$fixture_env" GITHUB_STEP_SUMMARY="$fixture_summary" \
    ANDROID_HOME="$1" ANDROID_SDK_ROOT="$1" ANDROID_API=37 BUILD_TOOLS_VERSION=35.0.0 \
    SDK_SETUP_LIST_FILE="$TMP/list.txt" SDK_SETUP_SKIP_INSTALL=1 SDK_SETUP_SKIP_GRADLE=1 \
    bash "$ROOT/tools/ci/setup_android_sdk.sh"
}
run_setup "$TMP" >/dev/null
printf 'ANDROID_PLATFORM_PACKAGE=platforms;android-37.0\nANDROID_PLATFORM_DIR=%s/platforms/android-37.0\nANDROID_BUILD_TOOLS_DIR=%s/build-tools/35.0.0\n' "$TMP" "$TMP" > "$TMP/expected-env"
diff -u "$TMP/expected-env" "$fixture_env"
grep -Fxq '## Android SDK' "$fixture_summary"
grep -Fxq 'Build Tools: `35.0.0`' "$fixture_summary"
cp "$fixture_summary" "$TMP/expected-summary"
mkdir -p "$TMP/missing"
if run_setup "$TMP/missing" >/dev/null 2>&1; then
  echo 'missing-platform fixture unexpectedly passed' >&2
  exit 1
fi
diff -u "$TMP/expected-env" "$fixture_env"
diff -u "$TMP/expected-summary" "$fixture_summary"
# A platform without apksigner must also fail before exporting any paths.
rm "$TMP/build-tools/35.0.0/apksigner"
if run_setup "$TMP" >/dev/null 2>&1; then
  echo 'missing-apksigner fixture unexpectedly passed' >&2
  exit 1
fi
diff -u "$TMP/expected-env" "$fixture_env"
diff -u "$TMP/expected-summary" "$fixture_summary"
echo 'Android SDK setup fixtures passed'
