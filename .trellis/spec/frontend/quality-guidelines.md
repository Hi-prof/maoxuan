# Android UI Quality Guidelines

## Required Commands

Use JDK 17 and the configured Android SDK:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

Before release, also run `clean check`, `assembleRelease`, and instrumentation on API 28 plus the latest configured API.

For an installable personal build:

```powershell
.\gradlew.bat :app:assemblePersonal --no-daemon
```

`personal` inherits Release minification/resource shrinking and uses the standard local Android debug certificate. It is for local installation only.

Production `release` packaging consumes only `ANDROID_RELEASE_KEYSTORE_PATH`,
`ANDROID_RELEASE_STORE_PASSWORD`, `ANDROID_RELEASE_KEY_ALIAS`, and
`ANDROID_RELEASE_KEY_PASSWORD`. Missing or partial configuration must fail
before any release packaging task executes; Debug, Personal, and Release lint
remain usable without production credentials. Never commit the keystore or put
credentials in Gradle properties.

Keep `isMinifyEnabled = true` and `isShrinkResources = true` explicit on `personal`. Do not assume `initWith(release)` registers every optimization task: the build is acceptable only when its task graph contains both `minifyPersonalWithR8` and `shrinkPersonalRes`.

## Required Visual Coverage

- Viewports: `360 x 640`, `360 x 800`, and `412 x 915` dp.
- Content: short, medium, 90-character quote, longest work title/source, long back, missing optional sections, withdrawn state.
- Themes: system light and dark settings must both render the same fixed light app palette.
- Interactions: vertical forward/back paging, flip, back scrolling without paging, like, favorite, search, navigation restoration, update confirmation/cancel/error.
- Saved/notes: independent favorite/liked segment switching, four-item bottom
  navigation, note empty/list/editor states, standalone and linked note entry,
  delete/discard confirmation, persistence after restart, and withdrawn card
  summaries.
- Interpretation: action order at `360 x 640`, all three headings, long-body
  scrolling, close/back-state preservation, and sheet navigation-bar insets.
- Share output: exactly `1080 x 1440`, nonblank, background pixels present, quote/title/source in bounds, no UI controls.

## Regression Requirement

Every clipping, overlap, gesture, lifecycle, or accessibility bug gets an instrumentation regression when the assertion is stable. The compact quote regression fixes the card at `360 x 357 dp`, renders 90 code points, and asserts:

```text
quote.bottom <= source.top
source.bottom <= card.bottom
```

## Forbidden Patterns

- Text or action overlap, clipped source metadata, or scrollable quote fronts.
- Screen-size-dependent share screenshots.
- Dynamic dark theme or system-driven color inversion.
- Common action icons drawn by hand when Material provides them.
- Nested decorative cards, gradients, full-screen red, oversized empty hero layouts, or background photos that overpower text.
- Network calls from composition or automatic lifecycle callbacks.

## Review Checklist

- All visible actions have labels or content descriptions.
- Stable dimensions prevent state/content from shifting the card or toolbar.
- `letterSpacing` remains `0.sp`; font size does not scale with viewport width.
- The full back remains reachable at the minimum viewport.
- The interpretation sheet keeps its title, close action, and all three sections
  reachable at every target viewport without moving the underlying page.
- The four card icon actions keep `48.dp` targets. At minimum width the icon
  actions and interpretation/background actions may form two stable rows, but
  must not overlap the card, navigation, or one another.
- Note title/body/error content stays reachable above the IME; save/delete in
  progress blocks duplicate actions and system-back navigation.
- Pager state survives switching bottom tabs and detail/list return preserves position.
- APK permissions remain limited to network plus AndroidX's generated internal receiver permission.
- A production APK passes `apksigner verify --verbose --print-certs`; `aapt dump badging`
  reports the expected application ID, version code, and version name.
- An `app-vX.Y.Z` Release is explicitly non-latest, and the repository latest
  Release still serves the public content `manifest.json` after APK publication.
- Room `3 -> 4` migration and note CRUD/withdrawal lifecycle tests run on API 28
  and the latest configured API.
