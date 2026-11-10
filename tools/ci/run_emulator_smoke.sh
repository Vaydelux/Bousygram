#!/usr/bin/env bash
set -Eeuo pipefail

# The emulator runner invokes script entries one shell at a time. Keep the
# entire smoke test inside one Bash process so pipefail and exit traps work.
capture_diagnostics() {
  adb devices -l > emulator-adb-devices.txt 2>/dev/null || true
  {
    timeout 10s adb shell dumpsys activity activities 2>/dev/null | grep -E -m 25 'topResumedActivity|mResumedActivity|ActivityRecord|mFocusedApp' || true
    timeout 10s adb shell dumpsys window displays 2>/dev/null | grep -E -m 20 'mCurrentFocus|mFocusedApp|mInputMethodTarget' || true
    printf 'Bousygram PID: '
    timeout 10s adb shell pidof com.vaydelux.bousygram 2>/dev/null || true
  } > emulator-smoke-window-state.txt
  timeout 15s adb logcat -d -t 6000 -v brief 2>/dev/null \
    | grep -E 'FATAL EXCEPTION|AndroidRuntime|Force finishing activity|Process .*bousygram|ANR in|MediaRouteButton|MediaRouterThemeHelper' \
    | head -n 150 > emulator-smoke-crash-summary.txt || true
  if ! timeout 15s adb exec-out screencap -p > emulator-smoke-screen.png 2>/dev/null; then
    rm -f emulator-smoke-screen.png
  fi
}

fail() {
  echo "::error::$1"
  capture_diagnostics
  exit 1
}

adb install -r ci-artifacts/debug/Bousygram-debug.apk
adb install -r ci-artifacts/debug/Bousygram-debug-androidTest.apk

# Push only synthetic, checksum-verified media into the target app's own
# external-files sandbox. No real Telegram account, token, or private video.
fixture_dir='ci-artifacts/decoder-fixtures'
test -s "$fixture_dir/SHA256SUMS.txt" || fail 'Decoder fixtures were not generated'
( cd "$fixture_dir" && sha256sum --check SHA256SUMS.txt ) || fail 'Decoder fixture checksum mismatch'
remote_fixture_dir='/sdcard/Android/data/com.vaydelux.bousygram/files/decoder-fixtures'
adb shell mkdir -p "$remote_fixture_dir" || fail 'Could not create decoder fixture sandbox'
for name in seek-control.mp4 indexed.mkv unindexed.ts; do
  adb push "$fixture_dir/$name" "$remote_fixture_dir/$name" >/dev/null \
    || fail "Could not push $name to the emulator"
done


# ADB may briefly go offline after installation even when the runner reported
# boot completion. Retry transport readiness, never test assertion failures.
device_ready=false
for attempt in {1..12}; do
  if timeout 15s adb wait-for-device >/dev/null 2>&1 && \
    [[ "$(timeout 10s adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == '1' ]] && \
    timeout 10s adb shell pm path com.vaydelux.bousygram 2>/dev/null | grep -q 'package:'; then
    device_ready=true
    break
  fi
  echo "Emulator ADB not ready (attempt $attempt of 12)"
  sleep 3
done
if [[ "$device_ready" != true ]]; then
  fail 'Emulator ADB did not recover after installation; instrumentation not attempted.'
fi

# ADB's boot-completed flag does not prove the launcher and app can draw.
# Validate one real cold launch before instrumentation creates its own test Activity.
# This is a fail-closed startup gate, not an assertion retry or bypass.
echo 'Preflight: starting the real unauthenticated app'
if ! timeout 45s adb shell am start -W -n com.vaydelux.bousygram/.app.MainActivity > emulator-cold-launch.txt 2>&1; then
  fail 'Cold-launch command failed before instrumentation.'
fi
if ! grep -Eq 'Status: ok|Warning: Activity not started' emulator-cold-launch.txt; then
  fail 'Cold-launch command did not return Android activity startup success.'
fi
app_foreground=false
for attempt in {1..12}; do
  resumed="$(timeout 10s adb shell dumpsys activity activities 2>/dev/null | grep -m 1 'topResumedActivity=' || true)"
  app_pid="$(timeout 10s adb shell pidof com.vaydelux.bousygram 2>/dev/null | tr -d '\r' || true)"
  if [[ "$resumed" == *'com.vaydelux.bousygram/.app.MainActivity'* && -n "$app_pid" ]]; then
    app_foreground=true
    break
  fi
  echo "Cold launch not foreground (attempt $attempt of 12)"
  sleep 2
done
if [[ "$app_foreground" != true ]]; then
  fail 'Bousygram failed cold-launch foreground/PID preflight; see screenshot, logcat and window state.'
fi
# Give the emulator a stable window transition before the instrumentation runner.
sleep 2

test_classes='com.vaydelux.bousygram.core.cast.CastRouteButtonThemeInstrumentedTest,com.vaydelux.bousygram.core.cast.CastReceiverIntentInstrumentedTest,com.vaydelux.bousygram.app.MainActivitySmokeTest,com.vaydelux.bousygram.app.AndroidTvNavigationSmokeTest,com.vaydelux.bousygram.app.DashboardPreviewNavigationTest,com.vaydelux.bousygram.presentation.media.PlayerChromeInstrumentedTest,com.vaydelux.bousygram.presentation.media.CinematicPlayerChromeInstrumentedTest,com.vaydelux.bousygram.presentation.media.CinematicScrubberInstrumentedTest,com.vaydelux.bousygram.presentation.media.ChatSourcePickerInstrumentedTest,com.vaydelux.bousygram.presentation.media.SubtitleAppearanceInstrumentedTest,com.vaydelux.bousygram.core.media.ExperimentalStreamUrlPolicyInstrumentedTest,com.vaydelux.bousygram.core.media.PlaybackDecoderFixtureInstrumentedTest,com.vaydelux.bousygram.core.media.AudioOutputLifecycleInstrumentedTest,com.vaydelux.bousygram.presentation.components.TvFocusIndicatorInstrumentedTest,com.vaydelux.bousygram.data.database.RoomMigrationInstrumentedTest,com.vaydelux.bousygram.core.account.AccountPrivateDataPurgerInstrumentedTest,com.vaydelux.bousygram.presentation.shell.ModeSelectionInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasDpadFocusInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasSearchUiInstrumentedTest,com.vaydelux.bousygram.presentation.extras.MediaForgeWebBridgeInstrumentedTest,com.vaydelux.bousygram.presentation.extras.MediaForgeVirtualMouseInstrumentedTest,com.vaydelux.bousygram.presentation.extras.MediaForgeSubtitlePickerInstrumentedTest,com.vaydelux.bousygram.presentation.extras.ExtrasEmbedServerDropdownInstrumentedTest'
runner='com.vaydelux.bousygram.test/androidx.test.runner.AndroidJUnitRunner'

# Preserve the actual instrumentation exit status through tee. The test run
# remains a failure unless all selected tests complete successfully.
set +e
timeout 360s adb shell am instrument -w -r -e class "$test_classes" "$runner" | tee emulator-smoke-results.txt
instrumentation_exit=${PIPESTATUS[0]}
set -e
if [[ "$instrumentation_exit" -ne 0 ]] || ! grep -Eq '^OK \([0-9]+ tests?\)' emulator-smoke-results.txt; then
  fail "Instrumentation failed (exit $instrumentation_exit); saving emulator state before teardown."
fi

echo 'Android emulator instrumentation passed.'
