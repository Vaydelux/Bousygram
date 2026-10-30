# Bousygram v2 — Layout Blueprint / Text Wireframes

**Status:** Target designs, not screenshots of built UI. The supplied screenshots are visual references for dark/blue palette, restrained cards, tab flow and media player. The on-screen simulated D-pad is explicitly **out of scope**.

## 1. Android TV: compact full-width grid explorer

~~~text
┌───────────────────────────────────────────────────────────────────────────────┐
│ [Brand] Bousygram                     [Search] [Source status] [Profile]      │
│                                                                               │
│    [ Saved Messages ]   [ Channels ]   [ Groups ]                            │
│  ───────────────────────────────────────────────────────────────────────────  │
│   Channels > Technology                  12 videos · cached · refreshing...   │
│                                                                               │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐          │
│  │    MEDIA     │ │    MEDIA     │ │    MEDIA     │ │    MEDIA     │          │
│  │    COVER     │ │    COVER     │ │    COVER     │ │    COVER     │          │
│  └──────────────┘ └══════════════┘ └──────────────┘ └──────────────┘          │
│     Name, 24m       Focused name    Name, 48m        Name, 15m                │
│                                                                               │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐          │
│  │     ...      │ │     ...      │ │     ...      │ │     ...      │          │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘          │
│                                                                               │
│  [Back] [Home]                           Count/page status · focus-safe edge  │
└───────────────────────────────────────────────────────────────────────────────┘
~~~

The double-line card outline is the **focused** card, not a selected tab. More columns may fit at larger window dp; the layout is dynamic, not four columns everywhere. Source and media cards use **vertical laziness**, never a horizontally scrolling primary grid. Keep overscan-safe margins and avoid clipping focus outlines.

### Home paths

~~~text
Saved Messages ──────────────────────────────────────> Media grid > Player
Channels -> Actual joined channel cards -> Channel > Media grid > Player
Groups   -> Actual group cards ---------> Non-forum -> Media grid > Player
                                    └---> Forum -> Actual topics > Topic media > Player
~~~

Do not infer missing Topic from filename. A channel may add a real metadata filter later, not an invented folder.

## 2. TV player: restrained, opaque-enough chrome

~~~text
┌───────────────────────────────────────────────────────────────────────────────┐
│ [‹ Back]                                             [Subtitles / Settings]  │
│                                                                               │
│                                                                               │
│                            VIDEO SURFACE                                      │
│                         (no giant nav dock)                                   │
│                                                                               │
│                                                                               │
│  Title of selected file                                                        │
│  [↺ Start Over]                                [⏸]                            │
│  12:35 ━━━━━━━━━━━━━●───────────────────────────────────────────── 01:24:12  │
│              [← seek -10s | preview 12:25] [seek +10s →]                      │
└───────────────────────────────────────────────────────────────────────────────┘
~~~

Actual left/right remote keys, **not** two huge TV buttons, own seek preview. In preview state, OK confirms, Back cancels. Unknown time shows **--:--**, never an invented duration. Start Over and Back must be separate accessible actions.

### Down overlay — same-source media only

~~~text
┌───────────────────────────────────────────────────────────────────────────────┐
│                  CURRENT VIDEO remains visible and playing                    │
│                      overlaid with subtle dim                                 │
│  ┌─────────────────────────── Related in this source ───────────────────────┐ │
│  │  [Video 1] [Video 2] [Video 3] [Video 4]                                │ │
│  │  [Video 5] [Video 6] [Video 7] [Video 8]                                │ │
│  └──────────────────────────────────────────────────────────────────────────┘ │
│     D-pad ↑ from the first row → return to player • Back → dismiss tray       │
└───────────────────────────────────────────────────────────────────────────────┘
~~~

This tray does not autoplay or skip until OK selects an actual media entry. The source and topic ID from the current playback session constrain visible items.

## 3. Tablet: directory + content

~~~text
┌───────────────────────────────────────────────────────────────────────┐
│ Bousygram              Saved | Channels | Groups              Search   │
├───────────────────────┬───────────────────────────────────────────────┤
│ Channel directory     │ Channel > Name                               │
│ ▸ Channel A           │ [Video] [Video] [Video]                       │
│   Channel B           │ [Video] [Video] [Video]                       │
│   Channel C           │ [Video] [Video] [Video]                       │
│                       │                                               │
│                       │                                      Refresh   │
└───────────────────────┴───────────────────────────────────────────────┘
~~~

Use two panes only when width supports comfortable touch targets. Fall back to the phone-like stacked layout for narrow tablets and split-screen.

## 4. Mobile: stacked media explorer

~~~text
┌───────────────────────────────────┐
│ [Logo] Bousygram          Search   │
│ [Saved] [Channels] [Groups]       │
│ Channels > Tech Channels          │
│ ┌─────────────┐ ┌─────────────┐   │
│ │    CARD     │ │    CARD     │   │
│ └─────────────┘ └─────────────┘   │
│ ┌─────────────┐ ┌─────────────┐   │
│ │    CARD     │ │    CARD     │   │
│ └─────────────┘ └─────────────┘   │
│ [Back]             [Settings]     │
└───────────────────────────────────┘
~~~

No fake TV D-pad simulator in the shipped app. Mobile touch gestures use the same real source hierarchy and permissions but device-appropriate input affordances.

## 5. Startup and future Extras maintenance

~~~text
Bousygram splash (real loading/readiness → interactive)
  ├── [ Telegram Media ] ── existing Telegram auth/session ── explorer
  └── [ Movies / TV / Anime — Coming Soon ]
                     └── responsive Maintenance/Work-in-Progress page
                         [ Back to modes ]
~~~

Extras placeholder must be a complete dead-end route: no WebView, TMDB, AniList, virtual mouse, remote embeds, or external catalog calls in this phase.

## 6. Design constraints to implement and verify

- Themeable blue/neutral premium style, original brand mark and clear high-contrast focused state.
- TV compact/standard/accessible density; no half-screen header or player controls.
- Focus ring always in safe drawing bounds. Avoid clipping/scale at grid edges.
- Single vertical content scroll owner per view; no nested unbounded scroll containers.
- Empty, error, offline, fetching and refreshing are distinct states.
- Smooth 120–220ms initial transition design, subject to real low-end hardware measurement; reduced motion supported.
- Screen readers and real touch/remote keyboard navigation remain first-class.

**Design review:** Compare screenshot references on the target devices; these diagrams illustrate information layout but do not prove screenshot fidelity or device performance.
