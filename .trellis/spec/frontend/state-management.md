# State Management

## State Owners

- Room owns installed content, likes, favorites, personal notes, reading-round
  order, read timestamps, current position, content version, and submitted
  search history.
- `AppRepository` combines DAO `Flow`s and maps entities to immutable domain models.
- `MainViewModel` exposes `StateFlow` for `MainUiState`, search,
  `UpdateUiState`, `AppUpdateUiState`, and `NoteOperationUiState`, and receives explicit user intents.
- Navigation owns destinations/back stacks; screens own only transient presentation details.

```kotlin
val uiState = combine(contentUiState, initializing, initializationError) {
        content, isInitializing, error ->
    content.copy(isLoading = isInitializing, errorMessage = error)
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())
```

## State Categories

- Durable: Room-backed content and personal state.
- App-session: initialization and update phases in `MainViewModel`.
- Route-local: search text and list/pager positions when the navigation owner can restore them.
- Component-local: flip animation, background-sheet source expansion, and whether
  the detail route's background sheet is visible.
- Derived: active, favorite, liked, and search lists. Derive them from repository flows instead of persisting copies.

Notes are durable `PersonalNote` values in `MainUiState`. The editor's title,
body, delete-dialog visibility, and discard-dialog visibility are route-local
saveable/presentation state. The existing note ID and nullable card association
come from the route/domain value and are immutable during editing.

`NoteOperationUiState(inProgress, errorMessage)` is the single owner for an
active save/delete operation. Clear it when entering a different editor; while
`inProgress` is true, disable repeated actions and consume system back. On
success, clear the state before navigation; on failure, keep the editor values
and expose the repository error.

Search text remains route-local, but submitted history is durable. Only an explicit IME Search action saves a trimmed non-empty query. History clicks only restore the query; delete-one and clear-all are explicit ViewModel events backed by Room.

The reader keeps only the selected background card ID as transient state, keyed
by round and cleared when the settled page changes. The detail route keeps only
a local background-sheet visibility flag keyed by card ID. Neither belongs in
Room or `MainViewModel`, and closing the sheet must not alter flip or pager state.

## Update State Machine

`UpdatePhase` is the exhaustive workflow: `Idle`, `Checking`, `Available`, `Downloading`, `Success`, `UpToDate`, `Error`.

- `Checking` starts only from the explicit button.
- `Available` stores the parsed manifest and waits for user confirmation.
- `Downloading` owns a cancellable `Job` and progress from `0f..1f`.
- Cancel returns to `Idle`; validation/network failure becomes `Error`; installed data remains unchanged.
- Do not add scattered Boolean flags for the same workflow.

App updates use an independent exhaustive workflow: `Idle`, `Checking`,
`Available`, `Downloading`, `PermissionRequired`, `ReadyToInstall`, `UpToDate`,
and `Error`.

- `PermissionRequired` retains the verified APK privately and opens the per-app
  system setting only from an explicit action. An app-resume callback continues
  installation only when this phase is active and permission is now granted.
- `ReadyToInstall` keeps an explicit retry action because the system installer
  can be cancelled without returning an installation result to Compose.
- Content incompatibility sets `requiresAppUpdate`; selecting `更新应用` clears
  the content dialog before starting the independent App check.
- App and content download jobs are separately owned and cancellable; never
  encode both workflows into one phase enum.

## Reading Round State

The repository persists the exact randomized order and position. UI paging updates position only after a settled page. Backtracking changes position but never clears `readAt`. A completed round remains visible until the user explicitly starts a new one.

## Common Mistakes

- Keeping likes/favorites only in composable state.
- Re-randomizing from a seed after process restart.
- Duplicating active/liked/favorite filters in several screens.
- Using independent update Booleans that allow impossible combinations.
- Keeping note text only in a composable after save or encoding several notes
  into a card/user-state field.
- Allowing an edit route to replace the note's original card ID.
