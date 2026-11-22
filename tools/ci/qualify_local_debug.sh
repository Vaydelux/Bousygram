#!/usr/bin/env bash
set -Eeuo pipefail

# Local-only debug qualification. Never publishes, pushes or invokes Actions.
# Run from the repository root with JDK 21, Android SDK and native dependencies.
cd "$(dirname "$0")/../.."
[[ "$(git branch --show-current)" == "main" ]] || {
  echo "ERROR: Local debug qualification must run on main." >&2
  exit 1
}
[[ -z "$(git status --porcelain)" ]] || {
  echo "ERROR: Worktree must be clean for reproducible APK provenance." >&2
  exit 1
}
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
[[ -x ./gradlew ]] || { echo "ERROR: Gradle wrapper is not executable." >&2; exit 1; }

sha="$(git rev-parse HEAD)"
out="ci-artifacts/local-debug-${sha:0:12}"
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
bash tools/ci/verify_debug_preview_publication.sh "$out" "$sha" "local"
( cd "$out" && sha256sum --check SHA256SUMS.txt )
[[ "$(find "$out" -maxdepth 1 -type f -name '*.apk' | wc -l)" -eq 3 ]] || {
  echo "ERROR: Expected exactly three APKs (arm32, arm64, universal)." >&2
  exit 1
}
[[ "$(git rev-parse HEAD)" == "$sha" ]] || {
  echo "ERROR: Source changed during qualification." >&2
  exit 1
}
echo "PASS: Locally qualified three debug APKs from $sha in $out"
echo "NOTE: Physical Android TV/phone and authenticated Telegram smoke tests remain required."
