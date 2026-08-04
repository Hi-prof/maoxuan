# Book Content Preferences Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use inline execution for this task and track every checkbox. Do not dispatch sub-agents for this worktree.

**Goal:** Let users choose one or more installed content series in reading preferences and hard-filter the main reading feed without hiding search or saved personal content.

**Architecture:** Persist a normalized Room allowlist, expose it through `RecommendationSettings`, filter `rankedActiveCardIds()` before the existing recommendation ranker, and extend the existing Compose preference picker. Saving both preference types and replanning the active round is one transaction.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room, Kotlin Flow, JUnit, AndroidX instrumentation.

## Global Constraints

- Empty selected-series rows mean `全部内容`; a non-empty set is a hard allowlist.
- Only the main reading stream is filtered. Search, favorites, likes, and notes remain complete.
- Do not change content YAML, content package DTOs, or static release assets.
- Preserve every existing personal-data table and reading timestamp through migration.
- Keep UI usable at the project minimum viewport of `360 x 640 dp`.

---

### Task 1: Persist Content-Series Preferences

**Files:**
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/Entities.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/AppDao.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/XinghuoDatabase.kt`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/data/local/XinghuoDatabaseMigrationInstrumentedTest.kt`
- Generate: `app/schemas/com.xuhuangbin.xinghuozhaidu.data.local.XinghuoDatabase/7.json`

**Interfaces:**
- Produces: `ContentSeriesPreferenceEntity(series: String)`.
- Produces: DAO observe/get/insert/clear functions for content-series preferences.
- Produces: `XinghuoDatabase.MIGRATION_6_7` and schema version 7.

- [x] Add a migration test that seeds a version-6 database, runs `MIGRATION_6_7`, verifies `content_series_preferences` is empty, and rechecks representative cards, user state, notes, interest preferences, reduced feedback, and round progress.
- [x] Run `./gradlew.bat :app:compileDebugAndroidTestKotlin` and confirm the test first fails because schema 7 and the migration do not exist.
- [x] Add the entity and DAO methods:

```kotlin
fun observeContentSeriesPreferences(): Flow<List<ContentSeriesPreferenceEntity>>
suspend fun getContentSeriesPreferences(): List<ContentSeriesPreferenceEntity>
suspend fun insertContentSeriesPreferences(values: List<ContentSeriesPreferenceEntity>)
suspend fun clearContentSeriesPreferences()
```

- [x] Register the entity, bump Room to version 7, create `MIGRATION_6_7`, and add it to `Room.databaseBuilder(...).addMigrations(...)`.
- [x] Run `./gradlew.bat :app:kspDebugKotlin :app:compileDebugAndroidTestKotlin` to generate and compile schema 7, then verify the migration test compiles.

### Task 2: Enforce The Reader Allowlist

**Files:**
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/model/Models.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/AppRepository.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/MainViewModel.kt`
- Modify: `app/src/test/java/com/xuhuangbin/xinghuozhaidu/data/ReadingRoundPlannerTest.kt`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/data/AppRepositoryInstrumentedTest.kt`

**Interfaces:**
- Produces: `RecommendationSettings.availableSeries: List<String>` and `selectedSeries: Set<String>`.
- Produces: `AppRepository.saveRecommendationPreferences(interestIds: Set<String>, selectedSeries: Set<String>)`.
- Produces: `MainViewModel.saveRecommendationPreferences(selectedInterests, selectedSeries, onSaved)`.

- [x] Add Repository tests for default all-series behavior, one-series filtering, multi-series filtering, replacement persistence after repository recreation, new-round enforcement, and unchanged search/favorite/like/note projections.
- [x] Add a planner test where the current item is absent from ranked IDs and assert the nearest eligible card becomes current without preserving disallowed locked items.
- [x] Run focused test compilation/JVM tests and confirm new assertions fail before implementation.
- [x] Extend `recommendationSettings` by combining saved series rows with active installed series, using their union for deterministic options.
- [x] Replace the preference save operation so interest rows, series rows, onboarding state, and round replanning commit in one transaction.
- [x] Filter active candidates at the beginning of `rankedActiveCardIds()` and leave `search`, `favorites`, `liked`, and `notes` unchanged.
- [x] Update the ViewModel callback to pass both selected sets and preserve the current recommendation operation state/error handling.
- [x] Run `./gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` and confirm the focused tests compile and JVM tests pass.

### Task 3: Add Content Range To Reading Preferences

**Files:**
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/interests/InterestSelectionScreen.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/interests/InterestPreferencesScreen.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/saved/SavedScreens.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/XinghuoApp.kt`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/interests/InterestSelectionInstrumentedTest.kt`

**Interfaces:**
- Consumes: available/selected series from `RecommendationSettings`.
- Produces: `InterestPreferencesScreen.onSave: (Set<InterestCategory>, Set<String>) -> Unit`.

- [x] Add Compose tests that render available series, select one and multiple series, switch back to `全部内容`, protect the last concrete selection, and assert the exact save payload.
- [x] Add UI parameters for available/selected series while leaving fresh-install onboarding behavior and callback unchanged.
- [x] Render `内容范围` and `兴趣标签` as separate unframed sections with responsive `FilterChip` rows inside the existing scroll container.
- [x] Update the preference title/summary and route wiring to save both selected sets and display `全部内容` or the concrete series count.
- [x] Run `./gradlew.bat :app:compileDebugAndroidTestKotlin :app:assembleDebug` and confirm UI/test compilation succeeds.

### Task 4: Full Quality Gate And Documentation

**Files:**
- Modify if behavior changes need explanation: `README.md`
- Review: `.trellis/spec/backend/database-guidelines.md`
- Review: `.trellis/spec/frontend/state-management.md`
- Review: `.trellis/spec/frontend/component-guidelines.md`

**Interfaces:**
- Consumes: all completed feature contracts.
- Produces: a verified, reviewable worktree commit without unrelated files.

- [x] Run `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin`.
- [ ] Run relevant migration, repository, and preference instrumentation tests on an available emulator; blocked because this environment has no connected device or installed emulator executable.
- [x] Run `python ./.trellis/scripts/task.py validate 08-04-book-content-preferences` and scan task artifacts for placeholders or contradictory requirements.
- [x] Review the full diff against backend/frontend spec quality checklists and fix all findings.
- [x] Decide whether the implementation establishes a reusable spec rule; schema 7 and hard-filter replanning contracts are recorded in backend/frontend specs.
- [ ] Inspect Git status and recent commit style, present the task-only commit set if confirmation is still required, then commit without pushing or modifying the base branch.
