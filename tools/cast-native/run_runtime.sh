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
timeout --kill-after=10s 180s adb shell "CLASSPATH=$remote/runtime.jar app_process /system/bin com.vaydelux.bousygram.core.cast.NativeCastRuntimeSmoke $remote" | tee "$out/runtime.log"
grep -qx NATIVE_RUNTIME_CALLBACKS_PASS "$out/runtime.log"
for ext in mkv ts; do adb pull "$remote/converted.$ext.mp4" "$out/converted.$ext.mp4" >/dev/null; done
timeout --kill-after=10s 180s python3 "$root/verify_runtime.py" "$out"
echo "Android x86-64 JNI runtime and independent media verification passed"
