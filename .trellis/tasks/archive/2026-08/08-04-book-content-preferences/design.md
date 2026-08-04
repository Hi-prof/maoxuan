# Book Content Preferences Design

## Architecture

The feature extends the existing offline recommendation settings boundary. Room stores a normalized series allowlist, `AppRepository` exposes available and selected series with the existing recommendation settings, and the main reading candidate pipeline filters by series before ranking. Compose remains stateless with respect to persistence and saves interest and series preferences through one ViewModel callback.

The content contract remains unchanged because every card already has a required stable `series` value.

## Domain Contract

`RecommendationSettings` gains:

```kotlin
val availableSeries: List<String> = emptyList()
val selectedSeries: Set<String> = emptySet()
```

An empty `selectedSeries` means `全部内容`. A non-empty set is a hard allowlist. Interest categories remain recommendation weights and do not change this filter.

Available options are the union of active installed series and saved selections, ordered deterministically. Including saved values prevents a content update from silently removing a user's choice or turning an empty candidate result into unrestricted content.

## Room And Migration

Schema version 7 adds:

```kotlin
@Entity(tableName = "content_series_preferences")
data class ContentSeriesPreferenceEntity(
    @PrimaryKey val series: String,
)
```

The DAO observes, reads, inserts, and clears these rows. `MIGRATION_6_7` creates the table without seed data. Therefore upgraded and fresh databases both default to unrestricted content, while all cards, reading progress, likes, favorites, notes, searches, interests, and feedback survive unchanged.

`saveRecommendationPreferences(interestIds, selectedSeries)` validates at most five known interest IDs and non-blank series values, then replaces both normalized preference sets and replans the active round in one Room transaction.

## Reader Data Flow

`rankedActiveCardIds()` loads active cards and selected series. Before profile construction and ranking it applies:

```kotlin
val candidates = if (selectedSeries.isEmpty()) {
    activeCards
} else {
    activeCards.filter { it.series in selectedSeries }
}
```

Profile signals may still be derived from all active liked/favorited/noted cards, but only filtered candidates can be returned. This keeps personal history intact while enforcing the selected reading scope.

The same method already feeds first-round creation, new rounds, preference saves, behavior replanning, and content-update reconciliation. `ReadingRoundPlanner` removes disallowed items, keeps the nearest still-eligible current item when possible, and positions the reader at the nearest eligible anchor otherwise. If no candidates exist, the persisted round has no items and the existing empty reader state is shown.

Search uses `activeCards` directly and favorites, likes, and notes use personal collections, so these screens remain unfiltered.

## User Interface

The existing settings destination becomes `阅读偏好`. It retains the interest chips and adds an unframed `内容范围` section above them:

- `全部内容` is selected when the saved set is empty; tapping it clears the allowlist.
- Series options use Material `FilterChip` controls in a responsive `FlowRow`.
- Tapping a series while `全部内容` is active begins a concrete selection.
- Multiple concrete series may be selected.
- The final concrete selection cannot be deselected; the user taps `全部内容` to remove the restriction.
- The existing save button writes interest and series preferences together and retains its loading/error behavior.

Fresh-install interest onboarding stays unchanged and starts with all content. The `我的` summary reports either `全部内容` or the number of selected series and opens the same preference route.

## Failure And Edge Handling

- Blank series values are rejected before persistence.
- A stale saved series remains visible in settings and produces no cards if it is the only selection; the filter never silently broadens.
- New installed series appears as an option. It enters the reader automatically only under `全部内容`.
- A failed transaction keeps both prior preference sets and the prior reading plan.
- No preference or reading history is logged or transmitted.

## Testing

- Room migration verifies version 6 data survives and the new table is empty.
- Repository instrumentation verifies unrestricted default, one-series and multi-series filtering, atomic replacement, restart persistence, new rounds, and unaffected search/personal state.
- Planner JVM tests verify nearest eligible anchoring when the current card is filtered out.
- Compose instrumentation verifies `全部内容`, multi-select, last-selection protection, save payload, loading behavior, and compact viewport scrolling.
- Full verification runs JVM tests, Android Lint, Debug assembly, Android test compilation, Trellis validation, and relevant instrumentation where an emulator is available.

## Rollback

Before release, revert the schema and feature together. After a version-7 database has shipped, rollback to an older APK is unsupported; deliver a forward migration instead. The new table contains only disposable local scope preferences, but rollback must never delete unrelated personal state.

