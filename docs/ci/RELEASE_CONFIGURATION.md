# Release Configuration

## Required GitHub environments

Create the following protected environments in **Settings → Environments**, with appropriate required reviewers and restricted access:

- `production-signing`: protects the release signing keystore and certificate.
- `release-qualification`: requires human-reviewed device qualification evidence tied to the exact signed candidate APKs.
- `release-draft`: controls creation of a verified draft GitHub Release.
- `release`: separately approves publication of the existing draft.

The one-input dispatcher (`android-apk-release.yml`) uses `GITHUB_TOKEN` with `contents:write` to create a version tag and `actions:write` to start the existing controlled workflow. A personal access token is not required. Tag protection settings must allow the authorized Actions actor to create new tags; existing tags are never moved.

## Required secrets in production-signing

- `RELEASE_KEYSTORE_BASE64` — Base64-encoded production Android keystore.
- `RELEASE_STORE_PASSWORD` — keystore password.
- `RELEASE_KEY_ALIAS` — signing key alias.
- `RELEASE_KEY_PASSWORD` — signing key password.
- `RELEASE_CERT_SHA256` — expected signing-certificate SHA-256 fingerprint.

The keystore is decoded only on the protected signing runner and cleaned up afterward. Never commit signing credentials or keystore contents.

## Required secret in release-qualification

- `RELEASE_DEVICE_QUALIFICATION_JSON` — protected JSON record with `status=passed`, matching tag/commit, `nativeTdlibRuntime=passed`, reviewer approval, an HTTPS evidence URL, and the exact SHA-256 of all three signed APKs.

Its documented structure is in [RELEASE_DEVICE_QUALIFICATION_RECORD.md](RELEASE_DEVICE_QUALIFICATION_RECORD.md). This is **not** the old `RELEASE_DEVICE_QUALIFICATION_STATUS` or `RELEASE_DEVICE_QUALIFICATION_URL` variable pair. Those obsolete variables do not satisfy the current validator.

Because the hashes are generated in the signing job, the qualification record must be prepared from **that exact candidate's verified checksums**, after real device checks. A prefilled generic qualification flag is insufficient.

## Avoid the raw-SHA workflow for normal releases

The **Android Controlled Release** workflow is the protected, internal downstream pipeline. Its two inputs require an **existing version tag** and the **full 40-character Git commit SHA** pointed to by that tag. A placeholder such as `test` is not a valid SHA and is rejected before checkout.

For normal use, select **Actions → Release Bousygram APKs**, not **Android Controlled Release**. That one-input dispatcher resolves the SHA for you. It automatically creates or reuses an immutable tag after verifying a CI-green `main` commit, plus signing, hardware qualification and reviewer approvals. It never publishes a release merely because the dispatcher succeeds.

To obtain an installable, **debug-only** test APK without production signing or a version tag, open a successful **Android Enterprise Checks** run on `main` and download its `debug-apks-<commit>` artifact. Debug APKs are for development/testing only and must not be presented as production releases.

## How to request a release

1. Obtain a green `Android Enterprise Checks` run for the commit on `main`.
2. Select **Actions → Release Bousygram APKs → Run workflow**, choose `main`, and leave the version field at `auto`.
3. It checks CI, generates or reuses a safe version tag, and starts **Android Controlled Release**.
4. Follow **Android Controlled Release** for signing, candidate artifacts, qualification, draft approval, and publication.
5. Download the ARM32, ARM64, or universal APK from the published GitHub Release only after all required checks and approvals succeed.

A failed release request must be repaired; never bypass signing or device verification to obtain a green status. The dispatcher creates or reuses a version tag but **does not publish a release** directly. Production signing and device qualification are still mandatory.
