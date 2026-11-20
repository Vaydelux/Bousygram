import { THEMES, resolveTheme, formatClock, fraction } from "./theme.mjs";

const $ = selector => document.querySelector(selector);
const themeSelect = $("#theme");
const themeColor = $('meta[name="theme-color"]');
const video = $("#video");
const fileInput = $("#file");
const captionInput = $("#caption-file");
const placeholder = $("#placeholder");
const title = $("#video-title");
const status = $("#playback-status");
const playToggle = $("#play-toggle");
const seek = $("#seek");
const current = $("#current-time");
const duration = $("#duration");
const fullscreen = $("#fullscreen");
const captionStatus = $("#caption-status");
let videoUrl = null;
let captionUrl = null;
let captionTrack = null;

function applyTheme(id) {
  const selected = resolveTheme(id);
  themeSelect.value = selected.id;
  document.documentElement.dataset.theme = selected.id;
  document.documentElement.style.colorScheme = selected.light ? "light" : "dark";
  for (const key of ["background", "surface", "text", "muted", "outline", "accent", "accentEnd", "onAccent"]) {
    const property = key.replace(/[A-Z]/g, character => "-" + character.toLowerCase());
    document.documentElement.style.setProperty(`--${property}`, selected[key]);
  }
  themeColor.setAttribute("content", selected.background);
  // Native player controls and custom scrubber both respond to the theme.
  video.style.accentColor = selected.accent;
  seek.style.accentColor = selected.accent;
}

try {
  applyTheme(localStorage.getItem("bousygram-web-theme"));
} catch {
  applyTheme("dark");
}

themeSelect.addEventListener("change", () => {
  applyTheme(themeSelect.value);
  try {
    localStorage.setItem("bousygram-web-theme", themeSelect.value);
  } catch {
    // Private browsing may forbid storage. The current selection still works.
  }
});

function refreshTimeline() {
  const ready = Number.isFinite(video.duration) && video.duration > 0;
  seek.disabled = !ready;
  seek.value = String(Math.round(fraction(video.currentTime, video.duration) * 1000));
  seek.style.setProperty("--played", `${Number(seek.value) / 10}%`);
  current.textContent = formatClock(video.currentTime);
  duration.textContent = ready ? formatClock(video.duration) : "0:00";
  playToggle.textContent = video.paused ? "Play" : "Pause";
  playToggle.disabled = !videoUrl;
  fullscreen.disabled = !videoUrl;
}

function clearCaptions() {
  if (captionTrack) {
    captionTrack.remove();
    captionTrack = null;
  }
  if (captionUrl) {
    URL.revokeObjectURL(captionUrl);
    captionUrl = null;
  }
  captionStatus.textContent = "No subtitles selected";
}

function clearVideo() {
  video.pause();
  video.removeAttribute("src");
  video.load();
  if (videoUrl) URL.revokeObjectURL(videoUrl);
  videoUrl = null;
  clearCaptions();
  video.hidden = true;
  placeholder.hidden = false;
  title.textContent = "Local video";
  refreshTimeline();
}

fileInput.addEventListener("change", () => {
  const file = fileInput.files?.[0];
  fileInput.value = "";
  if (!file) return;
  clearVideo();
  videoUrl = URL.createObjectURL(file);
  title.textContent = file.name;
  status.textContent = "Reading this device's file. No video is uploaded.";
  placeholder.hidden = true;
  video.hidden = false;
  video.src = videoUrl;
  video.load();
  refreshTimeline();
});

video.addEventListener("loadedmetadata", () => {
  status.textContent = "Ready. Press Play or use the browser's controls.";
  refreshTimeline();
});
video.addEventListener("durationchange", refreshTimeline);
video.addEventListener("timeupdate", refreshTimeline);
video.addEventListener("progress", refreshTimeline);
video.addEventListener("play", refreshTimeline);
video.addEventListener("pause", refreshTimeline);
video.addEventListener("ended", () => {
  status.textContent = "Playback finished.";
  refreshTimeline();
});
video.addEventListener("waiting", () => { status.textContent = "Buffering this device's file…"; });
video.addEventListener("playing", () => { status.textContent = "Playing locally on this device."; });
video.addEventListener("error", () => {
  if (!videoUrl) return;
  status.textContent = "Unsupported video or codec. Try MP4 (H.264 video and AAC audio).";
  refreshTimeline();
});

playToggle.addEventListener("click", async () => {
  if (!videoUrl) return;
  if (!video.paused) { video.pause(); return; }
  try {
    await video.play();
  } catch {
    status.textContent = "Playback was blocked. Use Play in the video controls.";
  }
});

seek.addEventListener("input", () => {
  if (!Number.isFinite(video.duration) || video.duration <= 0) return;
  video.currentTime = (Number(seek.value) / 1000) * video.duration;
  refreshTimeline();
});

fullscreen.addEventListener("click", async () => {
  if (!videoUrl) return;
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen();
    } else if (video.requestFullscreen) {
      await video.requestFullscreen();
    } else if (video.webkitEnterFullscreen) {
      video.webkitEnterFullscreen(); // iOS Safari
    }
  } catch {
    status.textContent = "Fullscreen is unavailable in this browser.";
  }
});

captionInput.addEventListener("change", () => {
  const file = captionInput.files?.[0];
  captionInput.value = "";
  if (!file) return;
  if (!file.name.toLowerCase().endsWith(".vtt") || file.size > 2 * 1024 * 1024) {
    captionStatus.textContent = "Choose a WebVTT subtitle file under 2 MB.";
    return;
  }
  clearCaptions();
  captionUrl = URL.createObjectURL(file);
  captionTrack = document.createElement("track");
  captionTrack.kind = "subtitles";
  captionTrack.label = file.name;
  captionTrack.srclang = "en";
  captionTrack.src = captionUrl;
  captionTrack.default = true;
  video.append(captionTrack);
  captionTrack.addEventListener("load", () => {
    for (const track of video.textTracks) track.mode = "showing";
  }, { once: true });
  captionStatus.textContent = `Loaded ${file.name} (WebVTT)`;
});

// Allow TV remotes and desktop keyboards to seek without requiring touch.
// Never hijack arrows on form inputs or native media controls.
document.querySelector("#player-card").addEventListener("keydown", event => {
  if (event.target.matches("input, select, button, textarea, video")) return;
  if (!videoUrl || !Number.isFinite(video.duration) || video.duration <= 0) return;
  const seconds = event.key === "ArrowLeft" ? -10 : event.key === "ArrowRight" ? 10 : 0;
  if (!seconds) return;
  event.preventDefault();
  video.currentTime = Math.max(0, Math.min(video.duration, video.currentTime + seconds));
  refreshTimeline();
});

window.addEventListener("pagehide", () => {
  if (videoUrl) URL.revokeObjectURL(videoUrl);
  if (captionUrl) URL.revokeObjectURL(captionUrl);
});

refreshTimeline();
if ("serviceWorker" in navigator && location.protocol === "https:") {
  navigator.serviceWorker.register("./sw.js", { scope: "./" }).catch(() => {
    // Offline shell is optional. Local playback does not rely on SW.
  });
}
