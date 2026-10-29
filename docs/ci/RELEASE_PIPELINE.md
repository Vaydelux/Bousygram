# Release Pipeline

## Automatic release versioning

Open **Actions → Release Bousygram APKs → Run workflow**, select `main`, and keep the version field as `auto` (the default). You do **not** need to create or name a version tag manually.

The action verifies the exact `main` commit has green Android Enterprise CI, chooses a semantic version tag, creates an immutable annotated Git tag when missing, and dispatches the existing protected `android-release.yml` workflow.

- First version is `v1.0.0`; future releases increment the patch (`v1.0.1`, `v1.0.2`, ...).
- If a previous failed release created an unreleased tag on the same commit, `auto` reuses it rather than creating another.
- You may enter an explicit version like `v2.0.0`; existing tags are reused only when they already target the same exact commit.
- GitHub Releases and tags are never overwritten or force-moved. A signing or hardware-qualification failure can leave an unreleased tag.

The dispatcher requires `GITHUB_TOKEN` permissions `contents: write` to create tags and `actions: write` to start the downstream action. Protected tag policies may require authorization for the GitHub Actions actor.

Choosing `auto` does not bypass production signing, evidence collection, or manual publication approvals. The signed release is only published when **Android Controlled Release** passes its original gates.

## Protected release stages

`android-release.yml` is manual-only and can still be run directly by providing `tag` and `source_sha`. It verifies that the tag resolves to the exact requested SHA in `main` history, checks a successful exact-commit mainline Android Enterprise run, and enters the protected `production-signing` environment.

It builds and verifies three **hardened, production-signed** APKs: `armeabi-v7a`, `arm64-v8a`, and `universal`. The APK verification checks signatures, package/version metadata, ABI/JNI content, APK integrity, and SHA-256 digests.

The release candidate initially records `deviceQualification.status=pending`. Only genuine protected device evidence bound to the **same tag, commit, and exact signed APK hashes** can advance the `release-qualification` gate. The `release-draft` environment controls draft creation; the separate `release` environment controls publication.

Configure protected reviewers and secrets as described in [RELEASE_CONFIGURATION.md](RELEASE_CONFIGURATION.md), and submit the hardware evidence format from [RELEASE_DEVICE_QUALIFICATION_RECORD.md](RELEASE_DEVICE_QUALIFICATION_RECORD.md).

No release is published automatically from `main`, pull requests, or an unqualified build. Starting **Release Bousygram APKs** requests the controlled process; it does **not** mean the release has passed signing or hardware qualification.
