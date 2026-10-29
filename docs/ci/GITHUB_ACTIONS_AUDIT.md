# GitHub Actions Audit

Audited PR #3 commit: `d1f91f96b8ce5bc718ec759384d28a1c0e67789b`.

## Latest remote evidence

- [TDLib Java API generation run 40841661912](https://github.com/Vaydelux/Bousygram/actions/runs/40841661912) passed on Ubuntu 26.04 with GNU C/C++ 13.3.0. It checked out the exact TDLib source revision `4c6377aa7386c39217878a9a56d581922ad37ca4`, configured a host build with Unix Makefiles, and ran `tl_generate_java`. Configure/build logs and the generated source were archived.
- [Android Enterprise run 40841661942](https://github.com/Vaydelux/Bousygram/actions/runs/40841661942) passed. Workflow lint, ShellCheck, Bash syntax, wrapper integrity and negative fixtures, SDK setup and fixtures, all required Gradle verification, APK validation, and artifact uploads succeeded.
- The first corrected-source run exposed that the generated Java file had been truncated during transfer. The full source (5,424,652 bytes) was uploaded in PR #3; Kotlin compilation then passed. The artifact verifier was updated to select the single debug APK containing both supported JNI ABIs.
- Debug APK artifact: [run 40841661942 artifacts](https://github.com/Vaydelux/Bousygram/actions/runs/40841661942). Artifact ZIP SHA-256: `87bf06843de2ca4b851fb5d90815262f841884b9c9a376e73346b019ba35c0ad`.

## Workflow controls and outstanding qualification

- `android-enterprise.yml`: PR checks and verification; workflow and shell linting, Gradle wrapper checks, SDK resolution, compatibility gates, build/test/lint/assembly, and debug artifact checks are required. Firebase Test Lab reports `NOT CONFIGURED`, so physical device qualification is not claimed.
- `tdlib-java-generation.yml`: reproducible Linux host generation pinned to the exact TDLib commit; no Android NDK compiler is used for host generation.
- `android-release.yml`: manual-only protected release workflow with exact-source CI checks, protected signing, candidate APK hash binding, qualification evidence, and separately gated publication. It has not been run.

CI is green. Production release remains BLOCKED pending hardware qualification, signed production APKs, protected evidence tied to exact hashes, and publication approval. No GitHub Release was created or published; private-media Cast stays disabled in production.
