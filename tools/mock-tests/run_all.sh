#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

printf '\n== Fast-Link Python mock suite ==\n'
python3 server/fastlink-proxy/run_mock_tests.py

printf '\n== Python syntax check ==\n'
python3 -m compileall -q server/fastlink-proxy tools/mock-tests
printf 'PASS: Fast-Link and test-runner Python sources compile\n'

printf '\n== Source picker revision tests ==\n'
python3 tools/mock-tests/test_chat_source_picker_revision.py
python3 tools/mock-tests/test_media_loading_performance.py
python3 tools/mock-tests/test_theme_redesign.py

printf '\n== Kotlin streaming helper suite ==\n'
if [[ "${SKIP_KOTLIN_MOCK:-0}" != "1" ]] && command -v kotlinc >/dev/null 2>&1; then
  OUT="${TMPDIR:-/tmp}/bousygram-core-mock-tests.jar"
  kotlinc \
    app/src/main/java/com/vaydelux/bousygram/core/tdlib/server/HttpRangeParser.kt \
    app/src/main/java/com/vaydelux/bousygram/core/tdlib/server/StreamAccessRegistry.kt \
    app/src/main/java/com/vaydelux/bousygram/core/tdlib/auth/TdlibDatabaseKeyPolicy.kt \
    app/src/main/java/com/vaydelux/bousygram/data/repository/MediaSearchPlan.kt \
    tools/mock-tests/BousygramCoreMockTest.kt \
    -include-runtime -d "$OUT"
  java -jar "$OUT"
else
  printf 'SKIP: Kotlin CLI checks disabled or kotlinc unavailable; run the Kotlin suite and Android Gradle tests on an Android development machine.\n'
fi

printf '\n== Static project sanity checks ==\n'
python3 - <<'PY'
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

for resource in (
    'app/src/main/AndroidManifest.xml',
    'app/src/main/res/values/strings.xml',
    'app/src/main/res/values/themes.xml',
):
    ET.parse(resource)

build = Path('app/build.gradle.kts').read_text()
assert 'Using debug signing for local release verification' not in build
assert 'Production signing is not configured' in build
assert 'nativeLibrary.isFile && nativeLibrary.length() > 0L' in build
assert 'if (!abis.contains("arm64-v8a"))' in build
assert build.index('val hasReleaseSigning') < build.index('android {')
assert 'signingConfig = signingConfigs.getByName("debug")' not in build.split('buildTypes {', 1)[1]

queue_builder = Path('app/src/main/java/com/vaydelux/bousygram/presentation/media/PlaybackHelpers.kt').read_text()
assert 'url = null' in queue_builder
assert 'LocalStreamingServer.getStreamUrl' not in queue_builder

player_source = Path('app/src/main/java/com/vaydelux/bousygram/presentation/media/PlayerScreen.kt').read_text()
assert 'FastLinkManager' not in player_source and 'activeFastLinkUrl' not in player_source
repository_source = Path('app/src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesRepository.kt').read_text()
assert 'FastLinkManager' not in repository_source and 'StreamVaultProBot' not in repository_source

view_model = Path('app/src/main/java/com/vaydelux/bousygram/presentation/media/vm/MediaViewModel.kt').read_text()
assert 'loadLatestMediaPagedProgressive' in view_model and 'loadChatMediaPagedProgressive' in view_model
assert 'Do not download the first 2 MiB of every video' in view_model
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
media_search = Path('app/src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesMediaSearch.kt').read_text()
assert 'searchMediaProgressiveInternal' in media_search
assert 'TdApi.SearchMessagesFilterPhotoAndVideo()' not in media_search
assert 'TdApi.SearchMessagesFilterVideo()' in media_search
assert 'TdApi.SearchMessagesFilterDocument()' in media_search
assert 'isFinal' in media_search
plan = Path('app/src/main/java/com/vaydelux/bousygram/data/repository/MediaSearchPlan.kt').read_text()
assert 'createMediaSearchPlan' in plan and 'coerceIn(1, 100)' in plan
thumbnail_utils = Path('app/src/main/java/com/vaydelux/bousygram/data/repository/SavedMessagesUtils.kt').read_text()
assert 'MAX_CONCURRENT_THUMBNAIL_DOWNLOADS = 3' in thumbnail_utils
assert 'THUMBNAIL_DOWNLOAD_PRIORITY = 1' in thumbnail_utils
assert 'TdApi.DownloadFile(fileId, THUMBNAIL_DOWNLOAD_PRIORITY, 0, 0, false)' in thumbnail_utils
assert 'completeThumbnailDownload' in thumbnail_utils
print('PASS: XML, fail-closed release signing, ARM64, lazy queue URLs, URL validation, metadata-first progressive media search, reduced TDLib query budget, bounded low-priority thumbnails, focus-prefetch removal, capability revocation, database-key recovery, credential migration, restart-safe server, real-app smoke-test source, and privacy-mode masking')

try:
    import yaml
except ImportError:
    print('SKIP: PyYAML unavailable; workflow YAML/shell validation needs CI or PyYAML.')
else:
    workflow = yaml.safe_load(Path('.github/workflows/android-enterprise.yml').read_text())
    assert isinstance(workflow, dict) and isinstance(workflow.get('jobs'), dict)
    shell_count = 0
    for job in workflow['jobs'].values():
        for step in job.get('steps', []):
            script = step.get('run')
            if isinstance(script, str):
                subprocess.run(['bash', '-n'], input=script, text=True, check=True)
                shell_count += 1
    print(f'PASS: workflow YAML and {shell_count} embedded Bash scripts')
PY

printf '\nNOTE: This runner does not replace :app:lintDebug, :app:testDebugUnitTest, :app:assembleDebug, or device testing.\n'
