#!/usr/bin/env bash
# Optional live-provider smoke test. Requires a protected TMDB_API_KEY secret.
# Never echo the key, provider JSON, headers, or full request URLs.
set -euo pipefail
: "${TMDB_API_KEY:?Set protected TMDB_API_KEY to run live Extras integration checks}"
command -v curl >/dev/null
command -v jq >/dev/null

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

api="https://api.themoviedb.org/3"
tmdb() {
  local result="$1"; shift
  curl --silent --show-error --fail --retry 2 --retry-delay 2 \
    --connect-timeout 6 --max-time 25 \
    --get "$api/$result" \
    --data-urlencode "api_key=$TMDB_API_KEY" \
    "$@" -o "$tmp/tmdb.json"
}

# Exercise the actual API namespaces used by the three Extras tabs.
tmdb "search/movie" --data-urlencode "query=Fight Club" --data-urlencode "include_adult=false"
jq -e '.results | type == "array" and length > 0' "$tmp/tmdb.json" >/dev/null
echo "TMDB movie search: PASS"

tmdb "search/tv" --data-urlencode "query=Game of Thrones" --data-urlencode "include_adult=false"
jq -e '.results | type == "array" and length > 0' "$tmp/tmdb.json" >/dev/null
echo "TMDB TV search: PASS"

tmdb "discover/movie" --data-urlencode "with_original_language=ja" --data-urlencode "page=1"
jq -e '.results | type == "array"' "$tmp/tmdb.json" >/dev/null
echo "TMDB language-filtered discovery: PASS"

# Keep watch/providers slash literal like Bousygram's production client.
curl --silent --show-error --fail --retry 2 --retry-delay 2 \
  --connect-timeout 6 --max-time 25 \
  "$api/movie/550?append_to_response=credits,videos,reviews,recommendations,watch/providers" \
  --get --data-urlencode "api_key=$TMDB_API_KEY" \
  -o "$tmp/tmdb.json"
jq -e '.id == 550 and (.credits | type == "object") and
    (.videos | type == "object") and (.["watch/providers"] | type == "object")' \
    "$tmp/tmdb.json" >/dev/null
echo "TMDB title, credits, trailers and regional providers: PASS"

tmdb "tv/1399/season/1"
jq -e '.episodes | type == "array" and length > 0' "$tmp/tmdb.json" >/dev/null
echo "TMDB TV seasons/episodes: PASS"

# Two lightweight AniList GraphQL requests; never perform a load test against
# the public rate-limited service. No token is required for public catalog data.
curl --silent --show-error --fail --retry 2 --retry-delay 3 \
  --connect-timeout 6 --max-time 25 \
  "https://graphql.anilist.co" \
  -H "Content-Type: application/json" \
  --data '{"query":"query ($page:Int!) { Page(page:$page,perPage:3) { pageInfo { hasNextPage } media(type:ANIME,isAdult:false) { id title { romaji } } } }","variables":{"page":1}}' \
  -o "$tmp/anilist.json"
jq -e '(.errors == null) and (.data.Page.media | type == "array" and length > 0)' \
  "$tmp/anilist.json" >/dev/null
echo "AniList anime discovery: PASS"

curl --silent --show-error --fail --retry 2 --retry-delay 3 \
  --connect-timeout 6 --max-time 25 \
  "https://graphql.anilist.co" \
  -H "Content-Type: application/json" \
  --data '{"query":"query ($id:Int!) { Media(id:$id,type:ANIME,isAdult:false) { id title { romaji } characters(page:1,perPage:2) { edges { node { name { full } } } } } }","variables":{"id":1}}' \
  -o "$tmp/anilist.json"
jq -e '(.errors == null) and (.data.Media.id == 1)' "$tmp/anilist.json" >/dev/null
echo "AniList title/character detail: PASS"

echo "Live Extras API smoke checks passed; no credentials or provider payloads were archived."
