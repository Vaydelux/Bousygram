# Release Pipeline

## One-input release request

After this change is merged to `main`, open **Actions → Release Bousygram APKs → Run workflow**. Select `main` and enter an **existing protected version tag**, such as `v1.0.0`.

The request workflow validates the tag, resolves the exact commit SHA, checks for successful commit-matched Android Enterprise CI, refuses to overwrite an existing release, and dispatches `android-release.yml`. It does not independently build, sign, publish, or bypass any production gate. The **Android Controlled Release** run is the authoritative record of whether a release was actually created.

Create the annotated tag on an already CI-green commit using an authorized account, under your repository's protected tag policy:

```bash
git checkout main
git pull --ff-only origin main
git tag -a v1.0.0 -m "Bousygram v1.0.0"
git push origin v1.0.0
```

Do not recreate, retarget, or force-push an existing version tag. If the tag points to a commit without successful mainline CI, the request is rejected.

## Protected release stages

`android-release.yml` is manual-only and can still be run directly by providing `tag` and `source_sha`. It verifies that the tag resolves to the exact requested SHA in `main` history, checks a successful exact-commit mainline Android Enterprise run, and enters the protected `production-signing` environment.

It builds and verifies three **hardened, production-signed** APKs: `armeabi-v7a`, `arm64-v8a`, and `universal`. The APK verification checks signatures, package/version metadata, ABI/JNI content, APK integrity, and SHA-256 digests.

The release candidate initially records `deviceQualification.status=pending`. Only genuine protected device evidence bound to the **same tag, commit, and exact signed APK hashes** can advance the `release-qualification` gate. The `release-draft` environment controls draft creation; the separate `release` environment controls publication.

Configure protected reviewers and secrets as described in [RELEASE_CONFIGURATION.md](RELEASE_CONFIGURATION.md), and submit the hardware evidence format from [RELEASE_DEVICE_QUALIFICATION_RECORD.md](RELEASE_DEVICE_QUALIFICATION_RECORD.md).

No release is published automatically from `main`, pull requests, or an unqualified build. Starting **Release Bousygram APKs** requests the controlled process; it does **not** mean the release has passed signing or hardware qualification.
