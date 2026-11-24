#!/usr/bin/env bash
# Optional SDK-free policy check. Never a substitute for Android qualification.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
command -v kotlinc >/dev/null
command -v java >/dev/null
: "${KOTLIN_COROUTINES_JAR:?Set KOTLIN_COROUTINES_JAR to the local kotlinx-coroutines-core-jvm.jar}"
[[ -f "$KOTLIN_COROUTINES_JAR" ]] || { echo "Missing coroutine JVM jar" >&2; exit 1; }
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
src=app/src/main/java/com/vaydelux/bousygram
kotlinc "$src/core/account/AccountSession.kt" \
  "$src/core/tdlib/server/TelegramPrefetchRangePolicy.kt" \
  tools/ci/fixtures/AccountPrefetchSmoke.kt \
  -cp "$KOTLIN_COROUTINES_JAR" -include-runtime -d "$tmp/check.jar"
java -cp "$tmp/check.jar:$KOTLIN_COROUTINES_JAR" AccountPrefetchSmokeKt
