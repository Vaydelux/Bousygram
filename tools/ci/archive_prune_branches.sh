#!/usr/bin/env bash
set -Eeuo pipefail
: "${REPO:?GitHub repo required}"
: "${ARCHIVE_RUN:?GitHub Actions run ID required}"
: "${GH_TOKEN:?GitHub Actions token required}"
if [[ "${GITHUB_REF:-}" != "refs/heads/main" ]]; then
  echo "::error::Branch pruning must run from the main ref." >&2
  exit 1
fi
# shellcheck source=tools/ci/branch_prune_policy.sh
source "$(dirname "${BASH_SOURCE[0]}")/branch_prune_policy.sh"

# Branch names may contain slashes; preserve original names from the API.
branches="$(gh api --paginate "repos/$REPO/branches?per_page=100" --jq '.[] | [.name, (.protected | tostring)] | @tsv')"
archived=0
deleted=0
skipped=0
while IFS=$'\t' read -r branch protected; do
  [[ -n "$branch" && "$branch" != "main" ]] || continue
  original_sha="$(gh api "repos/$REPO/git/ref/heads/$branch" --jq '.object.sha')"
  [[ "$original_sha" =~ ^[a-f0-9]{40}$ ]] || {
    echo "::error::Invalid SHA for $branch" >&2
    exit 1
  }
  # GitHub's compare endpoint compares main with the immutable SHA rather
  # than a moving branch name. Ahead-by > 0 means unique, unmerged commits.
  ahead="$(gh api "repos/$REPO/compare/main...$original_sha" --jq '.ahead_by')"
  open_count="$(gh api -X GET "repos/$REPO/pulls" \
    -f "state=open" -f "head=${REPO%%/*}:$branch" -f "per_page=1" --jq 'length')"
  [[ "$open_count" =~ ^[0-9]+$ ]] || {
    echo "::error::Unable to verify open PRs for $branch" >&2
    exit 1
  }
  open_pr=false
  if (( open_count > 0 )); then open_pr=true; fi
  if ! branch_prune_eligible "$branch" "$ahead" "$protected" "$open_pr"; then
    echo "Preserving $branch (main-ahead=$ahead, protected=$protected, openPR=$open_pr)"
    skipped=$((skipped + 1))
    continue
  fi

  archive_ref="refs/tags/archive/branch-cleanup/$ARCHIVE_RUN/$branch"
  existing=""
  existing="$(gh api "repos/$REPO/git/ref/${archive_ref#refs/}" --jq '.object.sha' 2>/dev/null)" || true
  if [[ -n "$existing" ]]; then
    [[ "$existing" == "$original_sha" ]] || {
      echo "::error::Archive ref collision for $branch" >&2
      exit 1
    }
  else
    gh api -X POST "repos/$REPO/git/refs" \
      -f ref="$archive_ref" -f sha="$original_sha" >/dev/null
  fi
  saved_sha="$(gh api "repos/$REPO/git/ref/${archive_ref#refs/}" --jq '.object.sha')"
  [[ "$saved_sha" == "$original_sha" ]] || {
    echo "::error::Archive verification failed for $branch" >&2
    exit 1
  }
  archived=$((archived + 1))

  # Recheck the head lease and open PR immediately before deletion. An
  # operator can push new commits or open a PR during the archive action.
  current_sha="$(gh api "repos/$REPO/git/ref/heads/$branch" --jq '.object.sha')"
  current_open_count="$(gh api -X GET "repos/$REPO/pulls" \
    -f "state=open" -f "head=${REPO%%/*}:$branch" -f "per_page=1" --jq 'length')"
  if [[ "$current_sha" != "$original_sha" || ! "$current_open_count" =~ ^[0-9]+$ ]] ||
     (( current_open_count > 0 )); then
    echo "Branch or PR changed during archive; keeping $branch"
    skipped=$((skipped + 1))
    continue
  fi
  gh api -X DELETE "repos/$REPO/git/refs/heads/$branch" >/dev/null
  deleted=$((deleted + 1))
  echo "Archived and pruned fully merged branch: $branch"
done <<< "$branches"

{
  echo "Archived $archived and deleted $deleted fully merged branches; preserved $skipped active/unmerged branches."
} >> "$GITHUB_STEP_SUMMARY"
remaining="$(gh api --paginate "repos/$REPO/branches?per_page=100" --jq '.[].name' | grep -v '^main$' || true)"
if [[ -n "$remaining" ]]; then
  echo "::warning::Unmerged, protected, or active PR branches remain. Do not delete them before review." >&2
  printf '%s\n' "$remaining" >&2
  exit 1
fi
echo "Verified: main is the only remaining branch." >> "$GITHUB_STEP_SUMMARY"
