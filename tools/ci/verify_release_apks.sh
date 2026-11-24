#!/usr/bin/env bash
set -euo pipefail

APK_DIR="${1:?APK output directory required}"
OUT_DIR="${2:?Artifact directory required}"
EXPECTED_CERT="${3:?Expected certificate SHA-256 required}"
BUILD_TOOLS="${4:?Android build-tools directory required}"
EXPECTED_VERSION="${5:?Expected versionName required}"
EXPECTED_VERSION_CODE="${6:?Expected versionCode required}"
EXPECTED_CERT="$(printf '%s' "$EXPECTED_CERT" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
[[ "$EXPECTED_CERT" =~ ^[0-9a-f]{64}$ ]] || { echo "Invalid expected certificate digest" >&2; exit 1; }
[[ "$EXPECTED_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$EXPECTED_VERSION_CODE" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid expected version" >&2; exit 1; }
[[ "${RELEASE_VERSION:?Release version required}" == "$EXPECTED_VERSION" ]] || { echo "Release filename version mismatch" >&2; exit 1; }
APKSIGNER="$BUILD_TOOLS/apksigner"
ZIPALIGN="$BUILD_TOOLS/zipalign"
AAPT2="$BUILD_TOOLS/aapt2"

[[ -x "$APKSIGNER" && -x "$ZIPALIGN" && -x "$AAPT2" ]] || { echo "Required Android SDK tools are missing under $BUILD_TOOLS" >&2; exit 1; }
command -v zipinfo >/dev/null || { echo 'zipinfo is required for release APK validation' >&2; exit 1; }
mapfile -t APKS < <(find "$APK_DIR" -maxdepth 1 -type f -name '*.apk' -print | sort)
[[ ${#APKS[@]} -eq 3 ]] || { echo "Expected exactly three hardened-release APKs, found ${#APKS[@]}" >&2; exit 1; }

# Do not destroy existing artifacts or arbitrary caller-selected directories.
[[ ! -L "$OUT_DIR" ]] || { echo "Artifact directory must not be a symlink" >&2; exit 1; }
if [[ -e "$OUT_DIR" ]]; then
  [[ -d "$OUT_DIR" && -z "$(find "$OUT_DIR" -mindepth 1 -maxdepth 1 -print -quit)" ]] || {
    echo "Artifact directory must be empty; use a new directory" >&2; exit 1;
  }
fi
declare -A FOUND
for apk in "${APKS[@]}"; do
  "$APKSIGNER" verify --verbose "$apk"
  "$ZIPALIGN" -c -P 16 -v 4 "$apk" >/dev/null
  zipinfo -t "$apk" >/dev/null
  cert_output="$("$APKSIGNER" verify --print-certs "$apk")"
  mapfile -t certs < <(sed -n 's/^Signer #[0-9][0-9]* certificate SHA-256 digest: //p' <<<"$cert_output" | tr -d ':[:blank:]' | tr '[:upper:]' '[:lower:]')
  [[ "${#certs[@]}" -eq 1 && "${certs[0]}" == "$EXPECTED_CERT" ]] || { echo "Certificate mismatch or multiple signers: $apk" >&2; exit 1; }
  badging="$($AAPT2 dump badging "$apk")"
  grep -Fq "package: name='com.vaydelux.bousygram' versionCode='$EXPECTED_VERSION_CODE' versionName='$EXPECTED_VERSION'" <<<"$badging" || { echo "Unexpected package/version metadata: $apk" >&2; exit 1; }
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
    grep -Fqx "lib/$abi/libtdjni.so" <<<"$libs" || { echo "TDLib JNI missing for $abi in $apk" >&2; exit 1; }
    for lib in libmpv.so libvlc.so libc++_shared.so; do
      grep -Fqx "lib/$abi/$lib" <<<"$libs" || { echo "$lib missing for $abi in $apk" >&2; exit 1; }
    done
  done <<<"$native_abis"
  [[ -z "${FOUND[$kind]:-}" ]] || { echo "Duplicate $kind APKs" >&2; exit 1; }
  FOUND[$kind]="$apk"
done
for kind in armeabi-v7a arm64-v8a universal; do
  [[ -n "${FOUND[$kind]:-}" ]] || { echo "Missing $kind hardened-release APK" >&2; exit 1; }
done
mkdir -p "$OUT_DIR"
for kind in armeabi-v7a arm64-v8a universal; do
  cp -- "${FOUND[$kind]}" "$OUT_DIR/Bousygram-v${RELEASE_VERSION}-hardenedRelease-$kind.apk"
done
sha256sum "$OUT_DIR"/*.apk > "$OUT_DIR/SHA256SUMS.txt"
