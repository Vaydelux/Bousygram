/** Public design tokens mirrored from BousygramThemePreset.
 * Keep ids/primary/background in sync with Android. No login or network calls.
 */
const entries = [
  ["dark", "Midnight", "#070d18", "#121c2b", "#8ae8fa", "#abc4ff", "#8399b1", false],
  ["aurora", "Aurora", "#0b0a1a", "#18152d", "#b3a2ff", "#82f2e4", "#988bc6", false],
  ["obsidian", "Obsidian", "#090c12", "#151b24", "#dde3ef", "#8de8ff", "#9faab9", false],
  ["evergreen", "Evergreen", "#061712", "#0d241c", "#91f2c6", "#a6d8ff", "#95ad9f", false],
  ["ember", "Ember", "#190d13", "#27171c", "#ffb695", "#ffd6a3", "#c1a0a8", false],
  ["high_contrast", "High contrast", "#060708", "#0f1215", "#ffe66a", "#7ce7ff", "#e1e7ef", false],
  ["light", "Daylight", "#f5faff", "#ffffff", "#075b83", "#19548b", "#70849a", true],
  ["ocean", "Deep Ocean", "#041923", "#0b2734", "#85dcf3", "#a9b6ff", "#88a7b4", false],
  ["plum", "Plum", "#180f1d", "#281830", "#f2abd8", "#c9b2ff", "#bda6bd", false],
  ["ivory", "Ivory", "#fbf6ee", "#fffdf8", "#79523f", "#665181", "#85786b", true],
  ["sakura", "Sakura Blossom", "#fff6f9", "#ffffff", "#a62359", "#6a3a82", "#c593a8", true],
  ["matcha", "Matcha Cream", "#f5faf1", "#fcfdf9", "#2b6640", "#874150", "#93a896", true],
  ["cotton_candy", "Dreamy Pastel", "#131024", "#1d1834", "#ffa8dc", "#8ce4ff", "#a799d1", false],
  ["material_you", "Material Tonal", "#f6f8fe", "#ffffff", "#2d53a8", "#216558", "#8c98b0", true],
  ["cupertino", "Cupertino Frost", "#f2f2f7", "#ffffff", "#005ec4", "#4c3fb5", "#9898a3", true],
  ["midnight_glass", "iOS Midnight", "#050508", "#121217", "#5ac8fa", "#6ce8b8", "#8c8c9a", false],
];

export const THEMES = Object.freeze(Object.fromEntries(entries.map(
  ([id, label, background, surface, accent, accentEnd, outline, light]) => [
    id, Object.freeze({
      id, label, background, surface, accent, accentEnd, outline, light,
      text: light ? "#172535" : "#f4f7ff",
      muted: light ? "#40536a" : "#bdc8d9",
      onAccent: light ? "#ffffff" : "#06121a",
    })
  ]
)));

export function resolveTheme(id) {
  return Object.prototype.hasOwnProperty.call(THEMES, id) ? THEMES[id] : THEMES.dark;
}

export function formatClock(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const whole = Math.floor(seconds);
  const hours = Math.floor(whole / 3600);
  const minutes = Math.floor((whole % 3600) / 60);
  const secs = String(whole % 60).padStart(2, "0");
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, "0")}:${secs}` : `${minutes}:${secs}`;
}

export function fraction(position, duration) {
  if (!Number.isFinite(position) || !Number.isFinite(duration) || duration <= 0) return 0;
  return Math.max(0, Math.min(1, position / duration));
}
