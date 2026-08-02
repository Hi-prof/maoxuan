# App Update Contract

## 1. Scope / Trigger

This contract applies whenever a change touches `AppUpdateClient`, `AppUpdateManager`, App update UI state, `AndroidManifest.xml`, `file_paths.xml`, Android version values, or `.github/workflows/app-release.yml`.

The project uses one GitHub repository for two release channels. Content Releases are repository `latest`; App Releases are always `non-latest`. App discovery must query and filter the Releases collection rather than reuse the content latest endpoint.

## 2. Signatures

Network boundary:

```kotlin
suspend fun AppUpdateClient.findUpdate(
    releasesUrl: String,
    currentVersion: String,
): AppRelease?

suspend fun AppUpdateClient.download(
    release: AppRelease,
    destination: File,
    onProgress: (Float) -> Unit,
): File
```

Android boundary:

```kotlin
suspend fun AppUpdateManager.findUpdate(releasesUrl: String, currentVersion: String): AppRelease?
suspend fun AppUpdateManager.download(release: AppRelease, onProgress: (Float) -> Unit): File
fun AppUpdateManager.canRequestPackageInstalls(): Boolean
fun AppUpdateManager.openInstallPermissionSettings()
fun AppUpdateManager.launchInstaller(apk: File)
```

## 3. Contracts

- `BuildConfig.APP_RELEASES_API_URL` points to the GitHub Releases collection with a bounded page size; networking starts only from an explicit user action.
- Accepted Releases are non-draft, non-prerelease tags matching `app-vMAJOR.MINOR.PATCH`.
- One accepted Release contains exactly one `xinghuo-zhaidu-vMAJOR.MINOR.PATCH.apk` and one same-name `.sha256` asset.
- The highest semantic version greater than `BuildConfig.VERSION_NAME` is offered; repository `latest` is never used for App selection.
- GitHub's API envelope ignores unknown fields because it is provider-owned, but every accepted tag, date, asset name, byte count, URL, and release state is validated explicitly.
- Checksum responses are at most 4 KiB. APKs are streamed to `cacheDir/app-updates`, are at most 150 MiB, and are promoted from `.part` only after byte-count and SHA-256 verification.
- Only HTTPS GitHub API/release hosts and final redirects are accepted. Test-only hosts are dependency-injected and never added to production allowlists.
- The FileProvider authority is `${applicationId}.fileprovider`; only the `app-updates/` cache child is exposed for APK installation.
- `REQUEST_INSTALL_PACKAGES` is used only after explicit confirmation. Android's package installer remains responsible for package-name and signing-certificate compatibility.
- App Releases stay `non-latest`; the app release workflow verifies that the repository latest Content Release and its `manifest.json` remain intact.

## 4. Validation & Error Matrix

| Condition | Behavior |
| --- | --- |
| HTTP failure or malformed API JSON | `AppUpdateException` with actionable Chinese text |
| Draft, prerelease, content tag, malformed semver, invalid date | Ignore candidate |
| Missing/duplicate/misnamed APK or checksum asset | Ignore candidate |
| Non-HTTPS or unapproved host/redirect | Reject request or candidate |
| Checksum over 4 KiB or malformed checksum line | Reject before APK installation |
| APK over 150 MiB, declared/actual size mismatch | Cancel download and delete candidate files |
| SHA-256 mismatch or coroutine cancellation | Delete `.part` and final candidate; never create install intent |
| Install permission absent | Enter `PermissionRequired` and open per-app system settings |
| Permission still denied on resume | Remain retryable; do not repeatedly launch settings or installer |
| Installer unavailable or cache path outside boundary | Enter App update `Error` |

## 5. Good / Base / Bad Cases

- Good: Releases include content tags plus stable, draft, and prerelease App tags; only the highest complete stable App version is offered.
- Base: the newest valid App tag equals the installed version; show `UpToDate` without downloading assets.
- Good: a verified APK is shared through FileProvider and the same signing certificate allows an in-place upgrade that preserves Room data.
- Bad: use `releases/latest`; it resolves to the Content Release and cannot discover App APKs.
- Bad: trust the APK asset URL without checking its paired `.sha256`; downloaded bytes must never reach the installer.
- Bad: download on app startup or tab entry; App and content networking are manual-only.

## 6. Tests Required

- JVM MockWebServer: highest semantic App version selection, same-version no-op, draft/prerelease/content/incomplete candidate filtering, unknown provider fields, and malformed response errors.
- JVM download: valid checksum, hash mismatch, declared/actual byte mismatch, 150 MiB limit, progress completion, and `.part` cleanup.
- Compose/instrumentation: both Mine update actions visible at `360 x 640 dp`; incompatible content exposes an `更新应用` action; App dialog confirmation/cancel states remain reachable.
- Release workflow: tag matches `versionName`, expected `versionCode` matches source, APK signature/badging pass, APK and `.sha256` upload, App Release is `non-latest`, and Content latest is restored/verified.

## 7. Wrong vs Correct

Wrong: query the shared repository latest Release for an App update.

```kotlin
fetch("https://github.com/Hi-prof/maoxuan/releases/latest")
```

Correct: query the Releases collection, validate provider data, filter immutable `app-v*` tags, and compare semantic versions.

```kotlin
appUpdateClient.findUpdate(BuildConfig.APP_RELEASES_API_URL, BuildConfig.VERSION_NAME)
```

Wrong: expose a broad cache root or start the installer before verification.

```xml
<cache-path name="cache" path="." />
```

Correct: expose only the verified update directory and create the install intent after hash validation.

```xml
<cache-path name="app_updates" path="app-updates/" />
```
