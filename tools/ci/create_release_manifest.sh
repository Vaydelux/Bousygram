#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${1:?Verified release artifact directory required}"
: "${RELEASE_TAG:?}" "${RELEASE_SHA:?}" "${RELEASE_VERSION:?}" "${RELEASE_VERSION_CODE:?}"
: "${TDLIB_COMMIT:?}" "${RELEASE_CERT_SHA256:?}" "${CI_VERIFICATION_RUN_ID:?}" "${CI_VERIFICATION_RUN_URL:?}"
: "${GITHUB_SERVER_URL:?}" "${GITHUB_REPOSITORY:?}" "${GITHUB_RUN_ID:?}"
[[ "$RELEASE_SHA" =~ ^[0-9a-fA-F]{40}$ && "$TDLIB_COMMIT" =~ ^[0-9a-fA-F]{40}$ ]] || { echo 'Invalid source revision' >&2; exit 1; }
[[ "$RELEASE_TAG" == "v$RELEASE_VERSION" && "$RELEASE_VERSION_CODE" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid release version metadata' >&2; exit 1; }
[[ "$CI_VERIFICATION_RUN_ID" =~ ^[1-9][0-9]*$ && "$GITHUB_RUN_ID" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid workflow run identity' >&2; exit 1; }
[[ "$CI_VERIFICATION_RUN_URL" == "$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$CI_VERIFICATION_RUN_ID" ]] || { echo 'Enterprise CI evidence URL does not match its run identity' >&2; exit 1; }
cert="$(printf '%s' "$RELEASE_CERT_SHA256" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')"
[[ "$cert" =~ ^[0-9a-f]{64}$ ]] || { echo 'Invalid signing certificate digest' >&2; exit 1; }

declare -A APK_HASHES
for kind in armeabi-v7a arm64-v8a universal; do
  mapfile -t files < <(find "$OUT_DIR" -maxdepth 1 -type f -name "*-$kind.apk" -print)
  [[ ${#files[@]} -eq 1 ]] || { echo "Expected one verified $kind APK" >&2; exit 1; }
  APK_HASHES[$kind]="$(sha256sum "${files[0]}" | awk '{print tolower($1)}')"
done

# jq variables are passed through --arg, not expanded by Bash.
# shellcheck disable=SC2016
jq -n --arg tag "$RELEASE_TAG" --arg commit "$RELEASE_SHA" --arg version "$RELEASE_VERSION" \
  --arg tdlib "$TDLIB_COMMIT" --arg cert "$cert" --arg versionCode "$RELEASE_VERSION_CODE" \
  --arg ciRunId "$CI_VERIFICATION_RUN_ID" --arg ciUrl "$CI_VERIFICATION_RUN_URL" \
  --arg buildRunId "$GITHUB_RUN_ID" --arg buildUrl "$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID" \
  --arg arm32 "${APK_HASHES[armeabi-v7a]}" --arg arm64 "${APK_HASHES[arm64-v8a]}" --arg universal "${APK_HASHES[universal]}" \
  '{
    tag: $tag, commit: $commit, versionName: $version, versionCode: ($versionCode|tonumber),
    tdlibCommit: $tdlib, signingCertificateSha256: $cert,
    apkSha256: {arm32: $arm32, arm64: $arm64, universal: $universal},
    ciVerification: {workflow: "android-enterprise.yml", sourceCommit: $commit, runId: ($ciRunId|tonumber), url: $ciUrl, job: "Build and verify"},
    buildProvenance: {workflow: "android-release.yml", runId: ($buildRunId|tonumber), url: $buildUrl},
    deviceQualification: {status: "pending"}, deviceQualificationEvidence: null,
    disabledProductionFeatures: ["private-media-cast", "crash-reporting", "engine-lab", "legacy-streaming-fallback"]
  }' > "$OUT_DIR/RELEASE_MANIFEST.json"
