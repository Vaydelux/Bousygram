#!/usr/bin/env bash
set -Eeuo pipefail

# Local-only debug qualification. Never publishes, pushes or invokes Actions.
# Run from the repository root with JDK 21, Android SDK and native dependencies.
cd "$(dirname "$0")/../.."
# Candidate mode is local-only and requires an explicit full commit SHA.
# Default remains main-only; publication workflows are unchanged.
source tools/ci/local_qualification_ref.sh
qualification_kind="$(local_qualification_kind "$@")" || exit 1
[[ -f .env ]] || {
  echo "ERROR: Create a local .env from .env.example; do not commit credentials." >&2
  exit 1
}
grep -Eq '^TMDB_API_KEY=[[:xdigit:]]{32}$' .env || {
  echo "ERROR: A 32-character TMDB v3 key is required in local .env." >&2
  exit 1
}
command -v java >/dev/null
command -v python3 >/dev/null
command -v sha256sum >/dev/null
command -v zipinfo >/dev/null
[[ -x ./gradlew ]] || { echo "ERROR: Gradle wrapper is not executable." >&2; exit 1; }

# Reuse the existing fail-closed packaging/verifier contracts locally.
# Numeric ID 1 is a local packaging marker, NOT a CI attestation.
export CI_VERIFICATION_RUN_ID=1
RELEASE_SHA="$(git rev-parse HEAD)"
export RELEASE_SHA
GITHUB_OUTPUT="$(mktemp)"
export GITHUB_OUTPUT
trap 'rm -f "$GITHUB_OUTPUT"' EXIT
if [[ -z "${ANDROID_BUILD_TOOLS_DIR:-}" ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -n "$sdk" ]] || { echo "ERROR: Set ANDROID_HOME or ANDROID_BUILD_TOOLS_DIR." >&2; exit 1; }
  export ANDROID_BUILD_TOOLS_DIR="$sdk/build-tools/35.0.0"
fi
[[ -x "$ANDROID_BUILD_TOOLS_DIR/apksigner" && -x "$ANDROID_BUILD_TOOLS_DIR/aapt2" ]] || {
  echo "ERROR: Android build-tools 35.0.0 with apksigner and aapt2 required." >&2
  exit 1
}
sha="$(git rev-parse HEAD)"
out="ci-artifacts/local-$qualification_kind-${sha:0:12}"
mkdir -p "$out"
echo "Qualifying commit $sha (local only)"
bash tools/ci/verify_brand_identity.sh
bash tools/ci/verify_telegram_local_only.sh
./gradlew --no-daemon --no-configuration-cache --console=plain \
  :app:verifyReleaseVersionPolicy \
  :app:verifyTdlibAbiCoverage \
  :app:verifyTvHardeningContract \
  :app:verifyPrivateMediaCastReleaseGate \
  :app:verifySensitiveLogRedaction \
  :app:verifyTdlibBindingCompatibility \
  :app:compileDebugKotlin \
  :mediaforge-core:testDebugUnitTest \
  :mediaforge-player-adapters:testDebugUnitTest \
  :app:lintDebug \
  :app:testDebugUnitTest \
  :app:assembleDebugAndroidTest
python3 tools/ci/test_favorites_storage.py
bash tools/ci/build_debug_preview.sh "$out"
# The verifier requires a build/run identifier, even for local qualification.
bash tools/ci/verify_debug_preview_publication.sh "$out" "$sha" "$CI_VERIFICATION_RUN_ID"
( cd "$out" && sha256sum --check SHA256SUMS.txt )
python3 tools/ci/apk_native_inventory.py "$out"/*.apk > "$out/NATIVE_INVENTORY.json"
[[ "$(find "$out" -maxdepth 1 -type f -name '*.apk' | wc -l)" -eq 3 ]] || {
  echo "ERROR: Expected exactly three APKs (arm32, arm64, universal)." >&2
  exit 1
}
[[ "$(git rev-parse HEAD)" == "$sha" ]] &&
  git diff --quiet && git diff --cached --quiet || {
  echo "ERROR: Source changed during qualification." >&2
  exit 1
}
echo "PASS: Locally qualified three $qualification_kind APKs from $sha in $out"
echo "NOTE: Local artifact run ID is not CI attestation; never publish these as CI artifacts."
echo "NOTE: Physical Android TV/phone and authenticated Telegram smoke tests remain required."
