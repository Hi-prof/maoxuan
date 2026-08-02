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
suspend fun saveInterestPreferences(selectedIds: Set<String>)
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
- Preference and feedback writes replan only positions strictly greater than
  `furthestPosition`. Positions `0..furthestPosition`, `currentPosition`, and
  every existing `readAt` remain unchanged.
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
| Empty preference set | Persist it as a valid balanced-feed preference |
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
- Replanning positions at or before `furthestPosition`, which changes history
  and can attach settled-page effects to the wrong card.
- Raising the schema version without testing search history, round position,
  read timestamps, likes, favorites, and notes across the migration.
- Adding a foreign-key cascade from notes to cards, which would erase personal
  writing when remote content is withdrawn.
