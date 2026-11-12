# Release candidate implementation — 2026-11-12

Status: **source changes implemented; candidate qualification incomplete**.
Base commit: `6c024a5f3bb91ca808abb4dfa545c36769624539` plus local working-tree
changes. No new APK or production publication is implied by this record.

## Product changes

- Shared media dropdown offers explicit Add/Remove favorite operations. Existing
  navigation and the Favorites screen remain. Playback/actions have separate click
  targets, visible focus and explicit D-pad focus links.
- Account-specific Room schema 3 migrates legacy preference keys once. Indexed
  video metadata seeds migrated records when available. Serialized mutations
  preserve invocation order; late metadata cannot reinsert removed records or
  write after account invalidation. Legacy preferences remain recovery input.
- Favorites render cached metadata first, load 60 records at a time, retain at
  most 240 active records, and offer previous-window navigation. Refresh uses four
  concurrent lookups. Failed lookups remain removable. Scroll/focus survive return
  from playback; removal restores neighboring focus.
- Available width, height and text scale guide toolbar overflow, grid density and
  Settings navigation. Theme previews measure their actual container. Touch/TV
  controls preserve 48/56 dp minimums; short side navigation can scroll.
- Offline Space Grotesk headings and Inter text; 8 dp cards/controls and 12 dp
  panels; existing seven theme IDs and mascot preserved. Shared focus uses 140 ms,
  screen arrival and HUD fades use 220 ms. System animation disabling is respected.

## Verified cleanup

Reference searches found no remaining consumers for the removed `.aistudio`
planning document/metadata, `ai/llm.md`, `ai/llm.txt`, root AI Studio
`metadata.json`, or `tools/tdlib/_perl_test.pl`. Empty agent artifact directories
were removed. Build/native/test/CI/release/license inputs and maintained product
documentation remain. Agent artifacts and local verification tools are ignored.

## Evidence and pending gates

- App JVM suite: **877 tests passed** after fixing scheduler fixtures to delete
  large temporary videos after each case rather than retaining them until JVM
  shutdown. Assertions and production streaming behavior were unchanged.
- `python tools/ci/test_favorites_storage.py`: **3 tests passed**. Shipped migration
  and DAO SQL match exported Room schema; 0/1/100/1,000/10,000-record pages,
  unavailable entries, repeated removal and late metadata are covered.
- TDLib ABI coverage and TV hardening checks passed locally.
- Final Kotlin/Java, instrumentation-source compilation and lint: pending final run.
- Instrumentation **execution** and screenshot inspection: pending. No connected
  device/emulator. Existing smoke scripts include favorites storage/actions tests
  and require seven component-theme captures plus a favorites capture. Component
  captures do not certify full-screen layouts.
- ARM32/ARM64/universal APK packaging, signatures/checksums and fresh-checkout
  verification: pending. C: has about 200 MB available; earlier verification
  exhausted storage. Portable tools stay under ignored `.release-tools/`.
- Physical ARM32/ARM64 TV, phone/tablet, both orientations, Android desktop windows,
  200% text, playback/seek/subtitles and long-session storage/PSS: pending.
- Production signing and stable publication remain independently gated.

Use the existing CI workflows for compilation, lint, JVM tests, `assembleDebug`,
`assembleDebugAndroidTest`, responsive UI and full playback smoke. Free at least
3 GB for local APK work; emulator installation requires additional space.
Local resource constraints do not waive a qualification gate.

## Migration and distribution

Do not downgrade a migrated database to an APK expecting schema 2. Roll forward
with a fix, or revert presentation changes while retaining schema 3. No destructive
Room fallback was added. Retained legacy preferences are not a second writable
favorites store.

Qualify an exact APK/source revision and record ABI, SHA-256 and signing identity.
Debug-signed artifacts must remain labelled debug.

## Font provenance

Unmodified Google Fonts variable TTFs; complete SIL OFL 1.1 texts ship in assets.
See [third-party notices](../THIRD_PARTY_NOTICES.md).

- `inter.ttf` SHA-256: `29160a80ff49ddcab2c97711247e08b1fab27a484a329ce8b813d820dc559031`
- `space_grotesk.ttf` SHA-256: `acad6de1fc93436f5c0f1f4137751ef04f1aea3063e7036535970ffcfbd79f72`
