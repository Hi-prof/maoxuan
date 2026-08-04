# Series Filtering Evidence

## Repository Facts

- Every `QuoteCard`, `CardEntity`, and content DTO already carries a required `series: String` value.
- The installed 600-card package currently contains four series: `毛泽东选集`, `毛泽东诗词`, `名人名言`, and `马原思考`.
- Search, favorites, likes, and notes are projected from `allCards` / `activeCards`, while the main reader is projected from persisted `reading_round_items`.
- `AppRepository.rankedActiveCardIds()` is the shared candidate source for initial round creation, new rounds, preference replanning, and content-update reconciliation.
- `ReadingRoundPlanner.reconcile()` already removes items absent from `rankedActiveCardIds`, preserves the nearest eligible anchor, and rebuilds the remaining positions.
- Interest preferences use a normalized Room table and are replaced inside the same transaction that replans the active round.

## Chosen Boundary

Treat selected series as an allowlist before recommendation ranking. An empty allowlist is the explicit `全部内容` state. Apply it only to `rankedActiveCardIds()` so search and personal saved content remain unaffected.

## Persistence Choice

Add a normalized `content_series_preferences(series TEXT PRIMARY KEY)` table. This matches existing Room ownership, supports multiple dynamic series, and lets preference replacement and reading-round reconciliation commit atomically. A delimiter-encoded singleton column and DataStore were rejected because they add parsing or cross-store consistency costs without product benefit.

## Compatibility Notes

- Migration `6 -> 7` creates the empty table, so every existing user continues with unrestricted content.
- A future series enters unrestricted feeds automatically. It does not enter a non-empty allowlist until selected.
- Selected series values are preserved even if a content update temporarily has no active cards in that series; this avoids silently broadening the user's filter.

