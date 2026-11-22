#!/usr/bin/env bash
set -Eeuo pipefail

# UI-only smoke: do not depend on FFmpeg or synthetic playback fixtures.
# The complete playback suite remains in tools/ci/run_emulator_smoke.sh.
adb install -r ci-artifacts/debug/Bousygram-debug.apk
adb install -r ci-artifacts/debug/Bousygram-debug-androidTest.apk

# Include the real Extras saved-library and responsive detail tests; merely
# compiling androidTest sources is not enough to qualify the new UI.
# Both ADB and the instrumentation test runner must report success.
classes='com.vaydelux.bousygram.data.database.FavoritesInstrumentedTest,com.vaydelux.bousygram.presentation.components.MediaActionsDropdownInstrumentedTest,com.vaydelux.bousygram.presentation.media.TelegramExplorerCompactHeaderInstrumentedTest,com.vaydelux.bousygram.presentation.media.TelegramHomeOverviewInstrumentedTest,com.vaydelux.bousygram.presentation.media.PlaybackThemeParityInstrumentedTest,com.vaydelux.bousygram.presentation.media.PlayerLoadingBannerInstrumentedTest,com.vaydelux.bousygram.presentation.media.TelegramLibraryDesignInstrumentedTest,com.vaydelux.bousygram.presentation.media.TelegramForumPagingInstrumentedTest,com.vaydelux.bousygram.presentation.components.UniformPremiumDesignInstrumentedTest,com.vaydelux.bousygram.presentation.components.BousygramThemeGalleryInstrumentedTest,com.vaydelux.bousygram.presentation.components.BousygramStatusComponentsInstrumentedTest,com.vaydelux.bousygram.presentation.design.StreamingDesignGalleryInstrumentedTest,com.vaydelux.bousygram.app.DashboardPreviewNavigationTest,com.vaydelux.bousygram.presentation.shell.ModeSelectionInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasSearchUiInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasDetailNavigationInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasVisualConsistencyInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasPersonalLibraryUiInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasPersonalLibraryPersistenceInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasZokuNativeDetailInstrumentedTest'
runner='com.vaydelux.bousygram.test/androidx.test.runner.AndroidJUnitRunner'

set +e
timeout 330s adb shell am instrument -w -r -e class "$classes" "$runner" | tee explorer-ui-smoke-results.txt
result=${PIPESTATUS[0]}
set -e

# A truncated instrumentation selection can report "OK (1 test)" even
# when most requested classes never executed. Fail closed on count mismatch.
expected_classes=$(awk -F, '{print NF}' <<< "$classes")
passed_tests=$(sed -nE 's/^OK \(([0-9]+) tests?\).*$/\1/p' explorer-ui-smoke-results.txt | tail -n 1)
if [[ "$result" -ne 0 ]] || [[ -z "$passed_tests" ]] || (( passed_tests < expected_classes )) || grep -Eq '^FAILURES!!!|^INSTRUMENTATION_FAILED:' explorer-ui-smoke-results.txt; then
  echo "::error::Expected at least $expected_classes test cases; instrumentation reported ${passed_tests:-none}."
  echo "::error::Explorer UI instrumentation failed (exit $result)."
  exit 1
fi

# Screenshot files are produced by the Compose tests inside the debuggable
# target app. Pull each into CI artifacts; a missing image fails visual QA.
mkdir -p ci-artifacts/ui-evidence
for name in telegram-library telegram-home-overview themes-midnight mode-selection media-favorites \
  component-theme-dark component-theme-aurora component-theme-obsidian \
  component-theme-evergreen component-theme-ember component-theme-high-contrast component-theme-light \
  component-theme-ocean component-theme-plum component-theme-ivory \
  brand-neutral-design-dark brand-neutral-design-light brand-neutral-design-high-contrast \
  extras-catalog-dark extras-catalog-light extras-catalog-focus extras-detail-hero extras-webview-pointer; do
  adb exec-out run-as com.vaydelux.bousygram cat "files/ui-evidence/${name}.png" \
    > "ci-artifacts/ui-evidence/${name}.png"
  test -s "ci-artifacts/ui-evidence/${name}.png" || {
    echo "::error::Missing UI screenshot evidence for $name"
    exit 1
  }
done

echo 'Explorer UI, responsive viewport and D-pad navigation tests passed.'
