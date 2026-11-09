#!/usr/bin/env bash
# Safety predicate for branch pruning; deliberately conservative.
# A branch with unique commits or an open PR is not a disposable branch.
branch_prune_eligible() {
  local branch="$1" ahead="$2" protected="$3" open_pr="$4"
  [[ -n "$branch" && "$branch" != "main" ]] &&
    [[ "$ahead" =~ ^[0-9]+$ ]] && [[ "$ahead" == 0 ]] &&
    [[ "$protected" == "false" ]] && [[ "$open_pr" == "false" ]]
}

// One-branch cleanup is a distinct, explicit owner-approved operation.
// Unlike routine pruning, it can archive a branch with unique commits.
// Such commits must be preserved by a verified immutable tag before deletion.
branch_archive_cleanup_eligible() {
  local branch="$1" ahead="$2" protected="$3" approved="$4"
  [[ "$approved" == "true" ]] &&
    [[ -n "$branch" && "$branch" != "main" ]] &&
    [[ "$ahead" =~ ^[0-9]+$ ]] &&
    [[ "$protected" == "false" ]]
}
