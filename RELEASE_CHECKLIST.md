# Release checklist

Current evidence: [2026-11-12 candidate implementation](docs/release/RELEASE_CANDIDATE_2026-11-12.md).
Historical reports do not qualify the current working tree or a new APK.

- [x] Reference-checked cleanup; required build/native/CI/license inputs retained.
- [x] Account-scoped favorites, Room migration and dropdown actions implemented.
- [x] Bundled fonts, shared shapes, responsive policies and focus links implemented.
- [x] App JVM suite: 877 passing tests.
- [x] Host favorites storage suite: 3 passing tests, including schema agreement.
- [x] TDLib ARM32/ARM64 coverage and TV hardening checks pass locally.
- [ ] Final compilation and lint pass for the final source tree.
- [ ] Fresh-checkout build and required exact-revision CI checks pass.
- [ ] Android instrumentation executes and screenshot artifacts are inspected.
- [ ] Phone/tablet orientations, Android window resizing and 200% text are reviewed.
- [ ] Real TV D-pad, Back, focus visibility and playback controls are qualified.
- [ ] ARM32/ARM64/universal candidate APKs have source/signature/SHA-256 records.
- [ ] Physical playback, seeking, subtitles, pause/recovery and storage/PSS pass.
- [ ] Production signing and stable publication gates are satisfied independently.

No newly qualified release APK or stable publication is claimed by this checklist.
