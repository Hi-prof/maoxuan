# Compose Component Guidelines

## Component Contract

Composable inputs are immutable domain values plus explicit callbacks:

```kotlin
@Composable
fun FlippableQuoteCard(
    card: QuoteCard,
    flipped: Boolean,
    onFlippedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun BackgroundSheet(
    card: QuoteCard,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- Put `modifier` last with an empty default.
- Hoist state when it affects navigation, persistence, or another component.
- Keep transient presentation state local with a stable `remember` key, for example source expansion keyed by card ID.
- Do not pass DAO entities, serialized DTOs, `Context`, or a ViewModel into reusable display components.
- `CardActions` owns the shared action ordering. `onBackground` renders one
  `读背景` text action in both reader and detail routes; do not add a separate
  visible interpretation or return-to-front action.
- `CardActions` exposes an `onNote` callback in both reader and detail routes.
  Like, favorite, note, and share are four accessible `48.dp` icon targets.
  At a `320.dp` compact viewport, icon and reading actions keep one stable row
  inside the standard `16.dp` horizontal screen padding. The wrap threshold is
  based on their intrinsic footprint, not an assumed device width; only narrower
  window configurations may use two rows.

## Card Layout Contract

- Card radius is `8.dp`; actions remain outside the card.
- Quote text uses bundled `QuoteFontFamily`, `FontWeight.Bold`, and `letterSpacing = 0.sp`.
- Normal height font tiers are 34/31/28 sp for lengths `1-32`, `33-60`, and `61-90`.
- When `maxHeight < 480.dp`, compact tiers are 25/22/20 sp for `33-60`, `61-75`, and `76-90`.
- The quote area uses `weight(1f)` so the title/source block always retains space.
- Background images fill the card at approximately 17% visible strength, use a low-saturation color matrix, and remain visual context rather than a separate illustration panel. A missing image path renders only the paper surface, with no placeholder container.
- The interpretation face owns vertical scrolling. When that content reaches a
  vertical edge, the parent pager may consume the remaining drag so readers can
  continue to adjacent cards without returning to the front face.

The compact threshold and font tiers are a tested system. Any change must update the 90-character bounds test and screenshots for all target viewports.

## Summary List Contract

- Search, favorite, and liked results share `CardSummaryList`; do not fork feature-specific summary rows.
- Summary rows contain no image or placeholder. Their stable structure is a `46.dp` index column, a `2.dp` `SpiritRed` rule, quote/work/metadata text, and a bottom divider.
- Mao selection cards use `卷一` through `卷四`; poetry, Marxism, and general quotes use stable `诗词`, `马原`, and `名言` labels. Modern dates reduce to the four-digit year while values such as `前4世纪` remain complete.
- Quote text is limited to three lines, work title and metadata to one line each, and withdrawn state is plain small red text rather than a chip.
- The row minimum height is `112.dp`, but content may grow; tests at `360.dp` must assert that the index and text columns do not overlap.

### Flip Gesture Contract

- `FlippableQuoteCard` owns an `Orientation.Horizontal` drag gesture. A left or right drag toggles either face; a drag below 22% of card width settles back unless horizontal velocity reaches 900 dp/s.
- Do not attach a whole-card click handler. The quote/interpretation faces toggle only through horizontal dragging, and the interpretation face has no visible return icon.
- Drag distance directly drives `rotationY`. One 180-degree flip spans 72% of card width, and release settles with a non-bouncy medium-low-stiffness spring.
- Switch front/back content only after the rotation crosses 90 degrees, and rotate back content by 180 degrees so text is never mirrored.
- Resolve drag direction after touch slop. Lock to a horizontal flip only when
  horizontal travel is at least `1.25` times vertical travel; once locked, the
  card consumes the rest of that gesture so its vertical component cannot also
  move the pager or interpretation content. Vertical-dominant drags remain
  unconsumed by the flip recognizer.
- Interpretation content keeps its own `verticalScroll`, and the reader keeps
  `VerticalPager` user scrolling enabled while the interpretation face is open.
  Nested scrolling passes edge remainder to the pager; the back face uses a
  `25%` low-velocity snap threshold while the front keeps the default `50%`.
- Keep the perspective layer's shadow elevation at zero and use the `Surface`'s stable `2.dp` shadow. A transformed dynamic shadow produces an oversized rectangular projection on API 28.
- Release settling must clear transient state in `finally`, so an interrupted animation cannot block later gestures.

Required instrumentation assertions: swipe left to back, swipe right to front,
a slow short drag returns to its starting face, a mostly horizontal diagonal
drag flips without paging, horizontal lock prevents same-gesture back scrolling,
and one slow drag can scroll a long interpretation to its edge then page forward.
The compact quote/source bounds remain valid. Device review must include an
in-motion frame on API 28 plus settled front/back states on API 28 and the latest
API.

## Styling

- Use `Ink`, `MutedInk`, `SpiritRed`, `Paper`, `Canvas`, and `Divider` from `ui.theme`.
- Do not add gradients, decorative orbs, heavy black borders, fake-antique textures, nested cards, or full-screen red fills.
- Use Material icons already in the project for actions; do not draw common icons manually.
- Keep dimensions stable so selection, loading, long labels, and state changes do not shift the main card.
- Background uses a Material 3 modal bottom sheet with an `8.dp` top radius,
  a fixed text header, no close icon, and one independently scrolling column. Its
  source/context/background/story sections are unframed; do not wrap them in nested cards.
- When `QuoteCard.imageAttribution` is present, the background sheet exposes the creator plus separate accessible links for the image source and license evidence. Share images show a short creator/license credit, while the share Intent text carries both complete URLs.
- Keep the standard drag handle visible. Swipe-down is the primary dismiss
  interaction; scrim taps and system back retain Material 3 default behavior.

## Accessibility

- Every actionable icon has a Chinese `contentDescription` or a visible text label.
- Decorative background imagery has `contentDescription = null`.
- Touch targets remain usable at the smallest viewport.
- Preserve the four `48.dp` icon targets next to the background text action.
  The compact action row must fit without clipping at `320.dp` width.
- Text must be readable under large system font settings where practical; never resolve clipping by allowing overlap.

## Search Interaction

- Configure the search text field with `ImeAction.Search`; typing alone and clicking a history row must not persist a new history entry.
- While the query is blank, show durable history with separate accessible actions for deleting one row and clearing all rows.
- Search screens that request keyboard focus use IME insets so empty states and history rows remain visible above the soft keyboard on API 28 and later.

## Saved And Notes Interaction

- The `SavedScreen` owns one `收藏 / 点赞` segmented control. The two lists use
  independent `LazyListState` instances and continue to represent independent
  persisted flags.
- The bottom bar order is `阅读 / 收藏 / 笔记 / 我的`; do not put the liked list
  back into `我的`.
- `NotesScreen` renders standalone and linked notes in one recently-updated
  list. A linked summary shows quote, work title, and withdrawal status and has
  a separate card-detail click target from the note-editor target.
- `NoteEditorScreen` has an optional single-line title, a required flexible
  body, save and delete icons, delete confirmation, and unsaved-change
  confirmation. During save/delete, disable editor actions and consume system
  back so asynchronous completion cannot pop two destinations.
- Keep note/card association read-only after creation. A note opened from a card
  shows that card context; the standalone add action creates no association.

## Mine And Update Interaction

- `MineScreen` displays the App version and content version as separate sections
  with explicit `检查应用更新` and `检查内容更新` actions; do not restore the
  ambiguous single `检查更新` label.
- App update dialogs expose version/date/size/release notes before download,
  progress plus cancellation during download, install-permission recovery, and
  an installer retry action after verification.
- At `360 x 640 dp`, both update sections remain reachable without text or
  button overlap; use one bounded scrolling content surface for large fonts.

## Interest And Recommendation Interaction

- Show `InterestSelectionScreen` only when Room reports fresh-install
  onboarding is required. It exposes all 12 curated interests as `FilterChip`
  controls, accepts zero to five selections, and provides `开始阅读` plus
  `暂时跳过`.
- Apply `WindowInsets.safeDrawing` and keep the content vertically scrollable so
  both commands remain visible at `360 x 640 dp`, including three-button system
  navigation. Disable unselected chips after five choices while keeping selected
  chips available for deselection.
- `MineScreen` exposes one `阅读偏好` row for both new and upgraded users. The
  destination edits the same zero-to-five interest set, adds a separate
  `内容范围` multi-select with an explicit `全部内容` state, and conditionally offers
  `清除“减少此类”记录` behind a confirmation dialog.
- The final concrete content-series chip cannot be deselected; returning to an
  unrestricted reader requires the visible `全部内容` choice. Keep series and
  interest chips inside the same vertically scrollable content area.
- The reader header places `MoreVert` beside search and puts `减少此类` in its
  menu. Bind the command to the pager's actual settled card, disable it for
  loading/error/completion states, and advance only after persistence succeeds.
- Keep the card action row unchanged; recommendation feedback belongs in the
  header overflow rather than a fifth compact card action.

## Common Mistakes

- Using a fixed quote box height that clips the source on 360 x 640.
- Letting the back scroll and `VerticalPager` fight before the back content has
  first chance to scroll, failing to consume a direction-locked horizontal drag,
  or disabling pager scrolling so the back face traps the reader.
- Adding animated `shadowElevation` to the rotating perspective layer instead of keeping shadow on the stable card `Surface`.
- Shrinking all normal cards because only the compact viewport needs a smaller tier.
- Putting like/favorite/share controls over quote text.
- Reintroducing a visible interpretation/return action or a whole-card click handler after the interaction was intentionally reduced to horizontal swipes.
- Reusing one list state for both saved segments, which makes switching one
  segment move the other.
- Letting system back leave a note editor while save/delete is in progress.
- Binding `减少此类` to a stale repository index while the pager is still
  settling, or leaving the action enabled on the completion page.
- Reintroducing thumbnails or placeholder image boxes into search, favorite, or liked summaries.
- Showing a licensed archive photo without propagating its source and license evidence to both the background sheet and share flow.
