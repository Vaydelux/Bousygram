# Release Pipeline

`android-release.yml` is manual-only. It requires a full version tag and exact source SHA, verifies the tag resolves to that SHA, requires protected device qualification evidence, builds `hardenedRelease`, verifies ARM32/ARM64/universal APKs, checks signatures and native contents, creates a draft release, and publishes only after a separate protected environment approval.

No release is published automatically from `main`, pull requests, or arbitrary branches.
