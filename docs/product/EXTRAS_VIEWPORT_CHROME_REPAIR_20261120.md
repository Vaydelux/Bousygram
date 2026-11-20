# Bousygram Extras — viewport-first catalog layout repair

Date: 2026-11-20
Scope: Android **Extras catalog** only (Movies, TV Shows, Anime).
Telegram Media and its playback must remain untouched.

## Why the previous UI was not responsive

Real phone screenshots at approximately 844×390dp landscape showed:
- A floating OutlinedTextField label clipped over the tab row.
- Both Compose's TextField outline and a custom focus outline rendering
  independently, appearing as a thick, doubled, misaligned border.
- A separate page title row, tab row, search row, filter disclosure row,
  horizontally clipped expanded-filter row, and result/refresh row stacked
  inside a very short available window. Result posters fell below the fold.
- Original language and other filter chips hidden beyond the viewport,
  with no useful representation of all available controls.

The earlier dp policy sized **individual** controls and cards, not the entire
vertical chrome budget. Tests of independent components therefore missed the
actual combined-screen failure.

## New UI behavior

1. Compact landscape phone windows (effective width >=520dp, effective
   height <520dp, and width >1.15×height) render the Back, Extras label,
   all three tabs and Info **in a single header row**. Compact layout is
   based on the actual Compose window dp, not screenshots, device ABI or pixels.
2. The search field and Filters/Saved controls occupy **one toolbar row**
   in compact landscape. Smaller phones and high-font-scale windows retain
   a vertically stacked toolbar to preserve readable field width.
3. Search has a single placeholder only; the floating Material3 label
   and redundant custom focus border are removed. Material3 retains its
   native focused outline and clear button. Search action still dismisses IME.
4. Advanced filters open as a **bounded Dialog with vertical LazyColumn**
   and full-width controls. Filter options are not a horizontal, partially
   hidden row, and expansion does not reduce poster grid height. The dialog
   uses native Back/dismiss and Done. Long dropdowns are scrollable.
5. Wider short-landscape phones (effective width >=700dp) move the result
   count and icon-only Refresh action into the one-line header, removing
   another control row ahead of posters. Smaller windows retain the status
   row, and controls remain at least 48dp touch targets.
6. Compact landscape poster cards use a 124dp minimum width and 8dp gaps;
   portrait phones/tablets and TVs retain their existing media grid sizing.
7. Compact landscape gutters become 16dp, filter-to-results spacing 6dp,
   and control minimum 48dp. TV retains its higher minimum control height,
   density/overscan treatment and D-pad focus behavior.
8. Existing grid state, TMDB/AniList fetching, posters, search debounce,
   user Saved library, loading/error fallbacks and playback source are
   deliberately unchanged.

## Required dp/orientation acceptance

- Phone portrait: 320×568, 360×740, 393×852, 428×926dp.
- Phone landscape: 520×360, 640×360, 740×360, 844×390dp.
- Tablet: 768×1024 and 1024×768dp.
- Google TV: 960×540 and 1280×720dp (plus larger UHD canvas).
- Font scales 1.0×, 1.35×, 1.6× and 2.0×. No horizontal chip clipping,
  no floating label collision, no content behind status/navigation bars,
  and no focus traps in filter dialog.
- Separate verification for Movies, TV Shows and Anime tabs with Filters
  collapsed, open, and provider dropdown menu open; search IME dismissed.
- Look for a meaningful first poster row above the fold in 844×390dp
  landscape. Explicit screenshots are required for visual sign-off; a
  passing unit test alone does not prove all device rendering.

## Regression suite

- JVM `ExtrasLayoutPolicyTest`: compact landscape breakpoint, gutters,
  font-scale fallback, portrait/tablet/TV exclusion.
- Android `ExtrasSearchUiInstrumentedTest`: both actions in compact
  inline toolbar remain accessible and geometrically alongside search.
- Existing Android responsive UI and Cast/media suites, Android Enterprise,
  and exact-commit debug artifact gate must run before release.

No claims are made about physical-device qualification until the new screen
has been viewed at the requested form factors. This patch does not change
the Telegram Media player.
