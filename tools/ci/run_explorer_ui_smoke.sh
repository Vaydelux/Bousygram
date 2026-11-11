#!/usr/bin/env bash
set -Eeuo pipefail

# UI-only smoke: do not depend on FFmpeg or synthetic playback fixtures.
# The complete playback suite remains in tools/ci/run_emulator_smoke.sh.
adb install -r ci-artifacts/debug/Bousygram-debug.apk
adb install -r ci-artifacts/debug/Bousygram-debug-androidTest.apk

# Both ADB and the instrumentation test runner must report success.
classes='com.vaydelux.bousygram.presentation.media.TelegramExplorerCompactHeaderInstrumentedTest,com.vaydelux.bousygram.presentation.media.TelegramLibraryDesignInstrumentedTest,com.vaydelux.bousygram.presentation.components.UniformPremiumDesignInstrumentedTest,com.vaydelux.bousygram.presentation.components.BousygramThemeGalleryInstrumentedTest,com.vaydelux.bousygram.app.DashboardPreviewNavigationTest'
runner='com.vaydelux.bousygram.test/androidx.test.runner.AndroidJUnitRunner'

set +e
timeout 240s adb shell am instrument -w -r -e class "$classes" "$runner" | tee explorer-ui-smoke-results.txt
result=${PIPESTATUS[0]}
set -e

if [[ "$result" -ne 0 ]] || ! grep -Eq '^OK \([0-9]+ tests?\)' explorer-ui-smoke-results.txt; then
  echo "::error::Explorer UI instrumentation failed (exit $result)."
  exit 1
fi

echo 'Explorer UI, responsive viewport and D-pad navigation tests passed.'
