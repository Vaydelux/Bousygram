#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/gradle/wrapper"
cp "$ROOT/gradle/wrapper/gradle-wrapper.properties" "$TMP/gradle/wrapper/"
cp "$ROOT/gradle/wrapper/gradle-wrapper.jar" "$TMP/gradle/wrapper/"
cp "$ROOT/gradlew" "$TMP/gradlew"
chmod +x "$TMP/gradlew"

expect_failure() {
  if bash "$ROOT/tools/ci/verify_gradle_wrapper.sh" "$TMP" --skip-runtime >/dev/null 2>&1; then
    echo "fixture unexpectedly passed: $1" >&2
    exit 1
  fi
}

sed -i 's#^distributionUrl=.*#distributionUrl=https\\://evil.example/gradle.zip#' "$TMP/gradle/wrapper/gradle-wrapper.properties"
expect_failure wrong-url
cp "$ROOT/gradle/wrapper/gradle-wrapper.properties" "$TMP/gradle/wrapper/gradle-wrapper.properties"
sed -i 's/^distributionSha256Sum=.*/distributionSha256Sum=deadbeef/' "$TMP/gradle/wrapper/gradle-wrapper.properties"
expect_failure wrong-sha
echo 'Gradle wrapper negative fixtures passed'
