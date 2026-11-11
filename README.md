<div align="center">
  <img src="brand/bousygram-mascot.webp" alt="Bousygram — Chow Chow and Telegram paper-plane emblem" width="154" />
  <h1>Bousygram</h1>
  <p><strong>Your Telegram media. One cinematic, local-first player.</strong></p>
  <p>Designed for Android TV · Adapted for phones and tablets</p>
  <p>
    <a href="https://github.com/Vaydelux/Bousygram/actions/workflows/android-enterprise.yml"><img src="https://github.com/Vaydelux/Bousygram/actions/workflows/android-enterprise.yml/badge.svg" alt="Android Enterprise Checks" /></a>
    <a href="https://github.com/Vaydelux/Bousygram/releases"><img src="https://img.shields.io/github/v/release/Vaydelux/Bousygram?include_prereleases&label=latest%20build" alt="Latest build" /></a>
    <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-4268E8" alt="Kotlin and Jetpack Compose" />
  </p>
  <p>
    <a href="#quick-start">Quick start</a> ·
    <a href="#playback--tv-controls">Player controls</a> ·
    <a href="#how-it-works">Architecture</a> ·
    <a href="#developer-setup">Build</a> ·
    <a href="#release-readiness">Release checks</a>
  </p>
</div>

---

## A media player built for the living room

**Bousygram** lets you browse Telegram chats, channels, groups, topics, and Saved Messages and play media directly on your Android device. Videos are fetched via **TDLib** and served through a local, byte-range-capable stream to the selected playback engine. **VideoLAN LibVLC is the default on new installations**, while existing saved engine choices are preserved. **Media3 ExoPlayer** and **libmpv** remain available for manual selection and supported fallback paths. Compatibility with MKV, MPEG-TS, alternate tracks, and seeking depends on the actual file and device.

The app is **local-first**: ordinary playback does not require a Bousygram cloud backend or a server that receives your videos. It is not an official Telegram client and is not affiliated with Telegram.

### Designed around the way you watch

| Experience | Details |
|:--|:--|
| 📺 **Android TV** | D-pad navigation, clear focus, episode drawer, compact auto-hiding playback controls |
| 📱 **Phone & tablet** | Adaptive layout, touch-friendly controls, saved playback position |
| ▶️ **Video playback** | MP4, MKV, and MPEG-TS best-effort support; seek, retry, audio/subtitle tracks where supported |
| 📂 **Media discovery** | Saved Messages, chats, groups, channels, forum topics, local metadata/index caching |
| ⚡ **Local streaming** | TDLib partial downloads, byte-range reads, foreground stream priority, bounded prefetch |
| 🎨 **Appearance** | Midnight, Aurora, Obsidian, Evergreen, Ember, Daylight and High Contrast; shared visual tokens for Android TV, tablet and phone |
| 🔒 **Privacy** | Android Keystore-backed credential protection and account-scoped media/cache boundaries |

**Compatibility depends on the actual file codecs, device decoder support, and network conditions.** Buffering cannot be eliminated when a connection is slower than a video's sustained bitrate.

## Quick start

1. Open the [Releases](https://github.com/Vaydelux/Bousygram/releases) page and choose the appropriate APK: **armeabi-v7a** for older 32-bit TVs, **arm64-v8a** for most newer devices, or **universal** when uncertain.
2. Install the APK with your device's file manager or Android Debug Bridge:
   ```bash
   adb install -r Bousygram-<version>-<variant>.apk
   ```
3. Obtain your personal Telegram `api_id` and `api_hash` from [my.telegram.org/apps](https://my.telegram.org/apps), using **API development tools**.
4. Sign in with your Telegram account via the in-app supported authentication flow.
5. Browse a source, select a video, and begin playback.

> Debug prereleases are for testing. Production release signing, physical-device evidence, and approvals have their own gates. **Do not treat a green CI badge as certification of uninterrupted playback.**

## Playback & TV controls

| Control | Behavior |
|:--|:--|
| **OK / Select** | Show controls; activate the focused control |
| **Left / Right** | Seek preview in the hidden-HUD TV experience; use the seek bar when visible |
| **Play / Pause** | Pause explicitly or arm playback; buffering must preserve the user's intent |
| **Episodes** | Open **More from this source** without requiring D-pad Up |
| **Options** | Playback speed, display mode, tracks, decoder selection, and additional actions |
| **Back** | Close the current overlay or return to the previous screen |
| **Loading / Buffering** | The HUD automatically hides when appropriate; visible statuses should not imply a manual pause |

The episode tray is restricted to the current source/topic. Available tracks and controls vary by file and playback engine. The player HUD fades away during active viewing but stays available for errors, seeking, and menu interaction.

## How it works

```text
Telegram account / chats / Saved Messages
                 │
                 ▼
     TDLib (MTProto / libtdjni)
                 │
         file metadata & partial bytes
                 │
                 ▼
    Local range-capable stream gateway
                 │
                 ├──► Media3 ExoPlayer (manual / Auto)
                 ├──► libmpv (fallback or manual)
                 └──► LibVLC (fresh-install default / manual)
                 │
                 ▼
      Android TV / phone / tablet
```

- **Progressive playback:** the player can start before a file is fully downloaded. Seeking requests missing ranges on demand.
- **Foreground priority:** speculative thumbnail or next-episode prefetch should not displace bytes needed by the currently playing video.
- **Audio/video intent:** rebuffering is distinct from a user-requested Pause; audio focus and Activity lifecycle rules still apply.
- **No automatic DASH transcoding:** Telegram files are not necessarily supplied as adaptive video renditions. Creating DASH/HLS segments from arbitrary originals on the device would add CPU, memory, and startup cost; see the actual optimization decisions and device results before changing the streaming architecture.

## Developer setup

**Requirements:** Android SDK, a compatible JDK/Gradle environment, and the native TDLib binaries expected by the repository. The Gradle wrapper is the build entry point.

The project uses the **Secrets Gradle plugin** with a repository-root `.env` file
(copy `.env.example`) for API credentials. `.env` is Git-ignored.
`local.properties` should contain only your local Android SDK path.
Do not commit live API keys or keystores.

```properties
# .env (in the repository root; never commit)
TMDB_API_KEY=YOUR_TMDB_V3_API_KEY
```

```properties
# local.properties (in the repository root; never commit)
sdk.dir=C:\\path\\to\\Android\\Sdk
```

Rebuild the debug APK after changing `TMDB_API_KEY`; this is read as
`BuildConfig.TMDB_API_KEY` for the Extras catalog and Telegram title metadata.
TMDB provides metadata, **not** video streams. API keys included in client APKs
can be extracted; use a backend proxy for credentials that must remain private.

**GitHub Actions debug APKs with TMDB:** Go to this repository's
**Settings → Secrets and variables → Actions → New repository secret**.
Set the secret name to `TMDB_API_KEY` and paste the **TMDB API Key (v3)**,
not the API Read Access Token (v4). Never add real credentials to
`.env.example` or commit them. For a debug APK with this key, manually run
**Actions → Android Enterprise Checks → Run workflow** and select the branch
you want to build. The workflow writes an ignored `.env` file inside the
trusted runner before Gradle builds the APK. Trusted main-branch pushes also
use the secret.

The workflows **never** pass the secret to `pull_request` builds;
those intentionally contain the placeholder. If you download an APK from
an automatic PR validation run, TMDB Movies/TV will still require a
credentialed rebuild. If the repository secret is missing, the manual
workflow also uses the placeholder and emits a notice.


```powershell
.\\gradlew.bat :app:verifyTdlibAbiCoverage :app:verifyTvHardeningContract
.\\gradlew.bat :app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest
.\\gradlew.bat :app:assembleDebug
```

On macOS/Linux use `./gradlew` with the same task names.

**Choose a player:** New installs select **LibVLC** by default; saved selections remain unchanged. Settings → Playback engine allows ExoPlayer, libmpv, LibVLC or Auto. The media player's engine control can cycle between the manual engines. **Auto** remains ExoPlayer/libmpv and does not silently enable LibVLC on low-memory TVs. Native MPEG-TS seeking still requires a usable index and verified byte-range behavior. See [LibVLC integration](docs/experiments/LIBVLC_DEBUG_COMPATIBILITY.md) and [third-party notices](docs/THIRD_PARTY_NOTICES.md).

**Production signing:** use the protected GitHub environments and the [Release Signing Setup](RELEASE_SIGNING_SETUP.md). `assembleHardenedRelease` fails closed without production signing and certificate verification. Do not use debug APKs as proof of production signing.

## Release readiness

The [Android Enterprise Checks](https://github.com/Vaydelux/Bousygram/actions/workflows/android-enterprise.yml) workflow validates code, TDLib compatibility gates, lint, tests, APK packaging, and supported ABI inventory. Real-world device qualification remains distinct from automated checks.

Before calling a build production-ready, verify playback on the exact signed candidate across an older ARM32 TV, modern ARM64 TV, phone, and tablet. Test cold-start, MP4/MKV/TS seeks, 30–60 minute viewing, network interruptions, restart/reopen, manual Pause, focus navigation, and account logout.

- [Release checklist](docs/release/PLAYBACK_DEVICE_QUALIFICATION.md)
- [Protected device evidence](docs/ci/RELEASE_DEVICE_QUALIFICATION_RECORD.md)
- [Production configuration](docs/ci/RELEASE_CONFIGURATION.md)
- [Project issues](https://github.com/Vaydelux/Bousygram/issues)

## Branding

The approved Bousygram emblem is the **Chow Chow mascot with a cyan ring and Telegram-style paper plane**. The small icon deliberately omits the wordmark, which becomes unreadable at favicon sizes.

- [Branding and icon rules](brand/README.md)
- [Icon preview](brand/bousygram-mascot.webp)
- [Favicon](brand/favicon.ico)

The phone, tablet and Android TV top bars now use the **same canonical Chow Chow mascot component**, rather than a generic play badge. The Android launcher and README share the same source artwork; browser favicons are downsampled and Android launchers may crop the adaptive icon to their chosen mask.

Themes are selectable in **Settings → Appearance** with live swatches for Midnight, Aurora, Obsidian, Evergreen, Ember, Daylight and High Contrast. Each theme shares the same typography, focus system and responsive layout rules; only semantic palette roles vary. See [UI visual qualification](docs/ci/UI_VISUAL_QUALIFICATION.md) for the screenshot and real-device acceptance gates.

## Privacy & security

TDLib connects to Telegram locally and maintains account data on the device. The app uses protected local stream endpoints with short-lived capability tokens rather than exposing arbitrary filesystem paths. Telegram credentials are stored with Android Keystore-backed encryption; older TDLib database migrations require explicit testing to prevent account lockouts. Local caches may retain partial media to make resuming quicker; logout and account-scoped cleanup prevent cross-account reuse.

Never attach personal Telegram logs, session credentials, playback tokens, or private media files to public GitHub issues.

---

<sub>Made by **Vaydelux** · Original Bousygram source is [MIT-licensed](LICENSE); bundled native dependencies have independent licenses — see [third-party notices](docs/THIRD_PARTY_NOTICES.md). · Android TV first, Android everywhere.</sub>
