#!/usr/bin/env bash
# Host-only reference, not an Android native dependency or device qualification.
set -euo pipefail
for tool in ffmpeg ffprobe python3; do command -v "$tool" >/dev/null; done
scratch="$(mktemp -d)"
trap 'rm -rf -- "$scratch"' EXIT
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i testsrc2=size=320x180:rate=24 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -t 72 -c:v libx264 -preset ultrafast -g 144 -keyint_min 144 \
  -sc_threshold 0 -bf 0 -pix_fmt yuv420p -c:a aac -b:a 96k "$scratch/source.mkv"
ffmpeg -hide_banner -loglevel error -i "$scratch/source.mkv" \
  -map 0:v:0 -map 0:a:0 -c copy -f mpegts "$scratch/source.ts"
for ext in mkv ts; do
  mkdir "$scratch/$ext"
  filters=()
  if [[ "$ext" == ts ]]; then filters=(-bsf:a aac_adtstoasc); fi
  ffmpeg -hide_banner -loglevel error -i "$scratch/source.$ext" \
    -map 0:v:0 -map 0:a:0 -c copy "${filters[@]}" \
    -f hls -hls_time 6 -hls_segment_type fmp4 -hls_playlist_type vod \
    -hls_segment_filename "$scratch/$ext/seg%03d.m4s" "$scratch/$ext/media.m3u8"
  ffmpeg -hide_banner -v error -xerror -i "$scratch/$ext/media.m3u8" \
    -map 0:v:0 -map 0:a:0 -f null -
done
python3 - "$scratch" <<'PY'
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(sys.argv[1])
def packets(path):
    result = subprocess.check_output([
        "ffprobe", "-v", "error", "-count_packets", "-show_entries",
        "stream=codec_name,codec_type,nb_read_packets", "-of", "json", str(path)
    ])
    streams = json.loads(result)["streams"]
    assert len(streams) == 2, streams
    return {s["codec_type"]: (s["codec_name"], int(s["nb_read_packets"])) for s in streams}

for ext in ("mkv", "ts"):
    source = packets(root / f"source.{ext}")
    target = root / ext
    manifest = target / "media.m3u8"
    output = packets(manifest)
    assert source == output, (ext, source, output)
    assert source["video"] == ("h264", 1728), source
    assert source["audio"][0] == "aac" and source["audio"][1] > 0, source
    segments = sorted(target.glob("seg*.m4s"))
    assert len(segments) == 12, segments
    assert 0 < (target / "init.mp4").stat().st_size
    assert all(0 < p.stat().st_size <= 8 * 1024 * 1024 for p in segments)
    playlist = manifest.read_text()
    assert '#EXT-X-MAP:URI="init.mp4"' in playlist
    assert "#EXT-X-ENDLIST" in playlist
    print(f"{ext}: {source}; {len(segments)} segments; max {max(p.stat().st_size for p in segments)} bytes; decode passed")
PY
