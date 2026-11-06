# P2.2 — Bousygram release candidate hardening and device gate

**State on November 6, 2026:** source-level accessibility and focus improvements
are committed to `main`. This is a development milestone; **not a
production-qualified release**. No real ARM32/ARM64 device qualification was
performed during this work.

## Protected release integrity

The existing controlled release workflow `.github/workflows/android-release.yml`
will only proceed when an exact 40-character source commit belongs to
`main`, its existing version tag resolves to that commit, the repository's
`main` branch is protected, and the commit has a completed successful
Android Enterprise **Build and verify** job.

It then verifies production signing credentials, builds the hardened APK
matrix, checks installed package/version/ABIs, signatures, alignment and
checksums, and generates a candidate manifest marked
`deviceQualification.status=pending`.

**Current blocker:** GitHub reported `main.protected=false` when P2.2 began.
The production workflow deliberately rejects that state. Do not remove the
protection requirement or invent a successful run. Enabling branch protection
may affect the requested **commit directly to main** development workflow:
configure suitable required checks/review protections and permitted automation
before production release, balancing main-only development with enforcement.

The protected release-qualification environment separately requires reviewer
approval, same-candidate APK hashes, real ARM32 TV / ARM64 TV / phone / tablet
evidence, TDLib runtime and every required scenario marked passed. The
negative fixtures specifically reject P2.1 `observations-only` PSS evidence
when supplied instead of manually reviewed device results.

## Accessibility and navigation checks in source

- TV focus restoration now returns **false** for missing or unattached focus
  nodes and uses a bounded 64-screen access-order history. Active Compose
  `FocusRequester` objects remain stable rather than being evicted
  mid-navigation.
- Advanced player options provide explicit spoken descriptions for audio /
  subtitles, speed, aspect ratio, episode queue, previous/next, 30-second
  seeking, decoder choice and closing options. Touch targets retain a
  minimum 48dp height.
- Cinematic navigation menu actions expose button semantics, rather than
  ambiguous text-only click targets.
- JVM and Android Compose instrumentation tests cover missing focus,
  bounded restoration state, touch semantics and TV menu behavior.

The above are source and instrumentation changes; focus contrast, spoken
announcements and ten-foot readability still require real Android TV and
phone accessibility checks.

## Physical release-candidate matrix

| Target | Reconnection | Playback and controls | Resource and accessibility |
| --- | --- | --- | --- |
| ARM32 low-RAM TV | Telegram cold/reconnect and account switching | MP4, indexed MKV, TS, libmpv/LibVLC, manual Pause, 50 engine swaps | 30–60-minute PSS trends, D-pad focus, visible captions |
| ARM64 TV | Network-loss recovery, force-stop/reopen | Seek, first frame, audio, subtitle/decoder switching | Surface recreation, high-contrast focus, real navigation |
| ARM64 phone | Account recovery after backgrounding | Touch scrub/seek, play/pause, audio-only, rotation | TalkBack labels, at least 48dp targets, lifecycle |
| ARM64 tablet | Multi-account isolation | Touch and keyboard/D-pad input, player controls | Landscape layout, zoom/font scaling and navigation |

Repeat each test with a **signed candidate** when qualifying production, not
a debug APK or an emulator build. Record outcome, installed version, exact
APK checksum, source SHA, actual decoder, frame/audio evidence and operator
approval separately. Keep private Telegram data and raw logs off public CI.

## Release stop conditions

Do **not** publish a production release with any of these unresolved:
- Failed or incomplete source/ABI tests; unavailable protected signing;
  unresolved P0/P1 issues; missing branch protection.
- Stale-account byte/data leakage, manual Pause overridden by buffering,
  broken MKV/TS seek, decoder crashes, surface failures, recurring stalls or
  ARM32 memory runaway on real hardware.
- No audible/visible media or inaccessible D-pad/touch playback controls on a
  required device class.
- PSS observation-only reports treated as manual rendered-frame, auth or
  production qualification.

Review
[device qualification](PLAYBACK_DEVICE_QUALIFICATION.md),
[controlled memory comparisons](../qualification/REPEATED_DEVICE_EVIDENCE_COMPARISON.md),
and [the release qualification record](../ci/RELEASE_DEVICE_QUALIFICATION_RECORD.md)
before starting the protected release workflow.
