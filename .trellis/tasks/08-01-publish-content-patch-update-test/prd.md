# Publish content patch for update pull test

## Goal

Publish a content patch release that lets the already-installed Android
`1.5.0` app discover a newer content snapshot from the Mine tab and pull it
through the existing manual update flow.

## Confirmed Facts

- The current app is `versionName=1.5.0`, `versionCode=6`.
- The bundled content and current public latest content release are both
  `1.4.0`, so the app correctly reports "already latest" today.
- The public stable manifest URL is
  `https://github.com/Hi-prof/maoxuan/releases/latest/download/manifest.json`.
- The public `content-v1.4.0` manifest and ZIP are reachable, and the manifest
  points to a valid package asset.
- The Android update workflow already checks the manifest, compares semantic
  content versions, asks for confirmation, downloads, verifies SHA-256, and
  imports into Room.

## Requirements

- Create a new content snapshot version greater than `1.4.0`; use `1.4.1`.
- Keep schema version, minimum app version, published-card count, card YAML,
  image assets, and Android UI code unchanged.
- Update only release metadata needed to produce a new manifest and package:
  content version, publication timestamp, and release notes.
- Build and validate the content package with the existing content tool.
- Publish through the existing `content-vX.Y.Z` workflow so GitHub `latest`
  points at the new content release.
- Do not include unrelated local frontend/spec edits in any commit or tag.

## Acceptance Criteria

- [x] `content/project.yaml` declares `contentVersion: 1.4.1`.
- [x] Formal content validation passes for 150 published cards.
- [x] The content package build emits `dist/content-v1.4.1.zip` and
  `dist/manifest.json`.
- [ ] The public latest manifest reports `contentVersion=1.4.1` after release.
- [ ] The public latest manifest's `packageUrl`, `packageBytes`, and
  `packageSha256` match a reachable ZIP asset.
- [ ] A current app installed with content `1.4.0` can manually check for the
  `1.4.1` update and download/install it.

## Notes

- This is a content release only. APK self-update or app-version update checks
  are out of scope.
