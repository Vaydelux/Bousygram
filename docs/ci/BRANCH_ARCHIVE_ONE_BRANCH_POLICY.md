# Bousygram main-only branch cleanup — 2026-11-09

Purpose: honor the repository owner's explicit request for **one remaining branch: `main`**, without losing experimental or otherwise unmerged work.

## Policy

- **Never merge unqualified experimental work just to remove a branch.** Specifically, open PR #143 (bounded Telegram foreground downloads and resume cache) remains experimental and is *not* included in the production candidate unless separately reviewed, tested and merged.
- On an explicitly tagged merge commit (`[archive-prune-branches]`) or a manual workflow dispatch from `main`, run the archive-first script under a write-scoped token.
- Every non-main branch, including branches with commits ahead of `main`, receives a unique immutable tag: `refs/tags/archive/branch-cleanup/{workflow-run-id}/{original-branch-name}`.
- Verify the tag resolves to the **exact original branch head SHA** before deleting the branch.
- If a branch has a PR, ensure the PR still points to that exact head; comment with the archive ref and close it **after preserving code**. Do not treat closing the PR as a merge.
- Recheck the branch SHA, open PR state and protected status at cleanup time. If anything has changed unexpectedly, keep the branch and fail the one-branch assertion.
- Never delete `main`, protected branches or archive tags; never force-push or rewrite Git history.
- Branch cleanup affects *Git branches*, not tags or existing GitHub Releases. Previously archived experiment commits remain retrievable and can be branched from an archive tag.
- GitHub Actions branch/API permissions may prevent deletion; the workflow must report the partial result and **must not falsely claim** only main remains.

## Release / debug separation

Branch cleanup does not qualify or release an APK. The three-ABI debug prerelease publisher only runs after exact-commit mainline Android Enterprise and emulator checks succeed. Production-signed releases require independent signed-candidate hashes, hardware playback and protected reviewer evidence. See `docs/ci/RELEASE_DEVICE_QUALIFICATION_RECORD.md` and `docs/ci/RELEASE_PIPELINE.md`.

## Restore archived experimental code

Use a preserved tag in `archive/branch-cleanup/{run-id}/{old-name}` and create a new branch from that tag if the feature is needed again. It must pass the full CI and physical qualification process before any production merge.
