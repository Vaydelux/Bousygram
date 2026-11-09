#!/usr/bin/env bash
# Prints only an exact-matching, latest successful mainline emulator run ID.
set -euo pipefail
[[ "$#" == 3 ]] || { echo 'Usage: verify_debug_emulator_gate.sh runs.json SHA owner/repo' >&2; exit 2; }
json="$1"; sha="$2"; repo="$3"
[[ "$sha" =~ ^[[:xdigit:]]{40}$ && "$repo" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || {
  echo 'Invalid source identity' >&2
  exit 2
}
jq -er --arg sha "$sha" --arg repo "$repo" '
  [.workflow_runs[] | select(
    .head_sha == $sha and .head_branch == "main" and .event == "push" and
    .head_repository.full_name == $repo and
    .path == ".github/workflows/android-emulator-smoke.yml"
  )] | sort_by(.run_number, .run_attempt, .id) | last |
  select(.status == "completed" and .conclusion == "success") |
  .id | select(type == "number" and . > 0)
' "$json"
