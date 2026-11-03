"""Repository contract checks for the current brand and theme architecture.

These static checks are cheap and intentionally do not replace Gradle lint,
JVM tests, Android instrumentation or manual device contrast verification.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
read = lambda name: (ROOT / name).read_text(encoding="utf-8")
theme = read("app/src/main/java/com/vaydelux/bousygram/presentation/theme/BousygramTheme.kt")
tokens = read("app/src/main/java/com/vaydelux/bousygram/presentation/theme/BousygramUiTokens.kt")
settings = read("app/src/main/java/com/vaydelux/bousygram/presentation/settings/SettingsScreen.kt")
player = read("app/src/main/java/com/vaydelux/bousygram/presentation/media/PlayerScreen.kt")
readme = read("README.md")
brand = read("brand/README.md")

presets = re.findall(r"^    (DARK|HIGH_CONTRAST|LIGHT)\(", theme, re.M)
assert presets == ["DARK", "HIGH_CONTRAST", "LIGHT"], presets
assert 'fun fromId(id: String?)' in theme
assert 'remember(preset) { schemeFor(preset) }' in theme
assert 'Color(0xFF37D7F2)' in theme
assert 'Color(0xFFF5FAFF)' in theme
assert 'themeContrastRatio' in tokens
assert 'BousygramThemePreset.entries.forEach' in settings
assert 'LocalConfiguration.current.screenWidthDp < 480' in settings
assert 'MaterialTheme.colorScheme.onSurface' in player
assert 'BousygramUiTokens.darkHudOpacity' in player
assert 'onEpisodes = if (queue.size > 1)' in player
assert 'episodesButtonFocusRequester' in player
assert 'brand/bousygram-mascot.webp' in readme
assert 'Chow Chow' in brand
assert (ROOT / "brand/favicon.ico").exists()
assert not (ROOT / "brand/bousygram-mark.svg").exists()
print("PASS: three persistent themes, contrast tokens, responsive theme selection, player episodes and approved mascot identity")
