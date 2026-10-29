#!/usr/bin/env bash
set -euo pipefail

APK_DIR="${1:?APK output directory required}"
OUT_DIR="${2:?Artifact directory required}"
EXPECTED_CERT="${3:?Expected certificate SHA-256 required}"
BUILD_TOOLS="${4:?Android build-tools directory required}"
EXPECTED_VERSION="${5:?Expected versionName required}"
EXPECTED_VERSION_CODE="${6:?Expected versionCode required}"
EXPECTED_CERT="$(printf '%s' "$EXPECTED_CERT" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"
AAPT2="$BUILD_TOOLS/aapt2"

[[ -x "$APKSIGNER" && -x "$ZIPALIGN" && -x "$AAPT2" ]] || { echo "Required Android SDK tools are missing under $BUILD_TOOLS" >&2; exit 1; }
command -v zipinfo >/dev/null || { echo 'zipinfo is required for release APK validation' >&2; exit 1; }
mapfile -t APKS < <(find "$APK_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort)
[[ ${#APKS[@]} -eq 3 ]] || { echo "Expected exactly three hardened-release APKs, found ${#APKS[@]}" >&2; exit 1; }

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
declare -A FOUND
for apk in "${APKS[@]}"; do
  "$APKSIGNER" verify --verbose "$apk"
  "$ZIPALIGN" -c -P 16 -v 4 "$apk" >/dev/null
  zipinfo -t "$apk" >/dev/null
  cert="$($APKSIGNER verify --print-certs "$apk" | sed -n 's/^.*certificate SHA-256 digest: //p' | head -1 | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
  [[ -n "$cert" && "$cert" == "$EXPECTED_CERT" ]] || { echo "Certificate mismatch: $apk" >&2; exit 1; }
  badging="$($AAPT2 dump badging "$apk")"
  grep -q "package: name='com.vaydelux.bousygram' versionCode='$EXPECTED_VERSION_CODE' versionName='$EXPECTED_VERSION'" <<<"$badging" || { echo "Unexpected package/version metadata: $apk" >&2; exit 1; }
  grep -q "sdkVersion:'26'" <<<"$badging" || { echo "Unexpected minSdkVersion: $apk" >&2; exit 1; }
  grep -q "targetSdkVersion:'37'" <<<"$badging" || { echo "Unexpected targetSdkVersion: $apk" >&2; exit 1; }
  libs="$(zipinfo -1 "$apk")"
  grep -q '/libtdjni.so$' <<<"$libs" || { echo "TDLib JNI library missing: $apk" >&2; exit 1; }
  grep -q '/libmpv.so$' <<<"$libs" || { echo "libmpv missing: $apk" >&2; exit 1; }
  has32=0; has64=0
  native_abis="$(awk -F/ '$1 == "lib" && NF >= 3 {print $2}' <<<"$libs" | sort -u)"
  if grep -qE '(^| )((x86)|(x86_64)|(mips))($| )' <<<"$(tr '\n' ' ' <<<"$native_abis")"; then
    echo "Unsupported native ABI in $apk: $native_abis" >&2; exit 1
  fi
  grep -q '^lib/armeabi-v7a/libtdjni.so$' <<<"$libs" && has32=1 || true
  grep -q '^lib/arm64-v8a/libtdjni.so$' <<<"$libs" && has64=1 || true
  if [[ $has32 -eq 1 && $has64 -eq 1 ]]; then kind=universal
  elif [[ $has32 -eq 1 ]]; then kind=armeabi-v7a
  elif [[ $has64 -eq 1 ]]; then kind=arm64-v8a
  else echo "Unknown APK ABI layout: $apk" >&2; exit 1; fi
  case "$kind" in
    armeabi-v7a) expected_abis=$'armeabi-v7a\n' ;;
    arm64-v8a) expected_abis=$'arm64-v8a\n' ;;
    universal) expected_abis=$'arm64-v8a\narmeabi-v7a\n' ;;
  esac
  [[ "$native_abis"$'\n' == "$expected_abis" ]] || { echo "Unexpected native ABI set in $apk: $native_abis" >&2; exit 1; }
  while IFS= read -r abi; do
    [[ -n "$abi" ]] || continue
    grep -qx "lib/$abi/libtdjni.so" <<<"$libs" || { echo "TDLib JNI missing for $abi in $apk" >&2; exit 1; }
    grep -qx "lib/$abi/libmpv.so" <<<"$libs" || { echo "libmpv missing for $abi in $apk" >&2; exit 1; }
  done <<<"$native_abis"
  [[ -z "${FOUND[$kind]:-}" ]] || { echo "Duplicate $kind APKs" >&2; exit 1; }
  FOUND[$kind]="$apk"
done
for kind in armeabi-v7a arm64-v8a universal; do
  [[ -n "${FOUND[$kind]:-}" ]] || { echo "Missing $kind hardened-release APK" >&2; exit 1; }
  cp -- "${FOUND[$kind]}" "$OUT_DIR/Bousygram-v${RELEASE_VERSION:?}-hardenedRelease-$kind.apk"
done
sha256sum "$OUT_DIR"/*.apk > "$OUT_DIR/SHA256SUMS.txt"
