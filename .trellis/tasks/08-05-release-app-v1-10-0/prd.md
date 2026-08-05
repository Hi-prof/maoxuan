# Release Android 1.10.0

## Goal

Publish the current `main` build as the signed Android `1.10.0` release so
installed `1.9.0` clients can discover and install the update from GitHub.

## Requirements

- Keep Android `versionName` at `1.10.0` and monotonically increasing
  `versionCode` at `13`.
- Align the App Release workflow's version-code assertion with source.
- Run the local release quality gate before creating the immutable tag.
- Push the consolidated `main` history and publish tag `app-v1.10.0` from its
  final release commit.
- Build and sign the production APK only in GitHub Actions using the existing
  repository secrets; do not expose or commit signing material.
- Publish exactly one versioned APK and its SHA-256 sidecar as a stable,
  non-latest GitHub Release.
- Preserve the Content Release as repository `latest` so the content manifest
  channel remains valid.

## Acceptance Criteria

- [x] Android unit tests, Release lint, Debug assembly, Personal assembly, and
  Android-test Kotlin compilation pass locally.
- [ ] `main` is pushed to `origin` without rewriting remote history.
- [ ] `app-v1.10.0` points at the final release commit and its GitHub Actions
  workflow succeeds.
- [ ] GitHub publishes `xinghuo-zhaidu-v1.10.0.apk` and its matching
  `.sha256` asset in a non-draft, non-prerelease, non-latest Release.
- [ ] The uploaded APK checksum matches the published sidecar.
- [ ] The repository latest Release remains a `content-v*` Release whose
  `manifest.json` is still downloadable.

## Notes

- This is a lightweight release task and remains PRD-only.
