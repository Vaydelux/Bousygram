#!/usr/bin/env bash
# SDK-independent policy tests, not Compose rendering or device qualification.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
: "${KOTLIN_COROUTINES_JAR:?Set KOTLIN_COROUTINES_JAR to the local kotlinx-coroutines-core-jvm.jar}"
[[ -f "$KOTLIN_COROUTINES_JAR" ]] || { echo "Missing coroutine JVM jar" >&2; exit 1; }
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
src=app/src/main/java/com/vaydelux/bousygram
kotlinc "$src/core/memory/BoundedOrderedWindow.kt" \
  "$src/core/media/StreamingLockPolicy.kt" \
  "$src/core/tdlib/utils/PreviewBucketPolicy.kt" \
  "$src/data/repository/ChatDirectoryProgress.kt" \
  "$src/presentation/extras/ExtrasCachePolicy.kt" \
  "$src/presentation/extras/ExtrasFocusRestorePolicy.kt" \
  "$src/presentation/extras/ExtrasLayoutPolicy.kt" \
  "$src/presentation/extras/ExtrasDetailLayoutPolicy.kt" \
  "$src/ui/adaptive/AdaptiveUi.kt" \
  tools/ci/fixtures/CatalogLayoutSmoke.kt \
  -cp "$KOTLIN_COROUTINES_JAR" -include-runtime -d "$tmp/check.jar"
java -cp "$tmp/check.jar:$KOTLIN_COROUTINES_JAR" CatalogLayoutSmokeKt
