#!/usr/bin/env bash
# Run the release-publisher manifest validator against positive and negative fixtures.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
verify="$root/tools/ci/verify_debug_preview_publication.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

sha='0123456789abcdef0123456789abcdef01234567'
run_id=12345
prefix="Bousygram-debug-v1.0.0-build.$run_id"
names=(
  "$prefix-arm64-v8a.apk"
  "$prefix-armeabi-v7a.apk"
  "$prefix-universal.apk"
)

reset_fixture() {
  find "$fixture" -maxdepth 1 -type f -delete
  for apk in "${names[@]}"; do
    printf 'fake verified fixture bytes: %s\n' "$apk" > "$fixture/$apk"
  done
  (
    cd "$fixture"
    sha256sum "${names[@]}" > SHA256SUMS.txt
  )
  printf 'version=1.0.0\nversionCode=1000000\nsourceSha=%s\nciRunId=%s\nsigning=Android generated debug key (not production signing)\n' \
    "$sha" "$run_id" > "$fixture/BUILD_INFO.txt"
}

reject() {
  local label="$1"
  if bash "$verify" "$fixture" "$sha" "$run_id" >/dev/null 2>&1; then
    echo "Publisher accepted invalid artifact: $label" >&2
    exit 1
  fi
}

reset_fixture
bash "$verify" "$fixture" "$sha" "$run_id" >/dev/null
reset_fixture
printf 'tampered' >> "$fixture/${names[0]}"
reject 'modified APK bytes'
reset_fixture
sed -i "s/sourceSha=$sha/sourceSha=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/" "$fixture/BUILD_INFO.txt"
reject 'wrong commit'
reset_fixture
sed -i "s/ciRunId=$run_id/ciRunId=67890/" "$fixture/BUILD_INFO.txt"
reject 'wrong CI run'
reset_fixture
printf 'version=9.0.0\n' >> "$fixture/BUILD_INFO.txt"
reject 'duplicate or extra metadata'
reset_fixture
printf 'not an apk' > "$fixture/rogue.apk"
reject 'extra APK'
reset_fixture
sed -i 's/arm64-v8a.apk/x86_64.apk/' "$fixture/SHA256SUMS.txt"
reject 'wrong checksum filename'
reset_fixture
sed -i '1s/^[0-9a-f]\{64\}/invalidhash/' "$fixture/SHA256SUMS.txt"
reject 'malformed checksum'
reset_fixture
rm "$fixture/${names[1]}"
reject 'missing APK'
echo 'Debug prerelease provenance and checksum fixtures passed.'
