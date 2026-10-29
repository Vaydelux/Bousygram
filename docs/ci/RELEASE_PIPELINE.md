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

## APK upgrade-safe version codes

The Gradle build derives Android `versionCode` from the same semantic `versionName` used in the signed release: `MAJOR × 1,000,000 + MINOR × 1,000 + PATCH`. This lets a new version install as an upgrade over the prior one without uninstalling and losing local Telegram data. For example, `v1.0.0` yields code `1000000` and `v1.0.1` yields `1000001`. The previous fixed debug code was `10`.

Each minor and patch field is limited to 0–999, and major to 0–2099, preserving Android's 2,100,000,000 code limit. Invalid or out-of-range versions fail configuration rather than building an APK that cannot be upgraded. `verifyReleaseVersionPolicy` exercises the same Gradle function with positive and negative fixtures in CI.

The existing production signing certificate must also match the previously installed production APK for an in-place update; a debug-signed APK cannot be upgraded to a differently signed production APK merely by increasing `versionCode`.

## Protected release stages

`android-release.yml` is manual-only and can still be run directly by providing `tag` and `source_sha`. It verifies that the tag resolves to the exact requested SHA in `main` history, checks a successful exact-commit mainline Android Enterprise run, and enters the protected `production-signing` environment.

It builds and verifies three **hardened, production-signed** APKs: `armeabi-v7a`, `arm64-v8a`, and `universal`. The APK verification checks signatures, package/version metadata, ABI/JNI content, APK integrity, and SHA-256 digests.

The release candidate initially records `deviceQualification.status=pending`. Only genuine protected device evidence bound to the **same tag, commit, and exact signed APK hashes** can advance the `release-qualification` gate. The `release-draft` environment controls draft creation; the separate `release` environment controls publication.

Configure protected reviewers and secrets as described in [RELEASE_CONFIGURATION.md](RELEASE_CONFIGURATION.md), and submit the hardware evidence format from [RELEASE_DEVICE_QUALIFICATION_RECORD.md](RELEASE_DEVICE_QUALIFICATION_RECORD.md).

No release is published automatically from `main`, pull requests, or an unqualified build. Starting **Release Bousygram APKs** requests the controlled process; it does **not** mean the release has passed signing or hardware qualification.
