from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
theme = (ROOT / "app/src/main/java/com/vaydelux/bousygram/presentation/theme/BousygramTheme.kt").read_text()
activity = (ROOT / "app/src/main/java/com/vaydelux/bousygram/app/MainActivity.kt").read_text()
prefs = (ROOT / "app/src/main/java/com/vaydelux/bousygram/data/store/EnterprisePreferences.kt").read_text()
settings = (ROOT / "app/src/main/java/com/vaydelux/bousygram/presentation/settings/SettingsScreen.kt").read_text()
components = (ROOT / "app/src/main/java/com/vaydelux/bousygram/presentation/components/EnterpriseComponents.kt").read_text()
source_picker = (ROOT / "app/src/main/java/com/vaydelux/bousygram/presentation/components/ChatSourcePicker.kt").read_text()
player = (ROOT / "app/src/main/java/com/vaydelux/bousygram/presentation/media/PlayerScreen.kt").read_text()

presets = re.findall(r'^    (MIDNIGHT|AURORA|OCEAN|SUNSET)\(', theme, re.M)
assert presets == ["MIDNIGHT", "AURORA", "OCEAN", "SUNSET"], presets
assert 'fun fromId(id: String?)' in theme
assert 'remember(preset) { schemeFor(preset) }' in theme
assert 'BousygramTheme(preset = themePreset)' in activity
assert 'onThemePresetChange = { preset ->' in activity
assert 'KEY_VISUAL_THEME = "visual_theme"' in prefs
assert 'fun visualThemeId()' in prefs and 'fun setVisualThemeId(value: String)' in prefs
assert '.put("visualTheme", visualThemeId())' in prefs
assert 'putString(KEY_VISUAL_THEME, visualTheme)' in prefs
assert 'BousygramThemePreset.entries.forEach' in settings and 'ThemePresetCard(' in settings
assert 'preset.primary, preset.secondary, preset.tertiary' in settings
assert 'MaterialTheme.colorScheme.background' in components
assert 'MaterialTheme.colorScheme.surfaceVariant' in components
assert 'scaleX = if (focused) 1.035f else 1f' in components
assert 'RoundedCornerShape(16.dp)' in components
assert 'MaterialTheme.colorScheme.primary' in source_picker and 'MaterialTheme.colorScheme.surface' in source_picker
assert 'color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)' in player
assert 'selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)' in player
print('PASS: four persistent theme presets, live theme switching, settings import/export, theme-aware surfaces, card/focus styling, source picker, and player overlay')
