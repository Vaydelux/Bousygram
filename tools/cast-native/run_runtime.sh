#!/usr/bin/env bash
# Shell-owned synthetic-only test process, no installed package or production data.
set -euo pipefail
lib="$(realpath "${1:?x86-64 bridge required}")"
out="$(realpath "${2:?fixture directory required}")"
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
(cd "$out" && sha256sum --check INPUTS.sha256)
[[ "$(adb shell getprop ro.product.cpu.abi | tr -d '\r')" == x86_64 ]]
remote="$(adb shell mktemp -d /data/local/tmp/bousygram-cast.XXXXXX | tr -d '\r')"
[[ "$remote" =~ ^/data/local/tmp/bousygram-cast\.[a-zA-Z0-9]+$ ]]
trap 'adb shell rm -rf "$remote" >/dev/null || true' EXIT
adb shell chmod 700 "$remote"
adb push "$lib" "$remote/libbousygram_cast_remux.so" >/dev/null
for name in source.mkv source.ts runtime.jar; do adb push "$out/$name" "$remote/$name" >/dev/null; done
run_case() {
    local suffix="$1"
    shift
    local marker="$1"
    shift
    local args="$*"
    local log="$out/runtime-$suffix.log"
    local case_root="$remote" case_timeout=180s
    if [[ "$suffix" == extended-* ]]; then case_root="$remote/extended"; case_timeout=210s; fi
    if ! timeout --kill-after=10s "$case_timeout" adb shell -T "CLASSPATH=$remote/runtime.jar app_process /system/bin com.vaydelux.bousygram.core.cast.NativeCastRuntimeSmoke $case_root $args" 2>&1 | tr -d '\r' | tee "$log"; then
        echo "::error::JNI runtime case failed: $suffix"
        adb shell 'cat /proc/meminfo; ps -A' || true
        adb logcat -d -t 3000 -v threadtime | tail -n 500 || true
        return 1
    fi
    grep -qx "$marker" "$log"
}
# Run each format/case separately for exact attribution, then retain the
# original same-process recovery gate. A crash in either is still a failure.
for ext in mkv ts; do
    for mode in GOOD ZERO_READ OVERSIZED_READ THROW_READ REFUSE_WRITE CANCEL; do
        run_case "$ext-$mode" NATIVE_SINGLE_CASE_PASS "$ext" "$mode"
    done
done
run_case reentry NATIVE_RUNTIME_CALLBACKS_PASS
for ext in mkv ts; do adb pull "$remote/converted.$ext.mp4" "$out/converted.$ext.mp4" >/dev/null; done
timeout --kill-after=10s 180s python3 "$root/verify_runtime.py" "$out"
timeout --kill-after=10s 180s bash "$root/../ci/qualify_rolling_hls_samples.sh" "$out"
echo "Android x86-64 JNI runtime, decoded media conservation and Kotlin rolling store qualification passed"

# Only after every finite integrity and replay gate has passed. Full synthetic
# output files are test evidence, not the rolling production storage path.
mkdir "$out/extended"
ffmpeg -v error -f lavfi -i testsrc2=size=320x180:rate=24 \
    -f lavfi -i sine=frequency=440:sample_rate=48000 -t 360 \
    -c:v libx264 -preset ultrafast -threads 2 -b:v 650k -minrate 650k -maxrate 650k \
    -bufsize 1300k -x264-params nal-hrd=cbr -g 144 -keyint_min 144 -sc_threshold 0 -bf 0 \
    -c:a aac -b:a 96k "$out/extended/source.mkv"
ffmpeg -v error -i "$out/extended/source.mkv" -c copy -f mpegts "$out/extended/source.ts"
adb shell mkdir -m 700 "$remote/extended"
adb push "$lib" "$remote/extended/libbousygram_cast_remux.so" >/dev/null
for ext in mkv ts; do
    adb push "$out/extended/source.$ext" "$remote/extended/source.$ext" >/dev/null
    run_case "extended-$ext-CANCEL_LATE" NATIVE_EXTENDED_CASE_PASS "$ext" CANCEL_LATE EXTENDED
    run_case "extended-$ext-GOOD" NATIVE_EXTENDED_CASE_PASS "$ext" GOOD EXTENDED
    adb pull "$remote/extended/converted.$ext.mp4" "$out/extended/converted.$ext.mp4" >/dev/null
done
timeout --kill-after=10s 300s python3 "$root/verify_runtime.py" "$out/extended" --extended
echo "Android x86-64 extended JNI qualification passed"
timeout --kill-after=10s 240s bash "$root/../ci/qualify_rolling_hls_tls.sh" "$out"
echo "Mutually authenticated TLS host receiver replay passed"
timeout --kill-after=10s 240s bash "$root/../ci/qualify_rolling_hls_live.sh" "$out"
echo "Verified live producer file replay passed"
