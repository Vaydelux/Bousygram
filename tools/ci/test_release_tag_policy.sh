#!/usr/bin/env bash
set -euo pipefail
tools="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
git -C "$fixture" init -q
git -C "$fixture" -c user.name=CI -c user.email=ci@example.invalid commit --allow-empty -qm Initial
check() {
  local expected="$1" mode="$2" actual
  actual="$(cd "$fixture" && bash "$tools/next_release_tag.sh" "$mode")"
  [[ "$expected" == "$actual" ]] || {
    echo "Expected $mode '$expected' but got '$actual'" >&2
    exit 1
  }
}
check v1.0.0 next
check '' latest
git -C "$fixture" tag v1.0.0
check v1.0.1 next
git -C "$fixture" tag v1.0.9
git -C "$fixture" tag v1.0.10
check v1.0.11 next
git -C "$fixture" tag v2.1.0
git -C "$fixture" tag v1.99.99
check v2.1.0 latest
check v2.1.1 next
git -C "$fixture" tag v3.0.0-rc1
git -C "$fixture" tag v02.0.0
check v2.1.1 next
echo 'Automated release version policy tests passed.'
