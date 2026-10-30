# GitHub Production Release Signing Setup

The **debug prerelease** workflow already builds debug-signed APKs without production credentials.
Do **not** add a fallback to debug signing in the protected production release workflow.

## Why the October 30 controlled release failed

The [Android Controlled Release run 41065198085](https://github.com/Vaydelux/Bousygram/actions/runs/41065198085)
failed at **Validate signing secrets without printing them**. The runner received
empty signing environment variables and correctly refused to sign a production APK.
This is a *configuration blocker*, not an Android compilation failure.

The `signed-build` job uses the GitHub environment named **`production-signing`**.
Its first step now identifies missing secret *names*, without displaying any values.

## Configure the five secrets securely

In GitHub, open **Bousygram > Settings > Environments > production-signing**.
Create the environment if necessary, then add the following **Environment secrets**.
Repository-level Actions secrets can also be used, but environment secrets allow stricter access.

| GitHub secret | What to enter |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64 of your existing, private JKS or PKCS12 Android signing keystore file |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Alias of the private signing key inside the keystore |
| `RELEASE_KEY_PASSWORD` | Password for that private key |
| `RELEASE_CERT_SHA256` | The signing **certificate** SHA-256 fingerprint shown by `keytool -list -v` |

**Do not** put these values in an issue, pull request, chat message, build log,
`local.properties` committed to Git, or an APK asset. Do not commit the keystore.
Back up the original keystore, alias and passwords securely: future updates to an app
signed with a different certificate might not install over the existing app.

### Encode an existing keystore on Windows (PowerShell)

Run this on your own trusted machine, replacing the placeholder with your real file:

```powershell
$keystorePath = 'C:\secure\bousygram-release.keystore'
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath)) | Set-Clipboard
```

Paste the clipboard's content **only** into the GitHub secret
`RELEASE_KEYSTORE_BASE64`, then clear your clipboard. To inspect the signing
certificate locally:

```powershell
keytool -list -v -keystore 'C:\secure\bousygram-release.keystore' -alias 'your-existing-alias'
```

Copy the value labeled `SHA256:` to `RELEASE_CERT_SHA256`.
Do not confuse this certificate fingerprint with the keystore's file checksum.

If you do **not** already have a release key, create and back up a new one using
Android Studio's **Build > Generate Signed Bundle / APK** wizard or `keytool`
on your own machine. Never replace a key that already signs installed versions.

## Other protected-release prerequisites

The next job, `device-qualification`, uses the **`release-qualification`**
GitHub environment and its secret `RELEASE_DEVICE_QUALIFICATION_JSON`.
It must contain valid, **genuine** evidence from device testing as required by
`tools/ci/qualify_release_manifest.sh`. Do not fabricate qualification results or
disable this gate. The `release-draft` and `release` environments can enforce manual approvals.

The release request also requires:
- An **existing version tag** in the form `vMAJOR.MINOR.PATCH`, pointing at the exact requested SHA.
- A **successful Android Enterprise Checks push run** for that same main-branch SHA.
- Matching Android Gradle version metadata and successful ABI, signing and manifest verification.

A debug prerelease tag such as `v1.0.0-debug.20261030T154549Z` **is not**
a production tag. Do not move or reuse an existing production tag when releasing a
new commit; create a new version instead.

## Retry and confirm

1. Configure protected secrets and environment approvals in GitHub Settings.
2. Run the **Android Controlled Release** workflow for the desired existing
   production tag and its exact 40-character commit SHA (or use the repository's
   versioned release workflow when appropriate).
3. Confirm the `signed-build` job verifies all APK signatures against
   `RELEASE_CERT_SHA256`.
4. Provide device-qualification evidence and approve the remaining protected
   environments before publishing a **production-signed** release.

If you only need an APK for installing/testing on a TV or phone, use
**Bousygram Debug APK Prereleases** and download one of its verified debug APKs.
Production signing secrets are **not** required for that workflow.
