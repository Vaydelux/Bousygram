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
    if ! timeout --kill-after=10s 180s adb shell -T "CLASSPATH=$remote/runtime.jar app_process /system/bin com.vaydelux.bousygram.core.cast.NativeCastRuntimeSmoke $remote $args" 2>&1 | tr -d '\r' | tee "$log"; then
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
