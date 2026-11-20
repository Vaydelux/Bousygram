const themeColors = Object.freeze({
  dark: "#070d18",
  light: "#f5faff",
  sakura: "#fff6f9",
  high_contrast: "#060708",
});

const theme = document.querySelector("#theme");
const themeColor = document.querySelector('meta[name="theme-color"]');

function applyTheme(value) {
  const selected = Object.hasOwn(themeColors, value) ? value : "dark";
  theme.value = selected;
  document.documentElement.dataset.theme = selected;
  themeColor.content = themeColors[selected];
}

try {
  applyTheme(localStorage.getItem("bousygram-web-theme"));
} catch {
  applyTheme("dark");
}

theme.addEventListener("change", () => {
  applyTheme(theme.value);
  try {
    localStorage.setItem("bousygram-web-theme", theme.value);
  } catch {
    // The selected theme still applies for this visit.
  }
});

const video = document.querySelector("#video");
const fileInput = document.querySelector("#file");
const placeholder = document.querySelector("#placeholder");
const title = document.querySelector("#video-title");
const status = document.querySelector("#playback-status");
let videoUrl;

fileInput.addEventListener("change", () => {
  const file = fileInput.files[0];
  if (!file) return;
  if (videoUrl) URL.revokeObjectURL(videoUrl);
  videoUrl = URL.createObjectURL(file);
  title.textContent = file.name;
  status.textContent = "Opening video.";
  placeholder.hidden = true;
  video.hidden = false;
  video.src = videoUrl;
  fileInput.value = "";
});

video.addEventListener("loadedmetadata", () => {
  status.textContent = "Ready to play.";
});

video.addEventListener("error", () => {
  if (!videoUrl) return;
  status.textContent = "This browser couldn't play the file. Try an MP4 encoded with H.264 and AAC.";
});

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("./sw.js").catch(() => {
    // Playback remains available when the browser declines offline storage.
  });
}
