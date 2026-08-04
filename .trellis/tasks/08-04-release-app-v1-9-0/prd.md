# Release app v1.9.0

## Goal

Publish the book-preference build as the signed Android `1.9.0` release from
the dedicated `task/1` branch.

## Requirements

- Set the Android app version to `1.9.0` with monotonically increasing
  `versionCode` 12.
- Keep the App Release workflow's version-code assertion aligned with source.
- Update the documented current App version without changing content versions.
- Commit and push only `task/1`; do not merge, rebase onto, or push `main`.
- Publish immutable tag `app-v1.9.0` through the existing signed-release
  workflow, with no signing material added to the repository.
- Keep the App Release non-latest so the latest Content Release and stable
  `manifest.json` channel remain unchanged.

## Acceptance Criteria

- [x] Local Android unit tests, lint, Debug assembly, and Android-test Kotlin
  compilation pass for the release source.
- [ ] `task/1` contains the version preparation commit and is pushed to GitHub.
- [ ] `app-v1.9.0` points at the final release commit and the tag workflow passes.
- [ ] GitHub publishes `xinghuo-zhaidu-v1.9.0.apk` and its matching `.sha256`.
- [ ] The published App Release is stable and non-latest, while the latest
  repository Release remains a `content-v*` Release with `manifest.json`.

## Notes

- This is a lightweight release task and remains PRD-only.
- The production APK is built and signed only by GitHub Actions.
