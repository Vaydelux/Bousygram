#!/usr/bin/env bash
# Verify the exact bytes produced and checked by Android Enterprise CI.
# No rebuilding, signing, or rewriting is permitted in the publish workflow.
set -euo pipefail

dir="${1:?Verified debug artifact directory required}"
expected_sha="${2:?Expected main source SHA required}"
expected_run_id="${3:?Expected successful mainline CI run id required}"
[[ "$expected_sha" =~ ^[0-9a-fA-F]{40}$ && "$expected_run_id" =~ ^[1-9][0-9]*$ ]] || {
  echo 'Invalid trusted CI identity' >&2
  exit 1
}
[[ -d "$dir" && -f "$dir/BUILD_INFO.txt" && -f "$dir/SHA256SUMS.txt" ]] || {
  echo 'Missing debug APK manifest or hashes' >&2
  exit 1
}

info="$dir/BUILD_INFO.txt"
# A duplicate field must not allow two contradictory interpretations.
for field in version versionCode sourceSha ciRunId signing; do
  [[ "$(grep -c "^$field=" "$info" || true)" -eq 1 ]] || {
    echo "Missing or repeated build metadata: $field" >&2
    exit 1
  }
done
[[ "$(wc -l < "$info")" -eq 5 ]] || { echo 'Unknown fields in debug build metadata' >&2; exit 1; }
version="$(sed -n 's/^version=//p' "$info")"
version_code="$(sed -n 's/^versionCode=//p' "$info")"
source_sha="$(sed -n 's/^sourceSha=//p' "$info")"
run_id="$(sed -n 's/^ciRunId=//p' "$info")"
signing="$(sed -n 's/^signing=//p' "$info")"

[[ "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] || {
  echo 'Invalid debug release version' >&2
  exit 1
}
[[ "$version_code" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid Android version code' >&2; exit 1; }
[[ "$source_sha" == "$expected_sha" && "$run_id" == "$expected_run_id" ]] || {
  echo 'Downloaded artifact was not built by the verified source CI run' >&2
  exit 1
}
[[ "$signing" == 'Android generated debug key (not production signing)' ]] || {
  echo 'Unexpected debug signing provenance' >&2
  exit 1
}
prefix="Bousygram-debug-v$version-build.$run_id"
expected_names=(
  "$prefix-arm64-v8a.apk"
  "$prefix-armeabi-v7a.apk"
  "$prefix-universal.apk"
)

mapfile -t actual_names < <(find "$dir" -maxdepth 1 -type f -name '*.apk' -printf '%f\n' | sort)
mapfile -t allowed_names < <(printf '%s\n' "${expected_names[@]}" | sort)
[[ "${#actual_names[@]}" -eq 3 ]] || { echo 'Expected exactly three verified debug APKs' >&2; exit 1; }
for i in 0 1 2; do
  [[ "${actual_names[i]}" == "${allowed_names[i]}" ]] || {
    echo "Unexpected APK filename: ${actual_names[i]}" >&2
    exit 1
  }
  [[ -s "$dir/${actual_names[i]}" ]] || { echo "Empty APK: ${actual_names[i]}" >&2; exit 1; }
done

hashes="$dir/SHA256SUMS.txt"
[[ "$(wc -l < "$hashes")" -eq 3 ]] || { echo 'Checksum record count mismatch' >&2; exit 1; }
# SHA256SUMS may only name the three exact APKs; reject path traversal/extra assets.
hash_names="$(awk '{if (NF != 2 || length($1) != 64 || $1 !~ /^[0-9a-f]+$/) exit 1; print $2}' "$hashes")" || {
  echo 'Malformed checksum record' >&2
  exit 1
}
mapfile -t hashed_names < <(printf '%s\n' "$hash_names" | sort)
[[ "${#hashed_names[@]}" -eq 3 ]] || { echo 'Invalid checksum records' >&2; exit 1; }
for i in 0 1 2; do
  [[ "${hashed_names[i]}" == "${allowed_names[i]}" ]] || {
    echo 'Checksum file does not match the three approved APK names' >&2
    exit 1
  }
done
( cd "$dir" && sha256sum --check --status SHA256SUMS.txt ) || {
  echo 'APK digest mismatch; refusing publication' >&2
  exit 1
}
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  printf 'version=%s\n' "$version" >> "$GITHUB_OUTPUT"
fi
echo "Verified identical CI-produced APKs: v$version, commit $expected_sha, run $run_id"
