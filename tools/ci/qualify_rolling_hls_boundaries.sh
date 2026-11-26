#!/usr/bin/env bash
# Host-only structural checks on exact app Kotlin sources. No Android/receiver claims.
set -euo pipefail
for cmd in ffmpeg kotlinc java; do command -v "$cmd" >/dev/null; done
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
cast="$root/app/src/main/java/com/vaydelux/bousygram/core/cast"
kotlinc "$cast/RollingFmp4Framing.kt" "$cast/RollingHlsAac.kt" "$cast/RollingHlsBudget.kt" \
  "$root/app/src/main/java/com/vaydelux/bousygram/core/cache/TvStorageBudget.kt" \
  "$root/tools/ci/fixtures/RollingHlsBoundarySmoke.kt" -include-runtime -d "$scratch/qualification.jar"
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc2=size=320x180:rate=24 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 -t 24 -c:v libx264 \
  -preset ultrafast -g 144 -keyint_min 144 -sc_threshold 0 -bf 0 -pix_fmt yuv420p \
  -c:a aac -b:a 96k "$scratch/source.mkv"
ffmpeg -hide_banner -loglevel error -i "$scratch/source.mkv" -map 0:v:0 -map 0:a:0 -c copy -f mpegts "$scratch/source.ts"
ffmpeg -hide_banner -loglevel error -i "$scratch/source.mkv" -map 0:a:0 -c copy -f adts "$scratch/source.aac"
for ext in mkv ts; do
  mkdir "$scratch/hls"
  filters=()
  if [[ "$ext" == ts ]]; then filters=(-bsf:a aac_adtstoasc); fi
  ffmpeg -hide_banner -loglevel error -i "$scratch/source.$ext" -map 0:v:0 -map 0:a:0 \
    -c copy "${filters[@]}" -f hls -hls_time 6 -hls_segment_type fmp4 -hls_playlist_type vod \
    -hls_segment_filename "$scratch/hls/seg%03d.m4s" "$scratch/hls/media.m3u8"
  echo "Host $ext:"
  java -jar "$scratch/qualification.jar" "$scratch"
  ffmpeg -hide_banner -v error -xerror -i "$scratch/hls/media.m3u8" -map 0:v:0 -map 0:a:0 -f null -
  mv "$scratch/hls" "$scratch/hls-$ext"
done
