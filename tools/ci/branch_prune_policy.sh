#!/usr/bin/env bash
# Safety predicate for branch pruning; deliberately conservative.
# A branch with unique commits or an open PR is not a disposable branch.
branch_prune_eligible() {
  local branch="$1" ahead="$2" protected="$3" open_pr="$4"
  [[ -n "$branch" && "$branch" != "main" ]] &&
    [[ "$ahead" =~ ^[0-9]+$ ]] && [[ "$ahead" == 0 ]] &&
    [[ "$protected" == "false" ]] && [[ "$open_pr" == "false" ]]
}
