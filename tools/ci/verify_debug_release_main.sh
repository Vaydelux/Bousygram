#!/usr/bin/env bash
# Fail closed unless CI-verified SHA is still an ancestor of the protected main.
# Auth uses GH_TOKEN via gh api, not git fetch (checkout disables persisted creds).
set -euo pipefail

[[ "$#" -eq 2 ]] || { echo 'Usage: verify_debug_release_main.sh SHA owner/repo' >&2; exit 2; }
sha="$1"; repo="$2"
[[ "$sha" =~ ^[0-9a-fA-F]{40}$ && "$repo" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || {
  echo 'Invalid verified release source identity' >&2
  exit 2
}
# GitHub's compare status is ahead when main contains additional descendants,
# identical when the SHA is the current main, and diverged/behind otherwise.
comparison="$(gh api "repos/$repo/compare/$sha...main")" || {
  echo 'Cannot verify mainline ancestry with authenticated GitHub API' >&2
  exit 1
}
jq -e --arg sha "$sha" '
  .base_commit.sha == $sha and
  (.status == "ahead" or .status == "identical")
' <<<"$comparison" >/dev/null || {
  echo 'CI source is no longer an ancestor of main; publication is blocked' >&2
  exit 1
}
echo "Verified exact release source is on main: $sha"
