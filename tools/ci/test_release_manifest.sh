#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
out="$tmp/release-artifacts"
mkdir -p "$out"
# These fixtures test provenance only, not APK signing or Android package validation.
for kind in armeabi-v7a arm64-v8a universal; do printf 'fixture %s\n' "$kind" > "$out/Bousygram-v1.2.3-hardenedRelease-$kind.apk"; done
sha256sum "$out"/*.apk > "$out/SHA256SUMS.txt"
export RELEASE_TAG=v1.2.3 RELEASE_VERSION=1.2.3 RELEASE_VERSION_CODE=10
export RELEASE_SHA=1111111111111111111111111111111111111111
export TDLIB_COMMIT=2222222222222222222222222222222222222222
export RELEASE_CERT_SHA256=3333333333333333333333333333333333333333333333333333333333333333
export CI_VERIFICATION_RUN_ID=42 GITHUB_RUN_ID=99 GITHUB_SERVER_URL=https://github.com GITHUB_REPOSITORY=fixture/repo
export CI_VERIFICATION_RUN_URL=https://github.com/fixture/repo/actions/runs/42
bash "$ROOT/tools/ci/create_release_manifest.sh" "$out"
jq -e '.ciVerification.runId == 42 and .buildProvenance.runId == 99 and .deviceQualificationEvidence == null and .deviceQualification.status == "pending"' "$out/RELEASE_MANIFEST.json" >/dev/null
# shellcheck disable=SC2016
jq --arg tag "$RELEASE_TAG" --arg commit "$RELEASE_SHA" '
  def device($abi; $hash; $name): {
    status:"passed", abi:$abi, apkSha256:$hash, nativeTdlibRuntime:"passed",
    model:$name, androidVersion:"15", iterations:3,
    evidenceUrl:"https://qualification.example/review/device-7"
  };
  . as $m | {
    status:"passed", tag:$tag, commit:$commit,
    nativeTdlibRuntime:"passed", reviewerApproval:true, openP0P1:0,
    evidenceUrl:"https://qualification.example/review/7",
    apkSha256:$m.apkSha256,
    deviceResults: {
      arm32Tv: device("armeabi-v7a"; $m.apkSha256.arm32; "ARM32 TV"),
      arm64Tv: device("arm64-v8a"; $m.apkSha256.arm64; "ARM64 TV"),
      arm64Phone: device("arm64-v8a"; $m.apkSha256.arm64; "ARM64 Phone"),
      arm64Tablet: device("arm64-v8a"; $m.apkSha256.universal; "ARM64 Tablet")
    },
    scenarioResults: (
      ["authenticatedStartup", "mkvTsSeek", "audioOnly", "mpvFallback",
       "forceStopReopen", "networkRecovery", "accountIsolation",
       "catalogNavigation", "lifecycle", "longSession"] |
      map({key: ., value: "passed"}) | from_entries
    ),
    unrelatedSecret:"must-not-be-exported"
  }
' "$out/RELEASE_MANIFEST.json" > "$tmp/qualification.json"

expect_rejected() {
  if bash "$ROOT/tools/ci/qualify_release_manifest.sh" "$out" "$tmp/rejected.json" "$RELEASE_TAG" "$RELEASE_SHA" > /dev/null 2>&1; then
    echo "Qualification accepted invalid fixture: $1" >&2; exit 1
  fi
  jq -e '.deviceQualification.status == "pending" and .deviceQualificationEvidence == null' "$out/RELEASE_MANIFEST.json" >/dev/null
}
jq '.commit = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'wrong commit'
jq '.apkSha256.arm64 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'wrong APK hash'
jq '.reviewerApproval = false' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'missing approval'
jq '.nativeTdlibRuntime = "not-run"' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'unverified native runtime'
jq '.deviceResults.arm32Tv = null' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'missing ARM32 TV qualification'
jq '.deviceResults.arm64Tablet.abi = "armeabi-v7a"' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'wrong tablet ABI'
jq '.deviceResults.arm64Tv.apkSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'device tested against another APK'
jq '.deviceResults.arm64Phone.iterations = 0' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'no repeated playback tests'
jq '.deviceResults.arm32Tv.evidenceUrl = "http://example.org/no-tls"' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'missing device HTTPS evidence'
jq '.scenarioResults.forceStopReopen = "not-run"' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'failed mandatory recovery scenario'
jq '.openP0P1 = 1' "$tmp/qualification.json" > "$tmp/rejected.json"
expect_rejected 'unresolved P1 release blocker'

bash "$ROOT/tools/ci/qualify_release_manifest.sh" "$out" "$tmp/qualification.json" "$RELEASE_TAG" "$RELEASE_SHA" > /dev/null
jq -e '.ciVerification.runId == 42 and .deviceQualification.status == "passed" and .deviceQualificationEvidence == "https://qualification.example/review/7"' "$out/RELEASE_MANIFEST.json" >/dev/null
jq -e 'has("unrelatedSecret") | not' "$out/DEVICE_QUALIFICATION.json" >/dev/null
jq -e '.deviceResults.arm32Tv.status == "passed" and .deviceResults.arm64Tablet.status == "passed" and .scenarioResults.networkRecovery == "passed" and .openP0P1 == 0' "$out/DEVICE_QUALIFICATION.json" >/dev/null
echo 'Release provenance and qualification fixtures passed'
