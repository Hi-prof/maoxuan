# App Online Update Design

## Architecture

The feature is split into three focused boundaries:

1. `AppUpdateClient` owns untrusted GitHub response parsing, release selection, checksum retrieval, bounded streaming download, and SHA-256 verification.
2. `AppUpdateManager` owns the Android cache directory, `REQUEST_INSTALL_PACKAGES` capability check, permission-settings intent, and `FileProvider` installer intent.
3. `MainViewModel` owns one exhaustive App update state machine; Compose renders state and sends explicit user intents.

The existing content updater remains independent. The only integration is an actionable content-version incompatibility state that starts the App update check.

## Contracts

`AppRelease` is an immutable domain value containing `versionName`, `publishedAt`, `releaseNotes`, `apkUrl`, `checksumUrl`, and `apkBytes`.

`AppUpdateClient` exposes:

```kotlin
suspend fun findUpdate(releasesUrl: String, currentVersion: String): AppRelease?
suspend fun download(
    release: AppRelease,
    destination: File,
    onProgress: (Float) -> Unit,
): File
```

`findUpdate` decodes the GitHub API with an API-specific tolerant DTO, then applies strict semantic validation to accepted candidates. GitHub may add response fields, so unknown API fields are ignored; accepted asset URLs, names, sizes, tags, dates, and release states remain explicitly validated.

`download` retrieves a small checksum asset first, streams the APK while updating one `MessageDigest`, verifies declared and actual byte counts, compares the digest, and atomically promotes a temporary file to the final cache file. Cancellation and every failure path delete both temporary and final candidates.

`AppUpdateManager` exposes update discovery/download plus:

```kotlin
fun canRequestPackageInstalls(): Boolean
fun openInstallPermissionSettings()
fun launchInstaller(apk: File)
```

Only files whose canonical parent is `cacheDir/app-updates` may be shared with the installer.

## State And Data Flow

`AppUpdatePhase` contains `Idle`, `Checking`, `Available`, `Downloading`, `PermissionRequired`, `ReadyToInstall`, `UpToDate`, and `Error`.

```text
explicit check
  -> fetch releases
  -> no newer release: UpToDate
  -> newer release: Available
  -> user confirms: Downloading
  -> checksum valid
      -> install permission granted: ReadyToInstall + launch installer
      -> permission missing: PermissionRequired + open system settings
  -> app resumes after settings
      -> permission granted: ReadyToInstall + launch installer
      -> still denied: remain PermissionRequired
```

The downloaded file is held privately by `MainViewModel`; Compose receives no filesystem path. Repeated check/download actions are ignored while an operation is active. Dismissing during download cancels the owned `Job`; other dismissals clear transient UI state and cached update references remain only until a new check or cache cleanup.

## User Interface

`MineScreen` keeps one quiet full-width surface with two unframed sections separated by a divider:

- Application: current `BuildConfig.VERSION_NAME` and “检查应用更新”.
- Content: existing version metadata and “检查内容更新”.

`AppUpdateDialog` mirrors the current content update dialog vocabulary but has independent typed state. The permission state explains the single required action and uses “打开设置”; the ready state uses “安装更新” so a cancelled system installer can be opened again.

When content installation is blocked by `minimumAppVersionCode`, the content dialog adds “更新应用”. Selecting it clears the content dialog and starts the explicit App check.

## Security And Compatibility

- Network calls allow HTTPS GitHub API and release-asset hosts only, including the final redirected URL.
- GitHub API requests include a stable User-Agent and API Accept header.
- Release tags and asset names must exactly match one semantic version.
- Checksum responses are capped at 4 KiB and APKs at 150 MiB.
- Hash verification happens before any install intent is created.
- Android's package installer enforces package name and signing-certificate compatibility for an in-place upgrade.
- `REQUEST_INSTALL_PACKAGES` is declared solely for the explicit user-driven installer flow.
- No Room schema or user-data migration is involved.

## Release Compatibility

App Releases remain `non-latest`; the client queries the Releases collection and filters `app-v*` tags. Content `latest` behavior remains unchanged. The implementation version becomes `1.7.0 / 9`; existing installations still require one final manual upgrade to `1.7.0`, after which later releases can update in-app.

## Testing

- JVM MockWebServer tests cover semantic selection, ignored invalid releases, no update, redirects/host policy, checksum parsing, successful download, cancellation cleanup, size mismatch, and hash mismatch.
- Pure state/format helpers are tested where practical; Compose integration is compiled and covered by existing instrumentation infrastructure.
- Run `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`; run `connectedDebugAndroidTest` only when a device is available.

## Rollback

Reverting the feature removes `AppUpdateClient`, `AppUpdateManager`, App update state/UI, the install permission, and the `app_updates` FileProvider path. Content updating and Room data remain unaffected. Cached APK files are disposable and Android may remove them at any time.
