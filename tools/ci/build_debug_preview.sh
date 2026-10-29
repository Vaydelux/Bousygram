#!/usr/bin/env bash
# Build three independently verified debug APKs for GitHub prereleases.
# These are DEBUG-SIGNED and are never substitutes for qualified production APKs.
set -euo pipefail

OUT_DIR="${1:-ci-artifacts/debug-preview}"
ANDROID_BUILD_TOOLS_DIR="${ANDROID_BUILD_TOOLS_DIR:?Android build tools path required}"
APKSIGNER="$ANDROID_BUILD_TOOLS_DIR/apksigner"
AAPT2="$ANDROID_BUILD_TOOLS_DIR/aapt2"
[[ -x "$APKSIGNER" && -x "$AAPT2" ]] || { echo "Missing Android SDK signing or package metadata tool" >&2; exit 1; }
command -v zipinfo >/dev/null || { echo 'zipinfo required' >&2; exit 1; }

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
# Do not allow stale files to be published as part of a new build.
rm -f "$OUT_DIR"/Bousygram-debug-*.apk "$OUT_DIR"/SHA256SUMS.txt "$OUT_DIR"/BUILD_INFO.txt
apk_dir="app/build/outputs/apk/debug"
declare -a apk_names=()
version=""
code=""

package_one() {
  local kind="$1" apk="" libs="" badging="" actual_version="" actual_code="" filename=""
  mapfile -t found < <(find "$apk_dir" -maxdepth 1 -type f -name '*.apk' -print | sort)
  [[ ${#found[@]} -eq 1 ]] || { echo "Expected exactly one debug APK for $kind; found ${#found[@]}" >&2; exit 1; }
  apk="${found[0]}"
  "$APKSIGNER" verify "$apk" >/dev/null
  zipinfo -t "$apk" >/dev/null
  badging="$("$AAPT2" dump badging "$apk")"
  grep -q "package: name='com.vaydelux.bousygram'" <<<"$badging" || { echo "Unexpected package in $apk" >&2; exit 1; }
  actual_version="$(sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p" <<<"$badging" | head -1)"
  actual_code="$(sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p" <<<"$badging" | head -1)"
  [[ "$actual_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && "$actual_code" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid package version in $apk" >&2; exit 1; }
  if [[ -z "$version" ]]; then version="$actual_version"; code="$actual_code"; fi
  [[ "$version" == "$actual_version" && "$code" == "$actual_code" ]] || { echo "Mismatched version across debug APKs" >&2; exit 1; }
  libs="$(zipinfo -1 "$apk")"
  local actual_abis expected_abis
  actual_abis="$(awk -F/ '$1 == "lib" && NF >= 3 {print $2}' <<<"$libs" | sort -u)"
  case "$kind" in
    universal) expected_abis=$'arm64-v8a\narmeabi-v7a' ;;
    arm64-v8a) expected_abis='arm64-v8a' ;;
    armeabi-v7a) expected_abis='armeabi-v7a' ;;
    *) echo "Invalid ABI label $kind" >&2; exit 1 ;;
  esac
  [[ "$actual_abis" == "$expected_abis" ]] || { echo "ABI mismatch for $kind: $actual_abis" >&2; exit 1; }
  while IFS= read -r abi; do
    [[ -n "$abi" ]] || continue
    grep -qx "lib/$abi/libtdjni.so" <<<"$libs" || { echo "TDLib native library missing for $abi" >&2; exit 1; }
    grep -qx "lib/$abi/libmpv.so" <<<"$libs" || { echo "MPV native library missing for $abi" >&2; exit 1; }
  done <<<"$actual_abis"
  filename="Bousygram-debug-v$version-build.${CI_VERIFICATION_RUN_ID:?}-$kind.apk"
  cp -- "$apk" "$OUT_DIR/$filename"
  apk_names+=("$filename")
  echo "Verified $filename"
}

# Each native filter is applied by Gradle at packaging time: never rename a
# universal APK to masquerade as a CPU-specific APK.
./gradlew --no-daemon --console=plain :app:verifyTdlibAbiCoverage :app:verifyTdlibBindingCompatibility :app:assembleDebug
package_one universal
./gradlew --no-daemon --console=plain -PdevAbi=arm64-v8a :app:assembleDebug
package_one arm64-v8a
./gradlew --no-daemon --console=plain -PdevAbi=armeabi-v7a :app:assembleDebug
package_one armeabi-v7a

( cd "$OUT_DIR"; sha256sum "${apk_names[@]}" > SHA256SUMS.txt )
printf 'version=%s\nversionCode=%s\nsourceSha=%s\nciRunId=%s\nsigning=Android generated debug key (not production signing)\n' \
  "$version" "$code" "${RELEASE_SHA:?}" "$CI_VERIFICATION_RUN_ID" > "$OUT_DIR/BUILD_INFO.txt"
echo "version=$version" >> "${GITHUB_OUTPUT:?}"
echo "Produced ${#apk_names[@]} verified debug APKs for version $version"
