#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$root/tools/ci/local_qualification_ref.sh"
temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
cd "$temp"
git init -q -b main
git -c user.name=Fixture -c user.email=fixture@example.invalid commit -qm init --allow-empty
sha="$(git rev-parse HEAD)"
reject() {
  if local_qualification_kind "$@" >/dev/null 2>&1; then
    echo "Qualification unexpectedly accepted: $*" >&2
    exit 1
  fi
}
[[ "$(local_qualification_kind)" == debug ]]
git switch -qc candidate
reject
[[ "$(local_qualification_kind --candidate "$sha")" == candidate-debug ]]
reject --candidate "${sha:0:12}"
reject --candidate 0000000000000000000000000000000000000000
git checkout -q --detach
[[ "$(local_qualification_kind --candidate "$sha")" == candidate-debug ]]
touch dirty
reject --candidate "$sha"
rm dirty
# Failure to read repository status must not masquerade as a clean tree.
git() {
  if [[ "$1" == status ]]; then return 1; fi
  command git "$@"
}
# Verify the injected failure itself before exercising the sourced helper.
if git status --porcelain >/dev/null 2>&1; then
  echo "Status failure fixture did not activate" >&2
  exit 1
fi
reject --candidate "$sha"
unset -f git
echo "PASS: 8 local qualification-ref cases"
