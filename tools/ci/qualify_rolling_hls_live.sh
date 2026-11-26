#!/usr/bin/env bash
# Virtual-clock file-store replay using actual Android-generated synthetic output.
set -euo pipefail
umask 077
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fixtures="$(realpath "${1:?native fixture directory required}")"
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
cast="$root/app/src/main/java/com/vaydelux/bousygram/core/cast"
debug="$root/app/src/debug/java/com/vaydelux/bousygram/core/cast"
kotlinc -Werror "$debug/RollingHlsLiveRetention.kt" "$debug/RollingHlsLiveFileStore.kt" \
    "$debug/RollingHlsLiveProducer.kt" "$root/tools/ci/fixtures/RollingHlsLiveProducerSmoke.kt" \
    "$root/tools/ci/fixtures/RollingHlsLiveRangeSmoke.kt" \
    "$root/tools/ci/fixtures/RollingHlsLiveTimingSmoke.kt" \
    -include-runtime -d "$scratch/producer.jar"
java -cp "$scratch/producer.jar" com.vaydelux.bousygram.core.cast.RollingHlsLiveTimingSmokeKt
java -cp "$scratch/producer.jar" com.vaydelux.bousygram.core.cast.RollingHlsLiveRangeSmokeKt
java -cp "$scratch/producer.jar" com.vaydelux.bousygram.core.cast.RollingHlsLiveProducerSmokeKt "$fixtures/converted.mkv.mp4"
kotlinc "$cast/RollingHlsBudget.kt" "$cast/RollingFmp4Framing.kt" \
    "$cast/RollingHlsSegmentStore.kt" "$cast/RollingHlsVerifiedFeed.kt" \
    "$cast/RollingFmp4SampleVerifier.kt" \
    "$root/app/src/main/java/com/vaydelux/bousygram/core/cache/TvStorageBudget.kt" \
    "$debug/RollingHlsLiveRetention.kt" "$debug/RollingHlsLiveFileStore.kt" \
    "$debug/RollingHlsLiveProducer.kt" "$debug/RollingHlsLiveVerifiedInput.kt" \
    "$root/tools/ci/fixtures/RollingHlsLiveVerifiedReplay.kt" -include-runtime -d "$scratch/replay.jar"
java -jar "$scratch/replay.jar" "$fixtures"
