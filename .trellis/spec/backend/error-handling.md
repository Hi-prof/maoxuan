# Error Handling

## Error Types

- Python `ValidationError(issues)` reports every source problem and exits CLI code `2`.
- Python `BuildError` reports deterministic/output failures and exits CLI code `3`.
- Android `ContentPackageException` represents invalid or unsafe package bytes.
- Kotlin `require`/`error` messages represent import contract violations and are surfaced as a user-readable update failure.
- `CancellationException` is control flow and must be rethrown after resetting transient update UI state.

## Handling Pattern

Validate at boundaries and keep the last installed version usable:

```kotlin
try {
    repository.downloadAndInstall(manifest, onProgress)
} catch (error: CancellationException) {
    updateState.value = UpdateUiState()
    throw error
} catch (error: Exception) {
    updateState.value = UpdateUiState(UpdatePhase.Error, message = error.message)
}
```

- Do not update `content_state` until the Room transaction succeeds.
- Do not catch and ignore package validation, hash, revision, or transaction errors.
- Initialization errors are recoverable and expose an explicit retry path.
- Network errors affect only manual update state; they never block cached reading.
- User-facing messages should be actionable Chinese text without stack traces, paths, or tokens.

## Remote Response Policy

There is no application API error envelope. Manifest/package fetches use HTTP status and strict JSON parsing. Only `http`/`https` source links are handed to the system browser; the app does not embed a WebView.

## Common Mistakes

- Converting cancellation into a generic red error state.
- Updating the installed version before file and Room changes commit.
- Treating malformed remote data as an empty/no-update response.
- Retrying or downloading automatically on launch, which violates manual-only networking.
