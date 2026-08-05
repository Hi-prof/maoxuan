# Compose 1.7.6 gesture flow notes

## Local dependency

- `gradle/libs.versions.toml` resolves Compose BOM `2024.12.01`.
- The resolved Compose Foundation implementation is `1.7.6`.

## Source findings

The local Gradle dependency was matched against the official
`foundation-android-1.7.6-sources.jar` from Google's Android Maven repository.

- `ScrollingLogic.performScroll` sends a child's unconsumed delta through
  `dispatchPostScroll` after the child attempts its own single-axis scroll.
- A parent `ScrollableNestedScrollConnection.onPostScroll` calls
  `performRawScroll(available)`, so a `verticalScroll` child naturally hands
  edge remainder to the parent `VerticalPager`.
- `PagerDefaults.flingBehavior` defaults `snapPositionalThreshold` to `0.5f`.
  For low velocity, the pager advances only when its own consumed distance is
  greater than that fraction of the page. A high velocity fling still advances.
- `PagerSnapDistance.atMost(1)` remains the default and limits one fling to one
  adjacent page.

## Consequence for this task

No custom nested-scroll bridge is required. The back face already hands its
unused edge delta to the pager. A lower positional threshold only while the
back face is active makes that handed-off remainder sufficient without making
front-face paging more sensitive or bypassing the back face's own scrolling.
