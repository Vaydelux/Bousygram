#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${1:?Verified release artifact directory required}"
QUALIFICATION_FILE="${2:?Protected qualification JSON file required}"
RELEASE_TAG="${3:?Release tag required}"
RELEASE_SHA="${4:?Release source SHA required}"
MANIFEST="$OUT_DIR/RELEASE_MANIFEST.json"

# A signed release must have reviewed results from all supported device classes,
# not merely a top-level "passed" flag and a URL. The protected environment still
# requires human review of the linked evidence; JSON cannot authenticate hardware.
# shellcheck disable=SC2016
jq -e --arg tag "$RELEASE_TAG" --arg sha "$RELEASE_SHA" '
  def httpsEvidence:
    type == "string" and (length >= 12 and length <= 2048) and
    test("^https://[^/[:space:]]+(/[^[:space:]]*)?$");
  def namedDevice($abi; $hash):
    type == "object" and
    .status == "passed" and .abi == $abi and
    .nativeTdlibRuntime == "passed" and .apkSha256 == $hash and
    (.model | type == "string" and length >= 2 and length <= 120) and
    (.androidVersion | type == "string" and length >= 1 and length <= 40) and
    (.evidenceUrl | httpsEvidence) and
    (.iterations | type == "number" and . >= 3 and . <= 10000);
  . as $q |
  .status == "passed" and .tag == $tag and .commit == $sha and
  .nativeTdlibRuntime == "passed" and .reviewerApproval == true and
  .openP0P1 == 0 and
  (.evidenceUrl | httpsEvidence) and
  (.apkSha256.arm32 | test("^[0-9a-fA-F]{64}$")) and
  (.apkSha256.arm64 | test("^[0-9a-fA-F]{64}$")) and
  (.apkSha256.universal | test("^[0-9a-fA-F]{64}$")) and
  (.deviceResults.arm32Tv | namedDevice("armeabi-v7a"; $q.apkSha256.arm32)) and
  (.deviceResults.arm64Tv | namedDevice("arm64-v8a"; $q.apkSha256.arm64)) and
  (.deviceResults.arm64Phone | namedDevice("arm64-v8a"; $q.apkSha256.arm64)) and
  (.deviceResults.arm64Tablet | namedDevice("arm64-v8a"; $q.apkSha256.universal)) and
  (["authenticatedStartup", "mkvTsSeek", "audioOnly", "mpvFallback",
    "forceStopReopen", "networkRecovery", "accountIsolation",
    "catalogNavigation", "lifecycle", "longSession"] |
    all(.[]; $q.scenarioResults[.] == "passed"))
' "$QUALIFICATION_FILE" >/dev/null || { echo 'Release blocked: missing physical-device matrix, scenario evidence, reviewer approval, or exact candidate identity.' >&2; exit 1; }
# shellcheck disable=SC2016
jq -e --arg tag "$RELEASE_TAG" --arg sha "$RELEASE_SHA" '
  .tag == $tag and .commit == $sha and .ciVerification.sourceCommit == $sha and
  .ciVerification.workflow == "android-enterprise.yml" and .ciVerification.job == "Build and verify" and
  (.ciVerification.runId | type == "number" and . > 0) and .deviceQualification.status == "pending"
' "$MANIFEST" >/dev/null || { echo 'Release blocked: candidate manifest identity is invalid.' >&2; exit 1; }
sha256sum -c "$OUT_DIR/SHA256SUMS.txt"

declare -A APK_HASHES
for kind in armeabi-v7a arm64-v8a universal; do
  mapfile -t files < <(find "$OUT_DIR" -maxdepth 1 -type f -name "*-$kind.apk" -print)
  [[ ${#files[@]} -eq 1 ]] || { echo "Expected one $kind candidate APK" >&2; exit 1; }
  APK_HASHES[$kind]="$(sha256sum "${files[0]}" | awk '{print tolower($1)}')"
done
# shellcheck disable=SC2016
jq -e --arg a "${APK_HASHES[armeabi-v7a]}" --arg b "${APK_HASHES[arm64-v8a]}" --arg u "${APK_HASHES[universal]}" \
  '((.apkSha256.arm32|ascii_downcase) == $a) and ((.apkSha256.arm64|ascii_downcase) == $b) and ((.apkSha256.universal|ascii_downcase) == $u)' \
  "$QUALIFICATION_FILE" >/dev/null || { echo 'Release blocked: device evidence hashes do not match the signed candidate APKs.' >&2; exit 1; }
# shellcheck disable=SC2016
jq -e --arg a "${APK_HASHES[armeabi-v7a]}" --arg b "${APK_HASHES[arm64-v8a]}" --arg u "${APK_HASHES[universal]}" \
  '.apkSha256 == {arm32: $a, arm64: $b, universal: $u}' "$MANIFEST" >/dev/null || { echo 'Release blocked: candidate manifest hashes do not match its APKs.' >&2; exit 1; }

# Export only the reviewed qualification fields; unrelated protected JSON stays private.
jq '{status, tag, commit, nativeTdlibRuntime, reviewerApproval, evidenceUrl, apkSha256, openP0P1, deviceResults, scenarioResults}' \
  "$QUALIFICATION_FILE" > "$OUT_DIR/DEVICE_QUALIFICATION.json"
# shellcheck disable=SC2016
jq --slurpfile q "$OUT_DIR/DEVICE_QUALIFICATION.json" '
  .deviceQualificationEvidence = $q[0].evidenceUrl |
  .deviceQualification = {status: $q[0].status, nativeTdlibRuntime: $q[0].nativeTdlibRuntime, reviewerApproval: $q[0].reviewerApproval}
' "$MANIFEST" > "$OUT_DIR/RELEASE_MANIFEST.json.tmp"
mv -- "$OUT_DIR/RELEASE_MANIFEST.json.tmp" "$MANIFEST"
