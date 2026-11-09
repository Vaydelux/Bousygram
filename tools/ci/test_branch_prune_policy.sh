#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=tools/ci/branch_prune_policy.sh
source "$(dirname "${BASH_SOURCE[0]}")/branch_prune_policy.sh"
allow() {
  if ! branch_prune_eligible "$@"; then
    echo "Expected branch eligible: $*" >&2
    exit 1
  fi
}
deny() {
  if branch_prune_eligible "$@"; then
    echo "Unsafe branch incorrectly eligible: $*" >&2
    exit 1
  fi
}
allow "merged/feature" 0 false false
deny main 0 false false
deny "open-pr" 0 false true
deny "unmerged-feature" 1 false false
deny "protected-release" 0 true false
deny "invalid-ahead" unknown false false
deny "" 0 false false
deny "inconsistent-flag" 0 false unknown
# A missing GitHub ref must not be treated as the literal "null" SHA.
missing_ref='{"message":"Not Found"}'
[[ -z "$(jq -r '.object.sha // empty' <<< "$missing_ref")" ]] || {
  echo 'Missing archive ref was mistaken for an existing tag' >&2
  exit 1
}
existing_ref='{"object":{"sha":"1111111111111111111111111111111111111111"}}'
[[ "$(jq -r '.object.sha // empty' <<< "$existing_ref")" == "1111111111111111111111111111111111111111" ]] || {
  echo 'Existing tag SHA was not preserved' >&2
  exit 1
}
echo "Branch archive/prune safety policy fixtures passed."
