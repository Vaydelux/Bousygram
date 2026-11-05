#!/usr/bin/env bash
set -Eeuo pipefail

# Entirely synthetic audiovisual samples. No private Telegram content or tokens.
# Run on the CI host; the emulator receives only the finished fixture bytes.
output_dir="${1:-ci-artifacts/decoder-fixtures}"
command -v ffmpeg >/dev/null || { echo '::error::ffmpeg is required for real media fixtures' >&2; exit 1; }
command -v ffprobe >/dev/null || { echo '::error::ffprobe is required for real media fixtures' >&2; exit 1; }
mkdir -p "$output_dir"

# H.264 baseline + AAC is decodable by the Android emulator's MediaCodec path.
# Regular 1-second IDR frames make indexed MKV seeking deterministic.
ffmpeg -hide_banner -nostdin -loglevel error -y \
  -f lavfi -i 'testsrc2=size=160x90:rate=12' \
  -f lavfi -i 'sine=frequency=523:sample_rate=22050' \
  -t 12 -map 0:v:0 -map 1:a:0 \
  -c:v libx264 -preset ultrafast -profile:v baseline -pix_fmt yuv420p \
  -g 12 -keyint_min 12 -sc_threshold 0 -threads 1 \
  -c:a aac -b:a 32k -ar 22050 -ac 1 -shortest \
  -movflags +faststart "$output_dir/seek-control.mp4"

# MKV has real Matroska cues; TS intentionally has no mandatory global seek index.
# Both preserve the exact same independently generated audio/video content.
ffmpeg -hide_banner -nostdin -loglevel error -y -i "$output_dir/seek-control.mp4" \
  -map 0 -c copy "$output_dir/indexed.mkv"
ffmpeg -hide_banner -nostdin -loglevel error -y -i "$output_dir/seek-control.mp4" \
  -map 0 -c copy -f mpegts "$output_dir/unindexed.ts"

for file in "$output_dir/seek-control.mp4" "$output_dir/indexed.mkv" "$output_dir/unindexed.ts"; do
  test -s "$file" || { echo "::error::Missing decoder fixture $file" >&2; exit 1; }
  ffprobe -v error -select_streams v:0 -show_entries stream=codec_name \
    -of default=noprint_wrappers=1:nokey=1 "$file" | grep -qx h264 \
    || { echo "::error::Fixture is not a playable H.264 stream: $file" >&2; exit 1; }
done

( cd "$output_dir" && sha256sum seek-control.mp4 indexed.mkv unindexed.ts > SHA256SUMS.txt )
( cd "$output_dir" && sha256sum --check SHA256SUMS.txt )
echo "Generated reproducible 12-second MP4/MKV/TS decoder fixtures in $output_dir"
