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

# This workflow is intentionally dispatched or merge-triggered on protected
# main after an explicit user request to keep only the main branch. Unmerged
# commits are preserved by immutable archive tags; active PRs are closed only
# after their exact source commits are saved and verified.
[[ "${ONE_BRANCH_CLEANUP:-false}" == "true" ]] || {
  echo "::error::Explicit one-branch cleanup approval is required." >&2
  exit 1
}
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
  open_prs="$(gh api -X GET "repos/$REPO/pulls" \
    -f "state=open" -f "head=${REPO%%/*}:$branch" -f "per_page=100")"
  open_count="$(jq -er 'length' <<< "$open_prs")"
  # Never close an unrelated PR, or a PR whose head has changed.
  jq -e --arg repo "$REPO" --arg branch "$branch" --arg sha "$original_sha" '
    all(.[]; .head.repo.full_name == $repo and
      .head.ref == $branch and .head.sha == $sha)
  ' <<< "$open_prs" >/dev/null || {
    echo "::error::PR head changed or belongs to another repository for $branch." >&2
    exit 1
  }
  if ! branch_archive_cleanup_eligible "$branch" "$ahead" "$protected" "$ONE_BRANCH_CLEANUP"; then
    echo "::warning::Keeping $branch (main-ahead=$ahead, protected=$protected)"
    skipped=$((skipped + 1))
    continue
  fi

  archive_ref="refs/tags/archive/branch-cleanup/$ARCHIVE_RUN/$branch"
  existing=""
  existing="$(gh api "repos/$REPO/git/ref/${archive_ref#refs/}" --jq '.object.sha // empty' 2>/dev/null)" || true
  if [[ -n "$existing" ]]; then
    [[ "$existing" == "$original_sha" ]] || {
      echo "::error::Archive ref collision for $branch" >&2
      exit 1
    }
  else
    gh api -X POST "repos/$REPO/git/refs" \
      -f ref="$archive_ref" -f sha="$original_sha" >/dev/null
  fi
  saved_sha="$(gh api "repos/$REPO/git/ref/${archive_ref#refs/}" --jq '.object.sha // empty')"
  [[ "$saved_sha" == "$original_sha" ]] || {
    echo "::error::Archive verification failed for $branch" >&2
    exit 1
  }
  archived=$((archived + 1))

  # Recheck the source before closing any open PR. A new push must not be
  # discarded based on a stale archive SHA.
  current_sha="$(gh api "repos/$REPO/git/ref/heads/$branch" --jq '.object.sha')"
  current_open_prs="$(gh api -X GET "repos/$REPO/pulls" \
    -f "state=open" -f "head=${REPO%%/*}:$branch" -f "per_page=100")"
  if [[ "$current_sha" != "$original_sha" ]] ||
      ! jq -e --arg repo "$REPO" --arg branch "$branch" --arg sha "$original_sha" '
        all(.[]; .head.repo.full_name == $repo and
          .head.ref == $branch and .head.sha == $sha)
      ' <<< "$current_open_prs" >/dev/null; then
    echo "::warning::Branch or PR changed during archival; keeping $branch"
    skipped=$((skipped + 1))
    continue
  fi
  if [[ "$(jq -r 'length' <<< "$current_open_prs")" != "$open_count" ]]; then
    echo "::warning::Open PR count changed for $branch; keeping branch."
    skipped=$((skipped + 1))
    continue
  fi

  # Preserve the experimental PR discussion and a directly recoverable tag,
  # rather than merging untested experimental playback code to main.
  while IFS= read -r pr_number; do
    [[ -n "$pr_number" ]] || continue
    gh api -X POST "repos/$REPO/issues/$pr_number/comments" \
      -f body="Archived commit $original_sha as tag \`$archive_ref\` for the requested main-only branch cleanup. The experimental code was NOT merged. Restore from the archive tag if hardware qualification later supports it." >/dev/null
    gh api -X PATCH "repos/$REPO/pulls/$pr_number" -f state=closed >/dev/null
  done < <(jq -r '.[].number' <<< "$current_open_prs")

  # Account for PRs opened while the archive ran, and verify the head lease.
  current_sha="$(gh api "repos/$REPO/git/ref/heads/$branch" --jq '.object.sha')"
  current_open_count="$(gh api -X GET "repos/$REPO/pulls" \
    -f "state=open" -f "head=${REPO%%/*}:$branch" -f "per_page=1" --jq 'length')"
  if [[ "$current_sha" != "$original_sha" || "$current_open_count" != "0" ]]; then
    echo "::warning::Branch or PR changed after archive; keeping $branch"
    skipped=$((skipped + 1))
    continue
  fi
  gh api -X DELETE "repos/$REPO/git/refs/heads/$branch" >/dev/null
  deleted=$((deleted + 1))
  echo "Archived and removed branch: $branch (original SHA=$original_sha)"
done <<< "$branches"

{
  echo "Archived $archived branches and deleted $deleted verified refs; preserved $skipped protected or changed branches."
} >> "$GITHUB_STEP_SUMMARY"
remaining="$(gh api --paginate "repos/$REPO/branches?per_page=100" --jq '.[].name' | grep -v '^main$' || true)"
if [[ -n "$remaining" ]]; then
  echo "::warning::Protected or concurrently changed branches remain; cleanup is incomplete." >&2
  printf '%s\n' "$remaining" >&2
  exit 1
fi
echo "Verified: main is the only remaining branch." >> "$GITHUB_STEP_SUMMARY"
