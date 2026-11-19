#!/usr/bin/env bash
# Work around transient/corrupt Android SDK system-image archives in CI without
# disguising an instrumentation failure as a passing test.
set -Eeuo pipefail

command -v sdkmanager >/dev/null || {
  echo "::error::Android sdkmanager is not on PATH" >&2
  exit 1
}
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk_root" && -d "$sdk_root" ]] || {
  echo "::error::Missing Android SDK root" >&2
  exit 1
}
package='system-images;android-35;aosp_atd;x86_64'
image_dir="$sdk_root/system-images/android-35/aosp_atd/x86_64"

for attempt in 1 2; do
  echo "Provisioning Android 35 ATD x86_64 image (attempt $attempt of 2)"
  if timeout 450s sdkmanager --install "$package" --channel=0 </dev/null; then
    echo "Android emulator system image is installed."
    exit 0
  fi
  echo "::warning::ATD system-image download/install failed; clearing its partial package."
  # Delete only the exact target system-image directory, never the wider SDK.
  rm -rf -- "$image_dir"
  sleep 5
done

echo "::error::Android emulator image unavailable after two attempts; tests were not executed." >&2
exit 1
