#!/usr/bin/env bash
set -euo pipefail
tool="$(cd "$(dirname "$0")" && pwd)/verify_debug_emulator_gate.sh"
sha="0123456789abcdef0123456789abcdef01234567"
repo="Vaydelux/Bousygram"
tmp="$(mktemp)"
trap 'rm -f "$tmp" "$tmp.next"' EXIT
write_case() {
  local id="${1:-55}" status="${2:-completed}" result="${3:-success}" commit="${4:-$sha}" \
      owner="${5:-$repo}" ev="${6:-push}" ref="${7:-main}" path="${8:-.github/workflows/android-emulator-smoke.yml}"
  jq -n --argjson id "$id" --arg st "$status" --arg result "$result" \
    --arg sha "$commit" --arg owner "$owner" --arg ev "$ev" \
    --arg ref "$ref" --arg path "$path" \
    '{workflow_runs:[{id:$id,run_number:5,run_attempt:1,status:$st,conclusion:$result,
      head_sha:$sha,head_branch:$ref,event:$ev,path:$path,head_repository:{full_name:$owner}}]}' > "$tmp"
}
must_fail() {
  if bash "$tool" "$tmp" "$sha" "$repo" >/dev/null 2>&1; then
    echo 'Invalid emulator evidence was accepted.' >&2
    exit 1
  fi
}
write_case
[[ "$(bash "$tool" "$tmp" "$sha" "$repo")" == 55 ]]
write_case 55 completed failure; must_fail
write_case 55 in_progress success; must_fail
write_case 55 completed success 9999999999999999999999999999999999999999; must_fail
write_case 55 completed success "$sha" Other/Repo; must_fail
write_case 55 completed success "$sha" "$repo" pull_request; must_fail
write_case 55 completed success "$sha" "$repo" push feature; must_fail
write_case 55 completed success "$sha" "$repo" push main .github/workflows/android-enterprise.yml; must_fail
write_case
jq '.workflow_runs += [(.workflow_runs[0] | .id=56 | .run_number=6 | .conclusion="failure")]' "$tmp" > "$tmp.next"
mv "$tmp.next" "$tmp"
must_fail
printf '{"workflow_runs":[]}' > "$tmp"
must_fail
printf 'garbage' > "$tmp"
must_fail
echo 'Debug release emulator evidence: all fixtures passed.'
