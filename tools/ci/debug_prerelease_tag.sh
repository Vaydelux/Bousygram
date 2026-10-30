#!/usr/bin/env bash
# Return an immutable UTC date/time tag for a verified CI run.
# Use the CI run's created_at (not publisher wall time), so retries reuse the same tag.
set -euo pipefail
version="${1:-}"
created_at="${2:-}"

if [[ ! "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo 'Invalid debug release version' >&2
  exit 2
fi
if [[ ! "$created_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]; then
  echo 'Expected the verified CI created_at timestamp in UTC ISO-8601 format' >&2
  exit 2
fi
# GNU date rejects out-of-range calendar values; require exact round-trip.
normalized="$(date -u -d "$created_at" +'%Y-%m-%dT%H:%M:%SZ')" || {
  echo 'Invalid CI creation timestamp' >&2
  exit 2
}
if [[ "$normalized" != "$created_at" ]]; then
  echo 'CI creation timestamp did not round-trip exactly' >&2
  exit 2
fi
stamp="$(date -u -d "$created_at" +'%Y%m%dT%H%M%SZ')"
printf 'v%s-debug.%s\n' "$version" "$stamp"
