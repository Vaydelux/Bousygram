#!/usr/bin/env bash
# Host reference only; synthetic fixture source files are outside the rolling store.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
ffmpeg -v error -f lavfi -i testsrc2=size=320x180:rate=24 \
    -f lavfi -i sine=frequency=440:sample_rate=48000 -t 360 \
    -c:v libx264 -preset ultrafast -threads 2 -b:v 650k -minrate 650k -maxrate 650k \
    -bufsize 1300k -x264-params nal-hrd=cbr -g 144 -keyint_min 144 -sc_threshold 0 -bf 0 \
    -c:a aac -b:a 96k "$scratch/source.mkv"
ffmpeg -v error -i "$scratch/source.mkv" -c copy -f mpegts "$scratch/source.ts"
timeout --kill-after=10s 300s python3 "$root/qualify_rolling_hls_long.py" "$scratch/source.mkv" "$scratch/source.ts"
