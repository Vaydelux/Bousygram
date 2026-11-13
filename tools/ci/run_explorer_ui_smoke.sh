#!/usr/bin/env bash
set -Eeuo pipefail

# UI-only smoke: do not depend on FFmpeg or synthetic playback fixtures.
# The complete playback suite remains in tools/ci/run_emulator_smoke.sh.
adb install -r ci-artifacts/debug/Bousygram-debug.apk
adb install -r ci-artifacts/debug/Bousygram-debug-androidTest.apk

# Both ADB and the instrumentation test runner must report success.
classes='com.vaydelux.bousygram.data.database.FavoritesInstrumentedTest,com.vaydelux.bousygram.presentation.components.MediaActionsDropdownInstrumentedTest,com.vaydelux.bousygram.presentation.media.TelegramExplorerCompactHeaderInstrumentedTest,com.vaydelux.bousygram.presentation.media.PlayerLoadingBannerInstrumentedTest,com.vaydelux.bousygram.presentation.media.TelegramLibraryDesignInstrumentedTest,com.vaydelux.bousygram.presentation.components.UniformPremiumDesignInstrumentedTest,com.vaydelux.bousygram.presentation.components.BousygramThemeGalleryInstrumentedTest,com.vaydelux.bousygram.presentation.design.StreamingDesignGalleryInstrumentedTest,com.vaydelux.bousygram.app.DashboardPreviewNavigationTest,com.vaydelux.bousygram.presentation.shell.ModeSelectionInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasSearchUiInstrumentedTest'
runner='com.vaydelux.bousygram.test/androidx.test.runner.AndroidJUnitRunner'

set +e
timeout 240s adb shell am instrument -w -r -e class "$classes" "$runner" | tee explorer-ui-smoke-results.txt
result=${PIPESTATUS[0]}
set -e

if [[ "$result" -ne 0 ]] || ! grep -Eq '^OK \([0-9]+ tests?\)' explorer-ui-smoke-results.txt; then
  echo "::error::Explorer UI instrumentation failed (exit $result)."
  exit 1
fi

# Screenshot files are produced by the Compose tests inside the debuggable
# target app. Pull each into CI artifacts; a missing image fails visual QA.
mkdir -p ci-artifacts/ui-evidence
for name in telegram-library themes-midnight mode-selection media-favorites \
  component-theme-dark component-theme-aurora component-theme-obsidian \
  component-theme-evergreen component-theme-ember component-theme-high-contrast component-theme-light \
  brand-neutral-design-dark brand-neutral-design-light brand-neutral-design-high-contrast; do
  adb exec-out run-as com.vaydelux.bousygram cat "files/ui-evidence/${name}.png" \
    > "ci-artifacts/ui-evidence/${name}.png"
  test -s "ci-artifacts/ui-evidence/${name}.png" || {
    echo "::error::Missing UI screenshot evidence for $name"
    exit 1
  }
done

echo 'Explorer UI, responsive viewport and D-pad navigation tests passed.'
