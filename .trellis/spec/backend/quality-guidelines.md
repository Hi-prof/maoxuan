# Backend Quality Guidelines

## Required Commands

From the repository root:

```powershell
$env:PYTHONPATH='content-tool/src'
python -m ruff check content-tool
python -m pytest content-tool
python -m xinghuo_content validate content --formal
python -m xinghuo_content build content --output dist --formal --verify-deterministic
```

For Android persistence or package changes:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

Run instrumentation on API 28 and the latest configured API before release.

## Forbidden Patterns

- Lenient JSON parsing (`ignoreUnknownKeys = true`) for release assets.
- Undocumented destructive Room migrations or destructive upgrades without
  explicit acceptance of local-data loss.
- Silent card deletion from snapshot omission.
- Non-monotonic revisions or mutable image IDs.
- UI/DAO code that reimplements package validation.
- Automatic launch/background update checks.
- Using the shared repository `latest` endpoint to discover App Releases; `latest` belongs to content updates.
- Committing `dist/`, APKs, local databases, SDK paths, virtual environments, or signing material.
- Printing signing passwords, keystore bytes, or Base64 keystore content in CI logs.

## Required Patterns

- Build output is deterministic and content addressed.
- Producer and consumer validation change together.
- User state survives content revision, withdrawal snapshot retention, and restoration.
- File writes precede the Room reference switch; cleanup follows commit/failure.
- Formal content has exactly the positive `expectedPublishedCards` count declared
  in `content/project.yaml`; current content `1.7.0` declares 700 published cards.
- Published quotes are unique after NFC normalization, and the content report
  includes sorted `workTitles` counts for editorial limit review.
- Every active card has both interpretation fields plus historical event,
  background, and story; removed schema-2 fields are rejected.

## Test Review Checklist

- Parser tests include valid, malformed, oversized, duplicate, traversal, bad-hash, and unknown-schema cases.
- Repository tests cover first import, revision, withdrawal, restoration, and local state preservation.
- Reading-round tests cover stable order, backtracking, additions, withdrawals, completion, and restart semantics.
- Recommendation JVM tests cover all 12 taxonomy pools against the bundled 700
  cards, bounded explicit-signal weights, 4:1 exploration mixing, diversity,
  deterministic randomness, and duplicate-free output.
- Package fixtures in JVM and instrumentation tests use the current package
  schema. A reader schema bump must update both fixture sets before repository
  and UI behavior can be tested meaningfully.
- Room and repository instrumentation covers schema `5 -> 6`, fresh versus
  migrated onboarding, locked-prefix tail replanning, preference replacement,
  negative-feedback clearing, restart persistence, and content add/withdraw on
  API 28 and the latest configured API.
- Release workflow validates `content-vX.Y.Z` against `project.yaml` before publishing assets.
- App release workflow validates `app-vX.Y.Z` against Android `versionName`,
  verifies the signed APK and publishes it as non-latest so the content manifest
  remains available through the stable latest-release URL.
- App update tests validate stable-release filtering, semantic ordering, host and
  size boundaries, paired checksum parsing, streaming hash verification, and
  cleanup after cancellation or failure.

## Windows Gradle Gotcha

An old Gradle daemon launched under another JDK can retain the lint cache and make `clean` fail with a locked file. Stop daemons from each previously used JDK, then rerun with the project JDK 17. Do not delete source or reset the worktree to work around a cache lock.
