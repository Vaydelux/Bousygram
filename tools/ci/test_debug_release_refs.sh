#!/usr/bin/env bash
# Deterministic authenticated release-ref policy fixtures: no network is contacted.
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"
cat >"$tmp/bin/gh" <<'MOCK_GH'
#!/usr/bin/env bash
set -euo pipefail
[[ "${1:-}" == api ]] || exit 50
shift
case "${1:-}" in
  repos/test/repo/compare/*)
    [[ -n "${MOCK_COMPARE:-}" ]] || exit 51
    printf '%s\n' "$MOCK_COMPARE"
    ;;
  repos/test/repo/git/ref/tags/*)
    [[ -n "${MOCK_TAG_JSON:-}" ]] || exit 1
    printf '%s\n' "$MOCK_TAG_JSON"
    ;;
  repos/test/repo/git/tags/*)
    [[ -n "${MOCK_TAG_TARGET:-}" ]] || exit 52
    printf '%s\n' "$MOCK_TAG_TARGET"
    ;;
  --method)
    [[ "${2:-}" == POST && "${3:-}" == "repos/test/repo/git/refs" ]] || exit 53
    printf 'created\n' >>"$MOCK_CREATED"
    printf '{}\n'
    ;;
  *) echo "Unexpected GitHub API call" >&2; exit 54 ;;
esac
MOCK_GH
chmod +x "$tmp/bin/gh"
export PATH="$tmp/bin:$PATH"
export MOCK_CREATED="$tmp/created"
: >"$MOCK_CREATED"
sha='0123456789abcdef0123456789abcdef01234567'
other='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
tag='v1.0.0-debug.20261110T141526Z'
verify="$root/tools/ci/verify_debug_release_main.sh"
tagger="$root/tools/ci/ensure_debug_release_tag.sh"

accept() { "$@" >/dev/null; }
reject() {
  if "$@" >/dev/null 2>&1; then
    echo "Release-ref policy unexpectedly accepted a prohibited reference: $*" >&2
    exit 1
  fi
}
export MOCK_COMPARE="{\"base_commit\":{\"sha\":\"$sha\"},\"status\":\"identical\"}"
accept bash "$verify" "$sha" test/repo
export MOCK_COMPARE="{\"base_commit\":{\"sha\":\"$sha\"},\"status\":\"ahead\"}"
accept bash "$verify" "$sha" test/repo
export MOCK_COMPARE="{\"base_commit\":{\"sha\":\"$sha\"},\"status\":\"diverged\"}"
reject bash "$verify" "$sha" test/repo
export MOCK_COMPARE="{\"base_commit\":{\"sha\":\"$sha\"},\"status\":\"behind\"}"
reject bash "$verify" "$sha" test/repo
export MOCK_COMPARE="{\"base_commit\":{\"sha\":\"$other\"},\"status\":\"ahead\"}"
reject bash "$verify" "$sha" test/repo
unset MOCK_COMPARE
reject bash "$verify" "$sha" test/repo
reject bash "$verify" "$other;echo" test/repo

# No existing tag -> create once through authenticated API, not git fetch.
export MOCK_TAG_JSON=''
accept bash "$tagger" "$tag" "$sha" test/repo
[[ "$(wc -l <"$MOCK_CREATED")" -eq 1 ]]

# Existing lightweight tag must be identical, never overwritten.
export MOCK_TAG_JSON="{\"ref\":\"refs/tags/$tag\",\"object\":{\"type\":\"commit\",\"sha\":\"$sha\"}}"
accept bash "$tagger" "$tag" "$sha" test/repo
[[ "$(wc -l <"$MOCK_CREATED")" -eq 1 ]]
export MOCK_TAG_JSON="{\"ref\":\"refs/tags/$tag\",\"object\":{\"type\":\"commit\",\"sha\":\"$other\"}}"
reject bash "$tagger" "$tag" "$sha" test/repo
export MOCK_TAG_JSON="{\"ref\":\"refs/tags/wrong\",\"object\":{\"type\":\"commit\",\"sha\":\"$sha\"}}"
reject bash "$tagger" "$tag" "$sha" test/repo

# Existing annotated tag must resolve to a commit with the same exact SHA.
export MOCK_TAG_JSON="{\"ref\":\"refs/tags/$tag\",\"object\":{\"type\":\"tag\",\"sha\":\"$other\"}}"
export MOCK_TAG_TARGET="{\"object\":{\"type\":\"commit\",\"sha\":\"$sha\"}}"
accept bash "$tagger" "$tag" "$sha" test/repo
export MOCK_TAG_TARGET="{\"object\":{\"type\":\"commit\",\"sha\":\"$other\"}}"
reject bash "$tagger" "$tag" "$sha" test/repo
export MOCK_TAG_TARGET="{\"object\":{\"type\":\"tree\",\"sha\":\"$sha\"}}"
reject bash "$tagger" "$tag" "$sha" test/repo
reject bash "$tagger" "v1.0.0-debug.20261110T141526Z/../main" "$sha" test/repo
[[ "$(wc -l <"$MOCK_CREATED")" -eq 1 ]]
echo 'Authenticated debug release ref policy fixtures passed.'
