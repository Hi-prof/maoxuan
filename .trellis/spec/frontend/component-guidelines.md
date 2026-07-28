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
fun InterpretationSheet(
    card: QuoteCard,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- Put `modifier` last with an empty default.
- Hoist state when it affects navigation, persistence, or another component.
- Keep transient presentation state local with a stable `remember` key, for example source expansion keyed by card ID.
- Do not pass DAO entities, serialized DTOs, `Context`, or a ViewModel into reusable display components.
- `CardActions` owns the shared action ordering. `onInterpret` renders as the
  text action immediately left of `read background / return to front` in both
  reader and detail routes.
- `CardActions` exposes an `onNote` callback in both reader and detail routes.
  Like, favorite, note, and share are four accessible `48.dp` icon targets.
  When available width is below `360.dp`, icon and reading actions use two rows;
  wider layouts keep one stable row.

## Card Layout Contract

- Card radius is `8.dp`; actions remain outside the card.
- Quote text uses bundled `QuoteFontFamily`, `FontWeight.Bold`, and `letterSpacing = 0.sp`.
- Normal height font tiers are 34/31/28 sp for lengths `1-32`, `33-60`, and `61-90`.
- When `maxHeight < 480.dp`, compact tiers are 25/22/20 sp for `33-60`, `61-75`, and `76-90`.
- The quote area uses `weight(1f)` so the title/source block always retains space.
- Background images fill the card at approximately 17% alpha and remain visual context, not a separate illustration panel.
- Back content owns vertical scrolling. While flipped, the parent pager must not consume that gesture.

The compact threshold and font tiers are a tested system. Any change must update the 90-character bounds test and screenshots for all target viewports.

### Flip Gesture Contract

- `FlippableQuoteCard` owns an `Orientation.Horizontal` drag gesture. A left or right drag toggles either face; a drag below 22% of card width settles back unless horizontal velocity reaches 900 dp/s.
- Drag distance directly drives `rotationY`. One 180-degree flip spans 72% of card width, and release settles with a non-bouncy medium-low-stiffness spring.
- Switch front/back content only after the rotation crosses 90 degrees, and rotate back content by 180 degrees so text is never mirrored.
- Horizontal dragging must not consume vertical motion. Back content keeps its own `verticalScroll`, and the reader disables `VerticalPager` user scrolling while the back is open.
- Keep the perspective layer's shadow elevation at zero and use the `Surface`'s stable `2.dp` shadow. A transformed dynamic shadow produces an oversized rectangular projection on API 28.
- Release settling must clear transient state in `finally`, so an interrupted animation cannot block later gestures.

Required instrumentation assertions: swipe left to back, swipe right to front, a slow short drag returns to its starting face, and the compact quote/source bounds remain valid. Device review must include an in-motion frame on API 28 plus settled front/back states on API 28 and the latest API.

## Styling

- Use `Ink`, `MutedInk`, `SpiritRed`, `Paper`, `Canvas`, and `Divider` from `ui.theme`.
- Do not add gradients, decorative orbs, heavy black borders, fake-antique textures, nested cards, or full-screen red fills.
- Use Material icons already in the project for actions; do not draw common icons manually.
- Keep dimensions stable so selection, loading, long labels, and state changes do not shift the main card.
- Interpretation uses a Material 3 modal bottom sheet with an `8.dp` top radius,
  a fixed header/close action, and one independently scrolling column. Its
  three sections are unframed; do not wrap them in nested cards.

## Accessibility

- Every actionable icon has a Chinese `contentDescription` or a visible text label.
- Decorative background imagery has `contentDescription = null`.
- Touch targets remain usable at the smallest viewport.
- Preserve the four `48.dp` icon targets when adding text actions. The compact
  action row must fit without clipping at `360.dp` width.
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

## Common Mistakes

- Using a fixed quote box height that clips the source on 360 x 640.
- Letting both the back scroll and `VerticalPager` handle the same drag.
- Adding animated `shadowElevation` to the rotating perspective layer instead of keeping shadow on the stable card `Surface`.
- Shrinking all normal cards because only the compact viewport needs a smaller tier.
- Putting like/favorite/share controls over quote text.
- Reusing one list state for both saved segments, which makes switching one
  segment move the other.
- Letting system back leave a note editor while save/delete is in progress.
