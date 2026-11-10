#!/usr/bin/env bash
# Idempotent immutable debug prerelease tags, authenticated through GH_TOKEN.
# Do not use git fetch: the release workflow intentionally disables checkout creds.
set -euo pipefail

[[ "$#" -eq 3 ]] || { echo 'Usage: ensure_debug_release_tag.sh tag SHA owner/repo' >&2; exit 2; }
tag="$1"; sha="$2"; repo="$3"
[[ "$tag" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)-debug\.[0-9]{8}T[0-9]{6}Z$ &&
   "$sha" =~ ^[0-9a-fA-F]{40}$ &&
   "$repo" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || {
  echo 'Invalid immutable debug release tag identity' >&2
  exit 2
}

if existing="$(gh api "repos/$repo/git/ref/tags/$tag" 2>/dev/null)"; then
  object_type="$(jq -er --arg ref "refs/tags/$tag" '
    select(.ref == $ref) | .object.type | select(. == "commit" or . == "tag")
  ' <<<"$existing")" || {
    echo 'Existing tag has an invalid target' >&2
    exit 1
  }
  target_sha="$(jq -er '.object.sha | select(test("^[a-fA-F0-9]{40}$"))' <<<"$existing")"
  if [[ "$object_type" == 'tag' ]]; then
    # Accept an existing annotated tag only if it resolves to the same commit.
    resolved="$(gh api "repos/$repo/git/tags/$target_sha")" || {
      echo 'Unable to verify existing annotated tag target' >&2
      exit 1
    }
    target_sha="$(jq -er '.object | select(.type == "commit") | .sha |
      select(test("^[a-fA-F0-9]{40}$"))' <<<"$resolved")" || {
        echo 'Existing annotated tag does not point to a commit' >&2
        exit 1
      }
  fi
  [[ "$target_sha" == "$sha" ]] || {
    echo 'Debug release tag already points to a different commit; refusing overwrite' >&2
    exit 1
  }
  echo "Verified existing immutable debug tag: $tag"
else
  # GitHub API rejects duplicate names and missing permissions. Never overwrite.
  gh api --method POST "repos/$repo/git/refs" \
    --raw-field "ref=refs/tags/$tag" \
    --raw-field "sha=$sha" >/dev/null
  echo "Created immutable debug tag: $tag"
fi
