#!/usr/bin/env bash
# Runs exact app Kotlin sources on finite synthetic Android-native output.
# No Android app, native loading, account or network access in this harness.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixtures="$(realpath "${1:?native fixture directory required}")"
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
cast="$root/app/src/main/java/com/vaydelux/bousygram/core/cast"
kotlinc "$cast/RollingHlsBudget.kt" "$cast/RollingFmp4Framing.kt" \
  "$cast/RollingHlsSegmentStore.kt" "$cast/RollingHlsVerifiedFeed.kt" \
  "$cast/RollingFmp4SampleVerifier.kt" "$cast/RollingHlsStreamingSink.kt" \
  "$root/app/src/main/java/com/vaydelux/bousygram/core/cache/TvStorageBudget.kt" \
  "$root/tools/ci/fixtures/RollingHlsSampleSmoke.kt" -include-runtime -d "$scratch/qualification.jar"
java -jar "$scratch/qualification.jar" "$fixtures" "$scratch"
