# Playback reliability milestone: natural EOF, launcher resume, and physical qualification

Baseline: `8f2401722c5669839bf2718c01deb207d6fc50c9`.
Status: source change and regression tests. **No actual ARM32/ARM64 hardware runs are claimed.**

## Confirmed source defect and correction

- `WatchNextManager.updateProgress` created Android TV launcher records using `bousy_account_{accountId}_{chatId}_{messageId}_{fileId}`.
- `PlayerScreen` natural-EOF cleanup attempted to delete `bousy_{chatId}_{messageId}_{fileId}` instead; the inserted record could remain after the video ended.
- Both creation and deletion now call the shared `watchNextProviderId` helper; the completion path pins the original verified account generation, preventing an old decoder callback from acting on a newly signed-in account.
- Regression tests verify exact provider IDs, account isolation, and invalid identities.

## Real-device acceptance protocol (NOT executed by CI)

On the **same exact commit/APK** used for automated checks, manually verify the following on *real* ARM32 TV and ARM64 TV; also verify background/resume on ARM64 phone/tablet.

| Test | Procedure | Expected observation |
|---|---|---|
| Completion cleanup | Play an authorized >10-second Telegram video until the natural ending, from an unfinished Watch Next entry | Android TV launcher removes that specific entry; unrelated account/video entries persist |
| Premature EOF | Simulate an incomplete/short range or malformed TS on each supported engine | No silent skip to another episode; retries are bounded and the UI exposes recovery |
| Manual pause | Pause during startup buffering, seek buffering, and Wi-Fi loss on Media3, libmpv, LibVLC | No autoplay until user requests Play again |
| Seek and resume | MKV, MP4, TS with/without a trusted seek index; seek back to zero, middle, near end | Position does not unexpectedly jump to old high-water checkpoint; unsupported TS seeks show a recoverable message |
| Engine switching | Switch Media3→MPV→LibVLC where supported; return to original, with captions | No dual players, dead surfaces, leaked subtitle windows or lost explicit pause |
| Account switch | Trigger logout while playback/Watch Next cleanup is pending | No new-account launcher history deletion or old-account progress shown |
| TV focus | Open player, seek controls, audio/subtitle dialog, Back, next episode; repeat at 720p/1080p/4K | D-pad focus remains visible and reachable |
| Memory stability | Repeat 20 playback/seek/switch cycles, then 30–60 minute viewing session | Compare captured PSS/heap and process continuity, not just one sample |

Do **not** claim success from the presence of a Watch Next tile alone: inspect its removal after genuine native EOF. Launcher providers can decline some operations; classify the result as unsupported/failed, not green.

## Existing safe capture and comparison tools

- `tools/qualification/Capture-BousygramPhysicalSoak.ps1` records sanitized process PSS/native heap on a real device; use `-Scenario arm32-playback` on ARM32 and `-Scenario native-engines` on each ABI.
- `tools/qualification/Compare-BousygramPhysicalSoak.ps1` compares complete matched runs on a single device.
- `tools/qualification/compare_physical_cohorts.py` compares at least three independent repeated baseline/candidate runs when available.
- `tools/ci/qualify_release_manifest.sh` blocks signed release without protected exact-artifact device evidence and approval.

None of these tools can verify actual video frames, decoder selection, Telegram authorization, D-pad navigation or third-party streaming entitlement on their own. Hardware qualification must remain **pending** until the user or a trusted device lab provides comparable, reviewed records.

## Remaining prioritized risks

1. **P1 device validation:** actual supported codecs, native surfaces, repeated seek/pause, and Android TV launcher provider behavior on real ARM32/ARM64 hardware.
2. **P1 playback fallback:** verify rebuffer/end/explicit user pause across three engines with fixture-based native diagnostics.
3. **P2 Compose state:** reactive watched/progress cards (currently remembered without the progress ledger revision).
4. **P2 maintainability:** extract pure state policy from large player and dashboard composables, keeping established UI and engine ownership behavior.
5. **P2 design:** compare real screenshots and TV remote focus at supported resolutions/themes/large text.

No external streaming features are introduced by this milestone.
