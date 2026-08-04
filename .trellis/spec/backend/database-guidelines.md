# Database Guidelines

## Overview

Room is the device source of truth for installed content, user state, and reading rounds. `XinghuoDatabase` exposes one `AppDao`; cross-table writes are coordinated by `AppRepository` with `database.withTransaction`.

## Table Contracts

- `cards`: stable UUID primary key, monotonic revision, content fields, two
  non-null interpretation columns, required historical event/background/story,
  image ID, and `active|withdrawn` availability.
- `card_sources`: ordered by `(cardId, position)` and replaced as a unit with a card revision.
- `image_assets`: stable logical image ID mapped to immutable SHA-256 content and an internal path.
- `user_card_state`: independent like/favorite flags and timestamps; it must survive content revisions.
- `reading_rounds`: one latest `active|completed` round; `currentPosition` is the
  settled page and `furthestPosition` is the greatest page the user has reached.
  Prior rounds become `archived` only when the user starts a new round.
- `reading_round_items`: persisted stable order with a unique `(roundId, cardId)` index and idempotent `readAt`.
- `withdrawals`: highest trusted tombstone revision for each withdrawn card ID.
- `content_state`: singleton row with primary key `0`.
- `search_history`: auto-generated ID, `NOCASE` unique keyword, and submitted time; ordered by submitted time then ID descending and capped at 10 rows.
- `notes`: auto-generated ID, nullable card ID, nullable title, non-empty body,
  and created/updated times; ordered by updated time then ID descending.
- `recommendation_state`: singleton row `id = 0`; row absence means a new
  installation still requires interest onboarding.
- `interest_preferences`: zero to five stable recommendation category IDs,
  replaced as a set when the user saves preferences.
- `content_series_preferences`: a normalized set of selected card `series`
  values. No rows means unrestricted content; any rows form the reader's hard
  allowlist.
- `reduced_cards`: one local negative-feedback row per stable card ID with a
  creation timestamp; it intentionally has no card foreign key.

## Transaction Pattern

Package import order is fixed:

1. Validate package bytes and decoded image metadata.
2. Write immutable content-addressed files.
3. In one Room transaction, apply images/cards/sources/withdrawals, reconcile the reading round, prune database image rows, and update `content_state`.
4. After commit, delete no-longer-referenced managed files.
5. In `finally`, clean orphan files created before a failed transaction.

User state and notes must never be cascade-deleted by a normal content
revision. A withdrawn card snapshot is deleted only when `liked` and
`favorited` are both false and no note references the card.

Initialization reads the bundled package without networking. It imports when
the database is empty or when the bundled content version is newer, reusing the
same validated atomic transaction. It never replaces a higher installed
version with the bundle.

Saving a search keyword replaces a case-insensitive duplicate and trims older rows in one transaction. Database version 2 adds this table through an explicit migration; existing content, personal state, and reading rounds must remain unchanged.

`markRead` is idempotent:

```sql
UPDATE reading_round_items
SET readAt = COALESCE(readAt, :readAt)
WHERE roundId = :roundId AND cardId = :cardId
```

## Query Patterns

- Expose observable data as `Flow` from DAO and combine it in the repository.
- Use parameterized Room queries only.
- Map `Entity -> domain model` in the repository; never expose entities to composables.
- Preserve source ordering explicitly with `position`.
- Keep active-card filtering distinct from liked/favorited snapshot visibility.
- Keep note observation independent from card observation. Join a note's
  nullable `cardId` to the existing domain-card flow in the UI state instead of
  duplicating quote snapshots inside the note row.

## Migrations

- Increment the Room schema version for every persisted schema change.
- Add and test an explicit migration before shipping an update when user data
  must be preserved.
- Destructive fallback is allowed only for a documented release where the user
  has explicitly accepted loss of all local data. Version 5 is such a one-user
  exception; schema `4 -> 5` rebuilds from the bundled content package and has
  no data-preserving migration test.
- Export/update schemas when migration tests are introduced.
- Database version 3 adds `interpretationCoreMeaning`,
  `interpretationKeyPoint`, and `interpretationContemporaryRelevance` through
  `MIGRATION_2_3`. The full registered chain is `1 -> 2 -> 3`.
- Migration defaults are `TEXT NOT NULL DEFAULT ''` only to make the structural
  migration valid. Startup must immediately import newer bundled schema-2
  content before those values are treated as publishable interpretation.
- Database version 4 creates `notes` plus non-unique indexes on `cardId` and
  `(updatedAt, id)` through `MIGRATION_3_4`. The full registered chain is
  `1 -> 2 -> 3 -> 4`.
- Database version 5 replaces the three old interpretation columns with
  `interpretationInspiration` and `interpretationExplanation`, adds required
  `historicalEvent`, and removes `contextExcerpt`. App `1.3.0` uses destructive
  fallback from prior schemas and then imports the bundled schema-3 content.
- Database version 6 adds the recommendation tables and
  `reading_rounds.furthestPosition` through `MIGRATION_5_6`. The migration
  preserves every existing table, reconstructs the furthest position from
  `currentPosition` and non-null `readAt` rows, and writes
  `onboardingCompleted = true` so upgrades never show fresh-install onboarding.
- Database version 7 adds `content_series_preferences` through
  `MIGRATION_6_7`. The empty migrated table preserves the prior unrestricted
  reader behavior and every existing personal-data row.

## Scenario: Local Recommendation State And Unseen-Tail Replanning

### 1. Scope / Trigger

- Trigger: schema 6 persists local interest preferences and negative feedback,
  while explicit recommendation signals can reorder an active reading round.

### 2. Signatures

```kotlin
data class RecommendationStateEntity(
    @PrimaryKey val id: Int = 0,
    val onboardingCompleted: Boolean,
)

data class InterestPreferenceEntity(@PrimaryKey val categoryId: String)
data class ReducedCardEntity(@PrimaryKey val cardId: String, val createdAt: Long)

suspend fun completeInterestOnboarding(selectedIds: Set<String>)
suspend fun saveRecommendationPreferences(
    interestIds: Set<String>,
    selectedSeries: Set<String>,
)
suspend fun reduceSimilarContent(cardId: String)
suspend fun clearReducedContentFeedback()
```

### 3. Contracts

- A current-schema database with no `recommendation_state` row is a fresh
  install. Saving or skipping onboarding writes the singleton and creates the
  first reading round in the same transaction.
- A migrated schema-5 database receives `recommendation_state(0, true)` and
  keeps its existing round, cards, sources, content state, likes, favorites,
  notes, search history, positions, and `readAt` values.
- Interest-only and feedback writes replan only positions strictly greater than
  `furthestPosition`. A changed content-series allowlist is the explicit
  exception: disallowed cards leave the entire round, while the nearest
  eligible card becomes current and retained `readAt` values remain attached to
  their original card IDs.
- The replanned tail contains each active candidate once. Newly published cards
  may enter that tail; withdrawn cards leave it. All recommendation data stays
  on device and must not be logged or sent over the network.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| More than five category IDs | Reject before writing with `最多选择 5 个兴趣标签` |
| Any unknown category ID | Reject before writing with `兴趣标签无效` |
| Reduced card ID does not exist | Reject with `卡片不存在`; write no feedback |
| Reduced card ID is withdrawn | Reject with `卡片已下架`; write no feedback |
| Empty interest set | Persist it as a valid balanced-feed preference |
| Replanning has no candidates | Keep a valid empty tail and unchanged prefix |
| Coroutine is cancelled | Propagate cancellation; do not report a user error |

### 5. Good/Base/Bad Cases

- Good: changing interests midway through a round leaves visited positions and
  timestamps byte-for-byte stable while the unseen tail receives a new order.
- Base: skipping onboarding saves zero selected categories and creates a
  balanced, duplicate-free first round.
- Bad: rewriting the complete round after a like moves a previously seen card,
  breaks backtracking, and can assign a `readAt` value to the wrong position.

### 6. Tests Required

- Pure JVM: taxonomy coverage for all bundled cards, bounded signal weights,
  4:1 personalized/exploration mixing, diversity fallback, determinism, and no
  duplicate or missing card IDs.
- Migration: seed a complete schema-5 database, migrate to 6, validate the
  schema and singleton, and assert all prior values plus reconstructed
  `furthestPosition` on API 28 and the latest configured API.
- Repository: assert onboarding save/skip, invalid IDs, preference replacement,
  reduce/clear, restart persistence, explicit positive signals, updates,
  withdrawals, completion, and locked-prefix preservation.

### 7. Wrong vs Correct

#### Wrong

```kotlin
dao.replaceRoundItems(round.id, rank(allActiveCards))
```

#### Correct

```kotlin
val locked = existingItems.filter { it.position <= round.furthestPosition }
val rankedTail = rank(activeCandidatesAfter(round.furthestPosition))
dao.replaceRoundItems(round.id, locked + rankedTail)
```

## Scenario: Content-Series Reader Allowlist

### 1. Scope / Trigger

- Trigger: schema 7 lets the user restrict the main reader to one or more
  installed card series without hiding search results or personal saved data.

### 2. Signatures

```kotlin
data class ContentSeriesPreferenceEntity(@PrimaryKey val series: String)

data class RecommendationSettings(
    val availableSeries: List<String>,
    val selectedSeries: Set<String>,
)

suspend fun saveRecommendationPreferences(
    interestIds: Set<String>,
    selectedSeries: Set<String>,
)
```

### 3. Contracts

- Room is the source of truth. Empty `selectedSeries` means `全部内容`; a
  non-empty set is a hard allowlist applied before `RecommendationRanker`.
- Saving interest and series sets plus replanning the active round is one Room
  transaction. A failed write changes neither preferences nor round items.
- Available UI options are active installed series plus saved stale selections.
  New installed series enters an unrestricted reader automatically but cannot
  bypass a non-empty allowlist.
- Only `rankedActiveCardIds()` is filtered. Search, favorites, likes, notes, and
  their recommendation signals continue to use all active/preserved cards.
- A series restriction may remove visited round items. The planner keeps the
  same current card when eligible; otherwise it selects the nearest eligible
  card and preserves retained cards' `readAt` values.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| Empty selected-series set | Persist unrestricted `全部内容` |
| One or more non-blank series | Persist exact set and filter the reader |
| Blank or surrounding-whitespace series | Reject with `内容范围无效`; write nothing |
| Saved series has no active cards | Keep the selection and expose an empty reader |
| Content update adds an unselected series | Show it as an option; keep it out of the filtered reader |
| Current card becomes disallowed | Move to the nearest eligible retained card |

### 5. Good/Base/Bad Cases

- Good: selecting `毛泽东选集` removes every other series from the active round,
  while a favorited `名人名言` card remains visible under 收藏.
- Base: an upgraded schema-6 user has no series rows and sees all content.
- Bad: deleting a stale final preference broadens the feed to all content without
  an explicit `全部内容` choice.

### 6. Tests Required

- Migration: validate `6 -> 7`, assert the new table is empty, and reassert
  cards, personal state, notes, interests, feedback, round position, and reads.
- Repository: assert unrestricted, single-series, multi-series, restart, new
  round, invalid-value atomicity, and unaffected search/favorite behavior.
- Planner: assert a disallowed locked prefix is removed and the nearest eligible
  card becomes current.
- Compose: assert `全部内容`, multi-select, final-selection protection, exact save
  payload, loading disablement, and scrolling at `360 x 640 dp`.

### 7. Wrong vs Correct

#### Wrong

```kotlin
RecommendationRanker.rank(allActiveCards, profile, random)
    .filter { it.series in selectedSeries }
```

This can distort ranking/exploration because ranking decisions include
disallowed candidates.

#### Correct

```kotlin
val candidates = if (selectedSeries.isEmpty()) allActiveCards else {
    allActiveCards.filter { it.series in selectedSeries }
}
RecommendationRanker.rank(candidates, profile, random)
```

## Scenario: Personal Notes And Withdrawal Retention

### 1. Scope / Trigger

- Trigger: adding standalone notes, multiple card-linked notes, and a new Room
  schema changes both the persistence contract and withdrawn-card lifetime.

### 2. Signatures

```kotlin
data class NoteEntity(
    val id: Long = 0,
    val cardId: String? = null,
    val title: String? = null,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
)

suspend fun saveNote(
    noteId: Long?,
    cardId: String?,
    title: String,
    body: String,
): Long

suspend fun deleteNote(noteId: Long)
```

`MIGRATION_3_4` only creates `notes`, `index_notes_cardId`, and
`index_notes_updatedAt_id`; it must not rewrite existing tables.

### 3. Contracts

- `cardId = null` creates a standalone note; a non-null ID must name an
  existing card when a note is created.
- Trim title and body before persistence. Store a blank trimmed title as null;
  reject a blank trimmed body.
- Editing preserves `createdAt` and the original `cardId`, changes `updatedAt`,
  and rejects attempts to change the association.
- Observe notes by `updatedAt DESC, id DESC`, allowing multiple rows for one
  card.
- Notes remain local device data and never enter a content package, search
  history, share image, log payload, or network request.
- A withdrawal keeps the last trusted card snapshot while any note references
  it. Deleting the last linked note removes that snapshot only when the card is
  also neither liked nor favorited; image pruning occurs in the same repository
  transaction and file cleanup follows commit.

### 4. Validation & Error Matrix

| Condition | Required result |
| --- | --- |
| New linked note references no card | Reject with `关联卡片不存在`; write no row |
| Trimmed body is empty | Reject with `笔记正文不能为空`; write no row |
| Edited note ID does not exist | Reject with `笔记不存在` |
| Edit supplies a different `cardId` | Reject with `不能修改笔记关联的卡片` |
| Delete supplies a missing note ID | Succeed idempotently; change nothing |
| Withdrawn card still has any note | Keep its card/source/image snapshot |
| Last note deleted but like/favorite remains | Keep the withdrawn snapshot |
| Last note deleted and no user state remains | Delete snapshot and prune unreferenced image |

### 5. Good/Base/Bad Cases

- Good: two notes reference one card, the card is withdrawn, and deleting only
  one note keeps the snapshot.
- Base: a standalone note has `cardId = null`, a nullable title, and a trimmed
  non-empty body.
- Bad: storing all card notes in a serialized card column prevents independent
  IDs, ordering, updates, and migration-safe cleanup.

### 6. Tests Required

- Migration: create a version-3 database with user/search/round state, migrate
  to 4, validate schema, insert a note, and assert prior rows are unchanged.
- Repository: assert standalone creation, multiple notes per card,
  `updatedAt DESC, id DESC` ordering, trim/null behavior, update, and deletion.
- Withdrawal: assert retention with two notes, retention after the first delete,
  and cleanup only after the final note and final like/favorite reference are gone.
- Process test: force-stop/relaunch and assert note bodies and card associations
  remain visible.

### 7. Wrong vs Correct

#### Wrong

```kotlin
if (!state.liked && !state.favorited) dao.deleteCard(cardId)
```

#### Correct

```kotlin
if (!state.liked && !state.favorited && dao.countNotesForCard(cardId) == 0) {
    dao.deleteCard(cardId)
}
```

## Common Mistakes

- Deleting a remotely absent card without an explicit tombstone.
- Changing card content at the same revision.
- Reusing an image ID for different bytes.
- Moving the database transaction before asset validation/writes, which can create references to missing files.
- Rebuilding a reading round from a random seed instead of persisting its exact order.
- Replanning positions at or before `furthestPosition` for ranking-only signals.
  Only an explicit content-series restriction may remove disallowed history,
  and retained timestamps must stay attached to card IDs.
- Raising the schema version without testing search history, round position,
  read timestamps, likes, favorites, and notes across the migration.
- Adding a foreign-key cascade from notes to cards, which would erase personal
  writing when remote content is withdrawn.
