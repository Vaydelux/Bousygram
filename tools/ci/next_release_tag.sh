#!/usr/bin/env bash
# Select version labels using numeric version sort. No network calls or ref mutation.
set -euo pipefail
mode="${1:-}"
[[ "$mode" == latest || "$mode" == next ]] || {
  echo 'Usage: next_release_tag.sh latest|next' >&2
  exit 2
}
tags="$(git tag --list 'v*' | grep -E '^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$' | sort -V || true)"
latest="$(printf '%s\n' "$tags" | tail -n 1)"
if [[ "$mode" == latest ]]; then
  printf '%s\n' "$latest"
elif [[ -z "$latest" ]]; then
  echo v1.0.0
else
  [[ "$latest" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || exit 1
  printf 'v%s.%s.%s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "$(( ${BASH_REMATCH[3]} + 1 ))"
fi
