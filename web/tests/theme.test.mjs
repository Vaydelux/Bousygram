import test from "node:test";
import assert from "node:assert/strict";
import { THEMES, resolveTheme, formatClock, fraction } from "../theme.mjs";

test("browser preview exposes all 16 Android preset IDs", () => {
  const ids = [
    "dark", "aurora", "obsidian", "evergreen", "ember", "high_contrast",
    "light", "ocean", "plum", "ivory", "sakura", "matcha",
    "cotton_candy", "material_you", "cupertino", "midnight_glass",
  ];
  assert.deepEqual(Object.keys(THEMES), ids);
  assert.equal(resolveTheme("sakura").accent, "#a62359");
  assert.equal(resolveTheme("evergreen").accent, "#91f2c6");
  assert.equal(resolveTheme("dark").background, "#070d18");
  assert.equal(resolveTheme("not-a-preset").id, "dark");
  assert.equal(resolveTheme(null).id, "dark");
  for (const token of Object.values(THEMES)) {
    assert.match(token.accent, /^#[a-f\d]{6}$/i);
    assert.match(token.background, /^#[a-f\d]{6}$/i);
    assert.notEqual(token.accent, token.background);
  }
});

test("playback time labels remain correct for short and long movies", () => {
  assert.equal(formatClock(-1), "0:00");
  assert.equal(formatClock(NaN), "0:00");
  assert.equal(formatClock(0), "0:00");
  assert.equal(formatClock(65.4), "1:05");
  assert.equal(formatClock(3600), "1:00:00");
  assert.equal(formatClock(4235), "1:10:35");
});

test("seek ratios clamp broken or unavailable durations", () => {
  assert.equal(fraction(10, 20), 0.5);
  assert.equal(fraction(-10, 20), 0);
  assert.equal(fraction(21, 20), 1);
  assert.equal(fraction(10, 0), 0);
  assert.equal(fraction(10, NaN), 0);
  assert.equal(fraction(NaN, 20), 0);
});
