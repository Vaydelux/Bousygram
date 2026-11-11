# Lean GitHub Actions policy — November 2026

## Retained (six workflows)
1. `android-enterprise.yml`: PR/main Gradle, lint, unit and native integrity. No redundant second ABI build; reports are archived only on failure for one day.
2. `android-emulator-smoke.yml`: relevant main/PR Android UI and playback instrumentation. No success artifacts; best-effort diagnostics only on failure, one day.
3. `android-debug-prerelease.yml`: **manual, main-only**, independently verifies Kotlin, tests, emulator and ARM32/ARM64/universal debug APKs, publishing directly to GitHub Releases and SHA256SUMS without Actions artifacts.
4. `android-apk-release.yml`: protected production release tag selection and dispatcher.
5. `android-release.yml`: controlled signed release and physical device evidence gates. Protected production candidate artifacts are preserved.
6. `tdlib-java-generation.yml`: manual or TDLib-code-specific PR only. Export optional via `TDLIB_ARCHIVE_GENERATED_API=true` (1-day retention).

## Removed (three redundant workflows)
- `libvlc-experimental-debug.yml`: duplicate Gradle/ABI builds and native library APK uploads. Standard CI, emulator and debug release still verify native artifacts.
- `extras-live-provider-smoke.yml`: nonessential hosted API check; `tools/ci/test_extras_live_apis.sh` remains for manual operation.
- `archive-prune-branches-once.yml`: one-time branch housekeeping, no longer scheduled on every push.

## Storage policy
One-time cleanup retains running, current main/source PR, last successful manual debug qualification and three recent essential workflow runs; it deletes older completed runs and orphan Actions artifacts while preserving **all published GitHub Releases/APKs** and protected signed production candidates. GitHub storage accounting can lag after deletions.

Opt-in Firebase instrumentation may still require short-lived APK Actions artifacts. Debug APKs are testing builds and do not bypass production device qualification. Do not re-enable per-commit debug publishing or unconditional artifact uploads.
