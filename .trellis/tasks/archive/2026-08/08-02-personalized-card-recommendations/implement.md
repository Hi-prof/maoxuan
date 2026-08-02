# Personalized Card Recommendation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` for inline
> execution. Track every checkbox and keep unrelated working-tree edits intact.

**Goal:** Upgrade the existing offline reading stream with explainable local
recommendations, fresh-install interest selection, explicit negative feedback,
and editable preferences.

**Architecture:** Pure Kotlin taxonomy/profile/ranking units produce a complete
ordered card list. Room owns onboarding, preferences, reduced-card feedback,
and the exact reading order; the repository transactionally replans only the
unseen tail. Compose renders immutable state and explicit commands.

**Tech Stack:** Kotlin 2, Android API 28-35, Room, Kotlin Flow/coroutines,
Jetpack Compose Material 3, JUnit, AndroidX Room/Compose instrumentation.

## Global Constraints

- Keep all recommendation and behavior data on device and out of logs/network.
- Preserve the existing reading route, exact locked history, single-round
  uniqueness, backtracking, completion, content updates, and personal data.
- Expose exactly 12 stable interests, allow 0..5 selections, and onboard fresh
  installs only.
- Reads, searches, and quick swipes never change preference weights.
- Keep the existing card action row unchanged at the tested 320 dp width.
- Work with the current app-update changes in shared files; never revert them.
- Release as `1.8.0 / versionCode 10` only after the complete quality gate.

---

### Task 1: Pure Taxonomy, Profile, And Ranking Engine

**Files:**

- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/recommendation/InterestCategory.kt`
- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/recommendation/InterestTaxonomy.kt`
- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/recommendation/RecommendationProfile.kt`
- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/recommendation/RecommendationRanker.kt`
- Create: `app/src/test/java/com/xuhuangbin/xinghuozhaidu/domain/recommendation/InterestTaxonomyTest.kt`
- Create: `app/src/test/java/com/xuhuangbin/xinghuozhaidu/domain/recommendation/RecommendationRankerTest.kt`

**Interfaces:**

```kotlin
fun InterestTaxonomy.categoriesFor(card: QuoteCard): Set<InterestCategory>
fun RecommendationProfileBuilder.build(input: RecommendationSignals): RecommendationProfile
fun RecommendationRanker.rank(
    cards: List<QuoteCard>,
    profile: RecommendationProfile,
    random: Random,
): List<String>
```

- [x] Write failing tests for all 12 IDs/labels, representative raw-theme and
  series mappings, unknown-theme eligibility, and bundled-content pool coverage.
- [x] Implement the enum and one exact taxonomy owner; run the focused taxonomy
  test and confirm it passes.
- [x] Write failing tests for selected/like/favorite/note/reduced ordering,
  per-category caps, multiple-note deduplication, empty-profile cold start,
  4:1 exploration mixing, negative fallback, diversity, determinism, and no
  duplicate/missing IDs.
- [x] Implement immutable signals/profile plus lane ranking with injected
  `Random`; run both focused JVM suites.

### Task 2: Data-Preserving Room Schema

**Files:**

- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/Entities.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/AppDao.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/XinghuoDatabase.kt`
- Generate: `app/schemas/com.xuhuangbin.xinghuozhaidu.data.local.XinghuoDatabase/6.json`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/data/local/XinghuoDatabaseMigrationInstrumentedTest.kt`

**Interfaces:**

```kotlin
RecommendationStateEntity(id: Int = 0, onboardingCompleted: Boolean)
InterestPreferenceEntity(categoryId: String)
ReducedCardEntity(cardId: String, createdAt: Long)
suspend fun replaceInterestPreferences(categoryIds: Set<String>)
suspend fun updateRoundPosition(roundId: Long, position: Int)
```

- [x] Write a failing `5 -> 6` migration test that seeds cards, user state,
  notes, search, round position/items/reads and validates every value afterward.
- [x] Add tests that migrated databases receive completed onboarding and a
  reconstructed furthest position while a fresh schema has no singleton.
- [x] Add entities, DAO queries/transactions, `furthestPosition`, schema version
  6, and `MIGRATION_5_6`; register the migration and generate the schema.
- [x] Run migration instrumentation on the available API 28/latest devices.

### Task 3: Recommendation-Aware Reading Repository

**Files:**

- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/ReadingRoundPlanner.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/AppRepository.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/model/Models.kt`
- Modify: `app/src/test/java/com/xuhuangbin/xinghuozhaidu/data/ReadingRoundPlannerTest.kt`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/data/AppRepositoryInstrumentedTest.kt`

**Interfaces:**

```kotlin
data class RecommendationSettings(
    val requiresOnboarding: Boolean,
    val selected: Set<InterestCategory>,
    val reducedCount: Int,
)
suspend fun completeInterestOnboarding(selectedIds: Set<String>)
suspend fun saveInterestPreferences(selectedIds: Set<String>)
suspend fun reduceSimilarContent(cardId: String)
suspend fun clearReducedContentFeedback()
```

- [x] Write failing planner tests proving a locked prefix and current index stay
  stable while only the tail is replaced, added cards join the tail, and every
  `readAt` survives.
- [x] Implement the tail planner and use it for create/reconcile/new-round paths.
- [x] Write failing repository tests for fresh/migrated initialization,
  skip/save, 0..5 validation, reduce/clear, positive signals, note deduplication,
  restart, content add/withdraw, and completion behavior.
- [x] Combine Room sources into signals, delay fresh first-round creation, and
  transactionally replan after every explicit signal change; run focused tests.

### Task 4: Onboarding, Settings, And Reader Feedback UI

**Files:**

- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/interests/InterestSelectionScreen.kt`
- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/interests/InterestPreferencesScreen.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/XinghuoApp.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/saved/SavedScreens.kt`
- Create: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/interests/InterestSelectionInstrumentedTest.kt`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/components/QuoteCardInstrumentedTest.kt`

**Interfaces:**

```kotlin
fun InterestSelectionScreen(
    selected: Set<InterestCategory>,
    onSelectionChange: (Set<InterestCategory>) -> Unit,
    onContinue: () -> Unit,
    onSkip: (() -> Unit)?,
)
```

- [x] Write failing Compose tests for 12 labels, maximum 5, deselection,
  onboarding skip/save, 360 x 640 scrolling, and accessibility labels.
- [x] Implement the reusable chip content, fresh-install shell gate, dedicated
  Mine route, save operation, reduced-count display, and clear confirmation.
- [x] Add a reader-header `MoreVert` menu with `减少此类`; keep `CardActions`
  unchanged and advance only from the repository success callback.
- [x] Add ViewModel state/intents without disturbing the app-update state
  machine; run UI tests and Android test compilation.

### Task 5: Specs, Version, And Release Integration

**Files:**

- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/app-release.yml`
- Modify: `README.md`
- Modify: `.trellis/spec/backend/database-guidelines.md`
- Modify: `.trellis/spec/backend/quality-guidelines.md`
- Modify: `.trellis/spec/frontend/component-guidelines.md`
- Modify: `.trellis/spec/frontend/state-management.md`
- Modify: `.trellis/spec/frontend/quality-guidelines.md`

- [x] Update executable specs for recommendation state, migration, round-tail
  invariants, UI placement, privacy, and required regression coverage.
- [x] Set `versionName = 1.8.0`, `versionCode = 10`, update workflow validation
  and README, and confirm content-release `latest` behavior remains unchanged.
- [x] Run `git diff --check`, conflict-marker scan, Trellis task validation, and
  verify unrelated local files are not staged.

### Task 6: Quality Gate And GitHub Delivery

- [x] Run `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon` with JDK 17.
- [x] Run `./gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon` and
  focused recommendation/database/repository/UI instrumentation on API 28 and
  the latest available API; document any environment-only gap.
- [x] Build `:app:assemblePersonal` locally for installation verification; use
  the GitHub release workflow for the formally signed Release APK.
- [x] Commit only reviewed app-update/recommendation/release files in coherent
  commits, archive completed Trellis tasks, and push `main`.
- [x] Create and push annotated tag `app-v1.8.0`, monitor the GitHub Actions run,
  and verify the non-latest Release contains the signed APK and `.sha256` asset.
