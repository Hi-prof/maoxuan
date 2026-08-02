# App Online Update Implementation Plan

> **For agentic workers:** Execute this plan task-by-task. Keep the user's existing `QuoteCard.kt` and `QuoteCardInstrumentedTest.kt` changes intact.

**Goal:** Add explicit, verified in-app APK update checks, downloads, and installer handoff without changing the existing content update contract.

**Architecture:** A pure network client parses and verifies GitHub App Releases; an Android manager owns cache and installer intents; `MainViewModel` and Compose own the user-driven state machine and presentation.

**Tech Stack:** Kotlin 2, Android API 28-35, Jetpack Compose Material 3, OkHttp, kotlinx.serialization, FileProvider, GitHub Releases API.

## Global Constraints

- App update networking starts only from an explicit user action.
- App Release remains `non-latest`; content Release remains the repository latest.
- Only HTTPS GitHub hosts are accepted.
- APK maximum size is 150 MiB and checksum response maximum size is 4 KiB.
- Version becomes `versionName 1.7.0` and `versionCode 9`.
- No task step may revert unrelated working-tree changes.
- Do not commit, tag, push, or publish without separate user authorization.

---

### Task 1: GitHub Release Discovery And Verified Download

**Files:**

- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/model/AppRelease.kt`
- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/network/AppUpdateClient.kt`
- Create: `app/src/test/java/com/xuhuangbin/xinghuozhaidu/data/network/AppUpdateClientTest.kt`

**Interfaces:**

- Produces `AppRelease` and `AppUpdateClient.findUpdate(...)` / `download(...)` for Task 2.

- [x] Write MockWebServer tests for highest valid `app-v*` selection, same-version no-op, draft/prerelease and incomplete-asset filtering.
- [x] Implement strict candidate validation over tolerant GitHub API DTO decoding and run the focused JVM test.
- [x] Add tests for valid checksum download, wrong hash, byte-count mismatch, size limit, and temporary-file cleanup.
- [x] Implement bounded checksum fetch and streaming APK digest verification; run `:app:testDebugUnitTest`.

### Task 2: Android Cache And Installer Boundary

**Files:**

- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/update/AppUpdateManager.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/AppContainer.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/xml/file_paths.xml`

**Interfaces:**

- Consumes `AppRelease` and `AppUpdateClient` from Task 1.
- Produces `findUpdate`, `download`, `canRequestPackageInstalls`, `openInstallPermissionSettings`, and `launchInstaller` for Task 3.

- [x] Add the dedicated canonical `cacheDir/app-updates` boundary and stale-file cleanup.
- [x] Add `REQUEST_INSTALL_PACKAGES` and the `app_updates` FileProvider cache path.
- [x] Implement Android 8+ permission capability/settings flow and a grant-read installer intent restricted to verified cached APK files.
- [x] Wire one `AppUpdateManager` through `AppContainer`; compile Debug Kotlin.

### Task 3: Exhaustive ViewModel State Machine

**Files:**

- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/XinghuoApp.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/AppRepository.kt`

**Interfaces:**

- Consumes `AppUpdateManager` from Task 2.
- Produces `AppUpdateUiState`, explicit check/download/install/resume/dismiss intents, and an actionable incompatible-content flag for Task 4.

- [x] Add one exhaustive App update phase/state value and one owned cancellable download job.
- [x] Implement check, confirm/download, permission request, resume-after-settings, retry-install, and dismiss transitions with actionable Chinese failures.
- [x] Mark content incompatibility as actionable and transition from content dialog to App check.
- [x] Wire manager injection through the ViewModel factory and add an app-resume callback that is a no-op outside `PermissionRequired`.

### Task 4: Mine Screen And Dialogs

**Files:**

- Create: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/update/AppUpdateDialog.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/update/UpdateDialog.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/saved/SavedScreens.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/XinghuoApp.kt`

**Interfaces:**

- Consumes typed state and intents from Task 3.

- [x] Split the ambiguous Mine action into current App version / content version sections and explicit update buttons.
- [x] Render App checking, available metadata, download progress/cancel, permission, ready-to-install, up-to-date, and error states.
- [x] Add “更新应用” to content incompatibility without changing other content error behavior.
- [x] Compile instrumentation Kotlin and add a `360 x 640 dp` visibility regression for the Mine actions.

### Task 5: Release Version And Quality Gate

**Files:**

- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/app-release.yml`
- Modify: `.trellis/spec/backend/error-handling.md`
- Modify: `.trellis/spec/backend/quality-guidelines.md`
- Modify: `.trellis/spec/frontend/state-management.md`
- Modify: `.trellis/spec/frontend/quality-guidelines.md`

**Interfaces:**

- Produces the releasable `1.7.0 / 9` source and executable project conventions.

- [x] Set Android version to `1.7.0 / 9`, add the GitHub Releases API BuildConfig URL, and update workflow version-code validation.
- [x] Update backend/frontend specs for App update state, explicit networking, install permission, security bounds, and release-channel behavior.
- [x] Run focused JVM tests, full Debug JVM tests, `lintDebug`, `assembleDebug`, and `compileDebugAndroidTestKotlin`.
- [x] Run the App update and repository instrumentation suites on API 28 and API 35. The full API 35 suite still has two pre-existing failures in card gesture tests outside this task (`InterpretationInstrumentedTest.readerCanPageForwardFromInterpretationBack` and `QuoteCardInstrumentedTest.verticalScrollContinuesAfterHorizontalFlipDragStarts`).
- [x] Run Trellis task validation, `git diff --check`, conflict-marker scan, and final status review without committing unrelated files.
