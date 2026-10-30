#!/usr/bin/env bash
# Verify human-readable, deterministic debug-release names before publication.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tagger="$root/tools/ci/debug_prerelease_tag.sh"

expected='v1.0.0-debug.20261030T090834Z'
first="$(bash "$tagger" 1.0.0 '2026-10-30T09:08:34Z')"
second="$(bash "$tagger" 1.0.0 '2026-10-30T09:08:34Z')"
[[ "$first" == "$expected" && "$second" == "$expected" ]] || {
  echo 'CI datetime release tags are not stable or have the wrong format' >&2
  exit 1
}
[[ "$(bash "$tagger" 2.1.3 '2026-01-01T00:00:00Z')" == 'v2.1.3-debug.20260101T000000Z' ]] || exit 1
reject() {
  if bash "$tagger" "$1" "$2" >/dev/null 2>&1; then
    echo "Invalid debug release metadata was accepted: version=$1 date=$2" >&2
    exit 1
  fi
}
reject 'v1.0.0' '2026-10-30T09:08:34Z'
reject '01.0.0' '2026-10-30T09:08:34Z'
reject '1.0.0;echo hacked' '2026-10-30T09:08:34Z'
reject '1.0.0' '2026-02-30T09:08:34Z'
reject '1.0.0' '2026-10-30T25:08:34Z'
reject '1.0.0' '2026-10-30T09:08:34+08:00'
reject '1.0.0' '2026-10-30'
reject '1.0.0' ''
echo 'Deterministic UTC debug release datetime tag tests passed.'
