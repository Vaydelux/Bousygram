#!/usr/bin/env bash
# Local SDK-independent qualification only. Never creates or publishes APKs.
# Requires a clean checkout, Bash, Git, Python, JDK, Kotlin and a coroutine jar.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
source tools/ci/local_qualification_ref.sh
kind="$(local_qualification_kind "$@")" || exit 1
sha="$(git rev-parse HEAD)"
echo "SOURCE-ONLY qualification: $sha ($kind)"
bash tools/ci/test_local_qualification_ref.sh
bash tools/ci/test_release_apk_verifier.sh
python3 -B tools/ci/test_apk_native_inventory.py
bash tools/ci/test_account_prefetch_smoke.sh
bash tools/ci/test_catalog_layout_smoke.sh
[[ "$(git rev-parse HEAD)" == "$sha" ]] || { echo "Source changed during qualification" >&2; exit 1; }
local_qualification_kind "$@" >/dev/null
echo "PASS: SDK-independent policy suites for $sha"
echo "NOT QUALIFIED: Android compilation, signing, rendering, native playback, device and performance tests."
