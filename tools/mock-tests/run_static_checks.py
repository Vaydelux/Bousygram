from pathlib import Path
import xml.etree.ElementTree as ET

for resource in (
    'app/src/main/AndroidManifest.xml',
    'app/src/main/res/values/strings.xml',
    'app/src/main/res/values/themes.xml',
):
    ET.parse(resource)

def read_text(path):
    return Path(path).read_text(encoding='utf-8')

build = read_text('app/build.gradle.kts')
assert 'Using debug signing for local release verification' not in build
assert 'Production signing is not configured' in build
assert 'nativeLibrary.isFile && nativeLibrary.length() > 0L' in build
assert 'if (!abis.contains("arm64-v8a"))' in build
assert build.index('val hasReleaseSigning') < build.index('android {')
assert 'signingConfig = signingConfigs.getByName("debug")' not in build.split('buildTypes {', 1)[1]

queue_builder = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/media/PlaybackHelpers.kt')
assert 'url = null' in queue_builder
assert 'LocalStreamingServer.getStreamUrl' not in queue_builder

player_source = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/media/PlayerScreen.kt')
assert 'FastLinkManager' not in player_source and 'activeFastLinkUrl' not in player_source
settings_source = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/settings/SettingsScreen.kt')
assert 'Fast-link bot username' not in settings_source and 'Hybrid Playback' not in settings_source
repository_source = read_text('app/src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesRepository.kt')
assert 'FastLinkManager' not in repository_source and 'StreamVaultProBot' not in repository_source

view_model = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/media/vm/MediaViewModel.kt')
assert 'Do not download the first 2 MiB of every video' in view_model and 'repository.fetchThumbnailPath(thumbnailFileId)' in view_model
assert 'LocalStreamingServer.warmUp' not in view_model
player = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/media/PlayerScreen.kt')
assert 'StreamAccessRegistry.revokeFromUrl(activeLocalStreamUrl)' in player
key_store = read_text('app/src/main/java/com/vaydelux/bousygram/data/store/TdlibDatabaseKeyStore.kt')
assert 'KEY_DATABASE_KEY_MODE' in key_store and 'editor.commit()' in key_store
assert 'TdlibDatabaseKeyPolicy.Decision.FAIL_CLOSED' in key_store
assert 'noBackupFilesDir' in key_store and 'readModeMarker()' in key_store
credentials_store = read_text('app/src/main/java/com/vaydelux/bousygram/data/store/ApiCredentialsStore.kt')
assert 'encrypted != null && prefs.edit().putString(key, encrypted).commit()' in credentials_store
assert 'prefs.edit().remove(key).commit()' in credentials_store
loopback = read_text('app/src/main/java/com/vaydelux/bousygram/core/tdlib/server/LocalStreamingServer.kt')
assert 'private var executor: ExecutorService = createExecutor()' in loopback
assert 'acceptConnections(listeningSocket, listenerExecutor)' in loopback
assert 'if (executor.isShutdown || executor.isTerminated)' in loopback
assert 'no valid stream URL can be issued' in loopback
assert 'no valid playlist URL can be issued' in loopback
assert 'no valid subtitle URL can be issued' in loopback

main_smoke = read_text('app/src/androidTest/java/com/vaydelux/bousygram/app/MainActivitySmokeTest.kt')
assert 'launchMainActivity(instrumentation, preview = false)' in main_smoke
assert 'launchMainActivity(instrumentation, preview = true)' in main_smoke
poster = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/components/EnterpriseComponents.kt')
assert ('if (privacyMode) null else media.thumbnailPath' in poster or 'if (privacyMode) null else preferredThumbnail' in poster)
assert 'if (!privacyMode)' in poster and 'Details hidden' in poster
home = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/layout/TvHybridHomeDashboard.kt')
assert 'thumbnailPath' in home and 'AsyncImage' in home
assert 'safeMode' in home and 'ContinueWatchingRepository' in home
search = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/search/TvVideoSearchScreen.kt')
assert 'privacyMode = privacyMode' in search and 'if (!privacyMode && recentQueries.isNotEmpty())' in search
settings = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/settings/SettingsScreen.kt')
assert 'Privacy mode' in settings
assert 'if (privacyMode) "Search media"' in search
assert 'No matching videos were found.' in search
browse = read_text('app/src/main/java/com/vaydelux/bousygram/presentation/media/BrowseScreen.kt')
assert 'No videos in this source' in browse
print('PASS: XML, fail-closed release signing, ARM64, lazy queue URLs, URL validation, debounced prefetch, capability revocation, database-key recovery, credential migration, restart-safe server, real-app smoke-test source, and privacy-mode masking')
