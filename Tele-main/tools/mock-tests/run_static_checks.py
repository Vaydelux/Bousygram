from pathlib import Path
import xml.etree.ElementTree as ET

for resource in (
    'app/src/main/AndroidManifest.xml',
    'app/src/main/res/values/strings.xml',
    'app/src/main/res/values/themes.xml',
):
    ET.parse(resource)

build = Path('app/build.gradle.kts').read_text()
assert 'Using debug signing for local release verification' not in build
assert 'Refusing to build $name: production signing is not configured.' in build
assert 'nativeLibrary.isFile && nativeLibrary.length() > 0L' in build
assert 'if (!tdLibAvailableAbis.contains("arm64-v8a"))' in build
assert build.index('val hasReleaseSigning') < build.index('android {')
assert 'signingConfig = signingConfigs.getByName("debug")' not in build.split('buildTypes {', 1)[1].split('create("staging")', 1)[0]

queue_builder = Path('app/src/main/java/com/vaydelux/bousygram/presentation/media/PlaybackHelpers.kt').read_text()
assert 'url = null' in queue_builder
assert 'LocalStreamingServer.getStreamUrl' not in queue_builder

fast_link = Path('app/src/main/java/com/vaydelux/bousygram/core/tdlib/fastlink/FastLinkManager.kt').read_text()
assert 'FastLinkUrlValidator.validate(trimmedCandidate)' in fast_link
validator = Path('app/src/main/java/com/vaydelux/bousygram/core/tdlib/fastlink/FastLinkUrlValidator.kt').read_text()
assert 'https' in validator and 'rawUserInfo' in validator and 'rawFragment' in validator

view_model = Path('app/src/main/java/com/vaydelux/bousygram/presentation/media/vm/MediaViewModel.kt').read_text()
assert 'FOCUS_PREFETCH_DEBOUNCE_MS' in view_model and 'focusWarmupJob?.cancel()' in view_model
player = Path('app/src/main/java/com/vaydelux/bousygram/presentation/media/PlayerScreen.kt').read_text()
assert 'StreamAccessRegistry.revokeFromUrl(activeLocalStreamUrl)' in player
key_store = Path('app/src/main/java/com/vaydelux/bousygram/data/store/TdlibDatabaseKeyStore.kt').read_text()
assert 'KEY_DATABASE_KEY_MODE' in key_store and 'editor.commit()' in key_store
assert 'TdlibDatabaseKeyPolicy.Decision.FAIL_CLOSED' in key_store
assert 'noBackupFilesDir' in key_store and 'readModeMarker()' in key_store
credentials_store = Path('app/src/main/java/com/vaydelux/bousygram/data/store/ApiCredentialsStore.kt').read_text()
assert 'encrypted != null && prefs.edit().putString(key, encrypted).commit()' in credentials_store
assert 'prefs.edit().remove(key).commit()' in credentials_store
loopback = Path('app/src/main/java/com/vaydelux/bousygram/core/tdlib/server/LocalStreamingServer.kt').read_text()
assert 'private var executor: ExecutorService = createExecutor()' in loopback
assert 'acceptConnections(listeningSocket, listenerExecutor)' in loopback
assert 'if (executor.isShutdown || executor.isTerminated)' in loopback
assert 'no valid stream URL can be issued' in loopback
assert 'no valid playlist URL can be issued' in loopback
assert 'no valid subtitle URL can be issued' in loopback

main_smoke = Path('app/src/androidTest/java/com/vaydelux/bousygram/app/MainActivitySmokeTest.kt').read_text()
assert 'launchMainActivity(instrumentation, preview = false)' in main_smoke
assert 'launchMainActivity(instrumentation, preview = true)' in main_smoke
poster = Path('app/src/main/java/com/vaydelux/bousygram/presentation/components/EnterpriseComponents.kt').read_text()
assert 'if (privacyMode) null else media.thumbnailPath' in poster
assert 'if (!privacyMode)' in poster and 'Details hidden' in poster
home = Path('app/src/main/java/com/vaydelux/bousygram/presentation/layout/TvHybridHomeDashboard.kt').read_text()
assert 'if (safeMode) null else video.thumbnailPath' in home
assert 'PRIVATE MODE' in home and 'Recently viewed' in home
search = Path('app/src/main/java/com/vaydelux/bousygram/presentation/search/TvVideoSearchScreen.kt').read_text()
assert 'privacyMode = privacyMode' in search and 'if (!privacyMode && recentQueries.isNotEmpty())' in search
settings = Path('app/src/main/java/com/vaydelux/bousygram/presentation/settings/SettingsScreen.kt').read_text()
assert 'FeatureRow("Privacy mode"' in settings
assert 'if (privacyMode) "Search media"' in search
assert 'No matching videos were found.' in search
browse = Path('app/src/main/java/com/vaydelux/bousygram/presentation/media/BrowseScreen.kt').read_text()
assert 'if (safeMode) "No videos in this source.' in browse
print('PASS: XML, fail-closed release signing, ARM64, lazy queue URLs, URL validation, debounced prefetch, capability revocation, database-key recovery, credential migration, restart-safe server, real-app smoke-test source, and privacy-mode masking')
