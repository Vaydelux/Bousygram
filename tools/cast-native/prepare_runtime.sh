#!/usr/bin/env bash
# Synthetic host data and standalone DEX, never an application APK.
set -euo pipefail
out="$(realpath -m "${1:?new output directory}")"
d8="$(realpath "${2:?d8 executable}")"
[[ ! -e "$out" && -x "$d8" ]]
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$out/classes"
javac --release 11 -Xlint:all -Werror -d "$out/classes" \
  "$root/../../app/src/debug/java/com/vaydelux/bousygram/core/cast/NativeCastRemuxBridge.java" \
  "$root/NativeCastRuntimeSmoke.java"
jar cf "$out/classes.jar" -C "$out/classes" .
"$d8" --min-api 26 --output "$out/runtime.jar" "$out/classes.jar"
ffmpeg -v error -y -f lavfi -i testsrc2=size=160x90:rate=12 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 -t 72 \
  -c:v libx264 -preset ultrafast -g 72 -keyint_min 72 -sc_threshold 0 -bf 0 \
  -c:a aac "$out/source.mkv"
ffmpeg -v error -y -i "$out/source.mkv" -map 0:v:0 -map 0:a:0 -c copy "$out/source.ts"
(cd "$out" && sha256sum source.mkv source.ts runtime.jar > INPUTS.sha256)
