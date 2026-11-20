#!/usr/bin/env bash
# The Telegram Media product is intentionally local-only for title metadata.
# Extras is a separate TMDB/AniList catalog and must remain functional.
set -Eeuo pipefail

root="app/src/main/java/com/vaydelux/bousygram"
deleted=(
  "$root/data/repository/TmdbRepository.kt"
  "$root/data/repository/TmdbQueryPolicy.kt"
  "$root/data/repository/TmdbImageCachePolicy.kt"
  "$root/data/repository/TelegramMetadataCorrections.kt"
  "$root/presentation/media/TelegramMetadataCorrectionDialog.kt"
  "app/src/test/java/com/vaydelux/bousygram/data/repository/TmdbRepositoryTest.kt"
  "app/src/test/java/com/vaydelux/bousygram/data/repository/TmdbQueryPolicyTest.kt"
  "app/src/test/java/com/vaydelux/bousygram/data/repository/TmdbImageCachePolicyTest.kt"
  "app/src/androidTest/java/com/vaydelux/bousygram/data/repository/TelegramMetadataCorrectionsInstrumentedTest.kt"
)

for file in "${deleted[@]}"; do
  if [[ -e "$file" ]]; then
    echo "::error::Telegram TMDB feature must stay removed: $file"
    exit 1
  fi
done

if grep -En 'onCorrectMetadata|TelegramMetadataCorrectionDialog|TelegramMetadataCorrections|tmdbExternalDiscoveryEnabled|TMDB discovery for Telegram videos' \
  "$root/presentation/media/BrowseScreen.kt" \
  "$root/presentation/media/TelegramExplorerDashboard.kt" \
  "$root/presentation/media/vm/MediaViewModel.kt" \
  "$root/presentation/components/MediaActionsDropdown.kt" \
  "$root/presentation/components/EnterpriseComponents.kt" \
  "$root/presentation/settings/SettingsScreen.kt" \
  "$root/data/store/EnterprisePreferences.kt"; then
  echo "::error::Telegram external metadata UI or call path remains."
  exit 1
fi

if grep -Eq 'tmdbPosterPath|tmdbBackdropPath' \
  "$root/presentation/components/EnterpriseComponents.kt"; then
  echo "::error::Telegram card still prefers a third-party thumbnail."
  exit 1
fi

grep -Fq 'SeriesMetadataParser.parse(' \
  "$root/presentation/media/vm/MediaViewModel.kt" || {
  echo "::error::Local Telegram season/episode parsing was removed."
  exit 1
}

grep -Fq 'val preferredThumbnail = media.thumbnailPath?.takeIf' \
  "$root/presentation/components/EnterpriseComponents.kt" || {
  echo "::error::Telegram card no longer uses its original thumbnail."
  exit 1
}

test -f "$root/presentation/extras/ExtrasCatalogApi.kt" || {
  echo "::error::Independent Extras discovery was removed."
  exit 1
}

echo 'PASS: Telegram external metadata removed; local parsing, thumbnails and Extras preserved.'
