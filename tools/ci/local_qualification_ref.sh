#!/usr/bin/env bash
# Local validation only. Never publish, dispatch Actions or modify git refs.
local_qualification_kind() {
  local head branch status
  head="$(git rev-parse HEAD)" || return 1
  branch="$(git branch --show-current)" || return 1
  status="$(git status --porcelain)" || {
    echo "ERROR: Unable to verify worktree cleanliness." >&2
    return 1
  }
  if [[ -n "$status" ]]; then
    echo "ERROR: Worktree must be clean for reproducible APK provenance." >&2
    return 1
  fi
  if [[ $# -eq 0 && "$branch" == main ]]; then
    printf 'debug\n'
  elif [[ $# -eq 2 && "$1" == --candidate && "$2" =~ ^[0-9a-f]{40}$ && "$2" == "$head" ]]; then
    printf 'candidate-debug\n'
  else
    echo "ERROR: Qualify clean main, or pass --candidate <exact HEAD SHA> for local-only qualification." >&2
    return 1
  fi
}
