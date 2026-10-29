#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SKIP_RUNTIME=0
[[ "${2:-}" == "--skip-runtime" ]] && SKIP_RUNTIME=1

PROPERTIES="$ROOT/gradle/wrapper/gradle-wrapper.properties"
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
WRAPPER="$ROOT/gradlew"
EXPECTED_URL='https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip'
EXPECTED_SHA='2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb'
EXPECTED_JAR_SHA='76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3'

[[ -f "$PROPERTIES" && -s "$JAR" && -f "$WRAPPER" ]] || { echo 'Gradle wrapper files are incomplete' >&2; exit 1; }
if git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  [[ "$(git -C "$ROOT" ls-files --stage -- gradlew | awk 'NR==1 {print $1}')" == 100755 ]] || { echo 'gradlew must be tracked as mode 100755' >&2; exit 1; }
fi
[[ -x "$WRAPPER" ]] || { echo 'gradlew is not executable' >&2; exit 1; }
url="$(sed -n 's/^distributionUrl=//p' "$PROPERTIES")"
sha="$(sed -n 's/^distributionSha256Sum=//p' "$PROPERTIES")"
[[ "$url" == "$EXPECTED_URL" ]] || { echo "Unexpected distributionUrl: $url" >&2; exit 1; }
[[ "$sha" == "$EXPECTED_SHA" ]] || { echo "Unexpected distributionSha256Sum: $sha" >&2; exit 1; }
unzip -tq "$JAR" >/dev/null
actual_jar_sha="$(sha256sum "$JAR" | awk '{print tolower($1)}')"
[[ "$actual_jar_sha" == "$EXPECTED_JAR_SHA" ]] || { echo "Gradle wrapper JAR hash mismatch: $actual_jar_sha" >&2; exit 1; }
if (( SKIP_RUNTIME == 0 )); then
  version="$("$WRAPPER" --version 2>&1)"
  grep -Fq 'Gradle 9.4.1' <<<"$version" || { echo 'Gradle wrapper did not execute the pinned distribution' >&2; exit 1; }
fi
printf 'Gradle wrapper verified: URL=%s distributionSha256=%s wrapperJarSha256=%s\n' "$url" "$sha" "$actual_jar_sha"
