# Changelog

## 1.0

- Stabilized Telegram playback with session-aware cache-ahead and retry diagnostics.
- Stopped active Telegram cache/download work when playback exits or the app backgrounds.
- Kept partial media cache for faster resume and safer long-form playback.
- Added Settings playback diagnostics export and copy actions.
- Added Settings storage manager with app cache, TDLib media cache, cache limit, and manual prune.
- Added configurable TDLib cache limits: 1 GB, 2 GB, 5 GB, and 10 GB.
- Improved Continue Watching behavior by including the last-played loaded item.
- Added release-ready Gradle versioning and versioned APK artifact names.
