# Personalized Card Recommendation Design

## Architecture

The first version is an offline, content-based recommender with four focused
boundaries:

1. `InterestTaxonomy` maps the existing immutable `QuoteCard.series/themes`
   values to 12 stable user-facing interest IDs.
2. `RecommendationProfileBuilder` converts selected interests and explicit
   local behavior into bounded category weights.
3. `RecommendationRanker` creates a duplicate-free, diversified order with an
   80/20 personalization-to-exploration target.
4. `AppRepository` owns all Room transactions that save preferences or
   feedback and rewrite only the unseen tail of the persisted reading round.

The content YAML and release package stay unchanged. All personal state remains
local and is never logged or sent over the network.

## Taxonomy Contract

`InterestCategory` is an enum with stable ASCII IDs and Chinese display labels:

```kotlin
enum class InterestCategory(val id: String, val label: String) {
    SelfGrowth("self_growth", "自我成长"),
    Learning("learning", "学习求知"),
    LifeWisdom("life_wisdom", "人生智慧"),
    Ideals("ideals", "理想奋斗"),
    Courage("courage", "勇气行动"),
    Practice("practice", "实践求真"),
    Philosophy("philosophy", "哲学思辨"),
    Labor("labor", "劳动创造"),
    Relationships("relationships", "人际关系"),
    PeopleSociety("people_society", "人民社会"),
    History("history", "历史时代"),
    Poetry("poetry", "诗词文学"),
}
```

`InterestTaxonomy.categoriesFor(card)` owns all exact theme and series
matching. One card can match several categories. Unknown themes do not fail
content import and remain eligible for exploration. Unit tests use the bundled
600-card package to prove every selectable category has a meaningful candidate
pool and that all active cards remain rankable.

## Profile And Ranking

Profile weights are category-level and bounded so one long-lived behavior does
not dominate forever:

```text
selected category      +8
liked card categories  +1 each, capped at +3 per category
favorite categories    +2 each, capped at +6 per category
linked-note categories +2 each unique card, capped at +6 per category
reduced categories     -6 each, capped at -12 per category
```

Multiple notes on one card count once. Reads, searches, and quick swipes do not
change the profile. Constants live in one object and tests assert their ordering
rather than scattering numeric values through repository code.

Ranking works without replacement:

1. Map every candidate card to categories and a summed profile score.
2. Create a positive lane (`score > 0`), a neutral lane (`score == 0`), and a
   discouraged lane (`score < 0`).
3. Use injected `Random` to weighted-shuffle the positive lane and normally
   shuffle the other lanes.
4. Emit up to four positive cards, then one exploration card. Exploration uses
   neutral cards before discouraged cards. When a lane is exhausted, fill from
   the remaining lanes.
5. When alternatives exist, avoid three consecutive cards from the same series
   or primary category.

All cards remain in the round exactly once. A skipped onboarding with no
behavioral profile uses the same diversity mixer over a balanced random order.

## Room Schema And Migration

The next Room schema adds:

```kotlin
RecommendationStateEntity(id = 0, onboardingCompleted: Boolean)
InterestPreferenceEntity(categoryId: String)
ReducedCardEntity(cardId: String, createdAt: Long)
ReadingRoundEntity.furthestPosition: Int
```

`ReducedCardEntity` intentionally has no card foreign key. A withdrawal does
not retain a card snapshot merely for recommendation feedback, while restoring
the same stable card ID can reuse the local feedback. Unknown/removed interest
IDs are ignored on read and removed on the next preference save.

The explicit current-to-next migration:

- creates all three recommendation tables;
- adds `furthestPosition` with a non-null default;
- initializes each existing round to the greater of its current position and
  maximum position with a non-null `readAt`;
- inserts singleton `onboardingCompleted = true` for an existing database;
- preserves cards, user state, notes, searches, rounds, positions, and reads.

A newly created current-schema database has no recommendation singleton. That
absence means fresh-install onboarding is required. Saving or skipping writes
the singleton and atomically creates the first ranked round.

## Repository Data Flow

The DAO exposes typed Flows for recommendation state, selected IDs, reduced
card IDs, user card state, notes, cards, and the active round. The repository
projects them into immutable `RecommendationSettings` and `ReaderState` values.

Every settled page updates both `currentPosition` and
`furthestPosition = max(furthestPosition, currentPosition)`. Preference saves,
like/favorite toggles, linked-note create/delete, and reduce feedback call one
transactional `replanUnseenTail` operation:

```text
locked prefix: positions 0..furthestPosition, unchanged
candidate tail: active items after furthestPosition plus newly published cards
rank candidate tail -> rebuild positions -> persist exact order
current position and every existing readAt remain unchanged
```

Content import uses the same planner. Withdrawn cards leave the order; new cards
enter only the unseen ranked tail. Starting a new round ranks all active cards
from the current profile.

For `减少此类`, inserting feedback and replanning commit together. The UI advances
only after success. A failed write keeps the current card and surfaces a short
Chinese error instead of silently losing feedback.

## User Interface

### Fresh Install

After offline content initialization, a fresh user sees `InterestSelectionScreen`
before the normal app shell. It is a focused full-screen form, not a tutorial or
marketing page:

- title `选择你感兴趣的内容`;
- 12 Material `FilterChip` controls in a responsive `FlowRow`;
- selected count with a hard maximum of 5;
- primary `开始阅读` command and secondary `暂时跳过` command.

The screen supports 360 x 640 dp with vertical scrolling and stable button
placement. Existing migrated users never see it.

### Interest Settings

`MineScreen` adds an `兴趣偏好` row that opens a dedicated route. The route
reuses the selection content, saves 0..5 selected IDs, and offers
`清除“减少此类”记录` only when feedback exists. Clearing requires a
confirmation dialog and does not change selected interests.

### Reader Feedback

The card action row remains unchanged. `ReaderHeader` adds a `MoreVert` icon
beside search; its menu contains the visible command `减少此类`. The menu is
disabled when no normal card is current. Successful feedback advances to the
next card; on the final card it reaches the existing completion state.

All actions have Chinese labels/content descriptions. Screens receive immutable
state and callbacks and never access Room directly.

## Error Handling And Privacy

- Preference validation rejects more than 5 IDs and ignores no unknown values
  on writes; invalid IDs are a programmer error covered by tests.
- Empty candidate sets produce the existing empty reader state.
- Recommendation failures are local and do not affect content import integrity.
- No recommendation state, current card, profile weight, or round order is
  logged. No new network permission or request is introduced.

## Testing

- JVM: taxonomy pools, signal caps, no-profile cold start, 80/20 mixing,
  discouraged ordering, no duplicates, deterministic injected randomness,
  diversity fallback, and locked-prefix preservation.
- Room migration: all existing state survives; existing databases skip
  onboarding; fresh databases require it; `furthestPosition` is reconstructed.
- Repository instrumentation: skip/save onboarding, preference replacement,
  feedback/clear, every explicit positive signal, mid-round tail replanning,
  restart, update additions, withdrawals, completion, and new rounds.
- Compose instrumentation: 12 chips, 5-selection limit, scrolling at 360 x 640,
  existing-user bypass, Mine route, clear confirmation, reader overflow, and
  successful advance without action-row regression.
- Full gate: Debug JVM tests, lint, Debug APK, Android test compilation, focused
  API 28/latest instrumentation, Trellis validation, and diff checks.

## Compatibility, Rollback, And Release

The source release becomes `1.8.0 / versionCode 10` and includes the already
implemented in-app updater. The app release workflow remains non-latest so the
repository latest Release continues to serve content updates.

Rollback before release reverts the feature and schema bump. After users open
the migrated database, rolling back to an APK that only understands the older
schema is unsupported; ship a forward-fix with a higher version instead. The
recommendation tables contain only disposable local preference/feedback data,
but migration must never destroy unrelated personal data.
