# GitHub Actions Audit

Latest audited checkout HEAD: `e11e09705361b7ad203566fb9a02aac1039c29f0` (2026-10-29). Latest enterprise run: [40817939430](https://github.com/Vaydelux/Bousygram/actions/runs/40817939430).

## Latest remote evidence

Wrapper integrity, wrapper negative fixtures, SDK installation, and the API 37 pre-build probe passed. The Gradle verification step failed at `verifyTdlibBindingCompatibility`: checked-in `TdApi.java` uses `cb863c1600082404428f1a84e407b866b9d412a8`, while native metadata pins `4c6377aa7386c39217878a9a56d581922ad37ca4`. ABI coverage, TV hardening, log redaction, and mismatch fixtures passed before that failure. Diagnostic reports were uploaded; debug APK normalization and upload were skipped. Firebase reported `NOT CONFIGURED`; instrumentation was skipped.

## Findings

| Area | Finding | Current handling |
|---|---|---|
| Wrapper | Gradle wrapper integrity and negative fixtures pass remotely. | Required before SDK installation. |
| SDK | Hosted SDK advertises API 37 as `platforms;android-37.0`. | Shared resolver installs and verifies the discovered platform and Build Tools; SDK fixtures are now a required enterprise step. |
| TDLib | Java binding and JNI native source revisions differ. | Compatibility gate fails before application compilation and APK packaging. |
| Debug APKs | APK artifacts must not be called verified after a failed Gradle gate. | Normalize and upload only on full job success; retain diagnostics with `always()`. |
| Firebase | Current remote run has no Firebase configuration. | Explicit `NOT CONFIGURED` job; this is not hardware qualification. |
| Signing | Normal CI does not need release signing credentials. | Signing stays inside the protected production-signing job. |
| Publishing | Release is separate from ordinary CI. | Draft and publish are protected manual stages; publication has not been run. |

## Workflow inventory and local audit

- `android-enterprise.yml`: pull requests, mainline/release pushes, and manual verification. Read-only repository permissions; action references use full commit SHAs; diagnostics and APK artifacts use separate success conditions.
- `android-release.yml`: manual-only. It validates a protected tag and exact source SHA, requires the exact successful mainline workflow run and its `Build and verify` job, signs in the protected environment, binds qualification to candidate APK hashes, and gates publishing through a separate protected environment.

The workflow edits in this working tree have not run remotely. Actionlint is not installed in this Windows environment. No release was drafted or published.
