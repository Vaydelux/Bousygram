# Extras theater and discovery redesign — first implementation
Reference revisions: Zoku TV ebc6c26, Bousygram 74734c5 (last qualified debug).
Preserve Telegram Media unchanged. Use the shared native theme system, never copy web CSS or browser sandbox assumptions verbatim.

## Workflow
```mermaid
flowchart TD
  A[Extras catalog] --> B[Movies / TV / Anime]
  B --> C[Search or filter]
  C --> D[Title details]
  D --> E[Saved titles]
  D --> F[Season and episode or movie]
  F --> G[Selected external player]
  G --> H[Bounded theater stage]
  H --> I[Tools: source, settings, diagnostics]
  H --> J[Native recovery]
  J --> K[Retry or choose server]
  K --> G
  H --> L[HTML fullscreen]
  L --> H
  H --> M[Return to title]
```

## Design
- Discovery: Telegram-style compact header, segmented catalog navigation, unified search/filter row on wide displays, stable poster grid and glass-styled saved/recent navigation. Existing per-tab search, paging window and focus restoration remain.
- Detail: glass profile surface, retain responsive artwork, synopsis, season/episode list, trailers, cast and metadata. Play and save stay explicit. No implied playback progress from metadata or iframe loads.
- Theater: video occupies remaining height, title and server controls in compact chrome, episode navigation below. Tools dialog contains diagnostics, explicit compatibility consent, reload and existing native-source controls; the WebView is not remounted merely by opening Tools.
- Security: reject child windows and JS dialogs. Small known tracker-domain list with exact DNS boundaries, never block the selected main document by that privacy rule. Do not promise removal of provider-controlled in-page overlays, DRM, or third-party playback availability. Preserve HTTPS, title identity, cookie opt-in, TLS rejection, no local access, and recovery.
- Devices: 320–430dp phones, landscape, TV 960×540dp, larger TVs/tablets, 1.0–2.0 font scale; 48dp minimum touch targets, 56dp TV controls where already applicable. Preserve low-memory bounded caches and no background scrape.

## Qualification and remaining work
Run offline request-policy and UI tests, emulator real-launch and D-pad smoke, test all 16 themes/Reduce Transparency, then test user-authorized real sources and physical ARM32 TV. Inspect actual screenshots; this document is a design contract, not proof of rendered pixels.
Next design work: richer scrollable discovery rails using actual catalog data, consolidated title-detail information hierarchy and saved/recent cards; do not fabricate provider availability or watch progress. Consider GeckoView only after measuring WebView version/codec limitations, APK and memory impact. Do not add a new playback engine merely to work around providers' page behavior.

## Phase 2–3 implementation
The featured block and bounded lazy rails now occupy full-span items within the existing grid; poster restoration includes the exact prefix count. Saved/recent rails use only local metadata for the active category; highly rated uses existing results with numeric scores ≥8. No extra API traffic or fake watch history. Narrow/short windows and filtered searches remain grid-only. Episode artwork has bounded decoding and fallback visuals; primary actions use concise labels. Qualification still required.

## Phase 4–5 player refinement
Explicit reload now invalidates session observations and approvals and recreates the WebView generation. Slow-page help stays non-blocking; blocked navigation does not overwrite playback state. Native immersive exit is available on touch as well as TV, with theme-aware server picker and selected-row contrast. No automatic server switching or relaxed security. Receiver approval test passed on unchanged Phase 2–3 retry; original intermittent cause remains unknown. Actual provider and physical-device qualification remain outstanding.

## Release candidate checkpoint
Phase 4 emulator suites passed: 78 responsive UI and 158 media/Cast test executions. Enterprise JVM verification found a stale wording assertion expecting legacy mode in the navigation-blocked advice. Update the assertion to require safe retry, server selection and explicit Playback tools; retain privacy and issue-priority checks. Request exact-main qualification and dated debug publication. Real provider playback, full visual screenshot review and physical ARM32 acceptance remain outstanding.

## Fullscreen and native playback follow-up
Both WebView fullscreen modes now auto-hide trusted HUD and TV cursor after 3 seconds of inactivity; touch, hover, D-pad activity or Back restores controls. Cursor focus remains mounted independently of its painted pointer. Media3 uses the shared cinematic scrubber and Bousygram action icons instead of the stock controller, preserving guarded transport and explicit recovery. Existing qualified debug Cast sender remains unchanged; public receiver-compatible source and subtitle origins must be qualified before enabling Cast. Direct VTT/TTML supported by existing policy; SRT conversion alone is not receiver delivery. Actual device and emulator verification remains required.

## Fullscreen subtitle geometry and local track switching
The external HTML player owns embed subtitle styling; no provider CSS is rewritten. Extras now scopes immersive system bars to the fullscreen lifecycle and requests native custom-view layout on real viewport changes, including return to the embedded view. This addresses app-side geometry without guaranteeing that external CSS recalculates. Media3 local subtitle selection captures current seekable position and play intent only when the user actually changes tracks; it remains unavailable in Cast-eligible sessions until track switching and sender ownership can be coordinated. Receiver-compatible VTT/TTML support remains gated; SRT delivery and physical qualification are pending.
