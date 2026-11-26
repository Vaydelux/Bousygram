#!/usr/bin/env bash
# Host fixture only. Android app does not execute FFmpeg CLI or this script.
set -euo pipefail
for cmd in ffmpeg ffprobe python3 timeout; do command -v "$cmd" >/dev/null; done
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
ffmpeg -v error -y -f lavfi -i testsrc2=size=160x90:rate=12 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 -t 72 \
  -c:v libx264 -preset ultrafast -g 72 -keyint_min 72 -sc_threshold 0 -bf 0 \
  -c:a aac "$scratch/source.mkv"
ffmpeg -v error -y -i "$scratch/source.mkv" -c copy "$scratch/source.ts"
timeout --kill-after=10s 180s python3 "$root/tools/ci/qualify_rolling_hls_pipe.py" \
  "$scratch/source.mkv" "$scratch/source.ts"
