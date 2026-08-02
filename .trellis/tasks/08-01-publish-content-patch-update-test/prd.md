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
- [x] The public latest manifest reports `contentVersion=1.4.1` after release.
- [x] The public latest manifest's `packageUrl`, `packageBytes`, and
  `packageSha256` match a reachable ZIP asset.
- [ ] A current app installed with content `1.4.0` can manually check for the
  `1.4.1` update and download/install it.

## Notes

- This is a content release only. APK self-update or app-version update checks
  are out of scope.

## Scope Update: 2026-08-02 Content Expansion

The user requested that the content library be expanded to 600 cards and no
longer be limited to Marxist / Mao text cards. This supersedes the earlier
`1.4.1` source-content target for local development, while the already-published
`content-v1.4.1` release remains the current public latest release until a new
tag is created.

Additional requirements:

- Keep the existing 150 Mao-related published cards active.
- Replace the previously generated pre-Qin raw-text excerpts with 450 curated
  cards. The new cards must be selected for recognizability, encouragement,
  and discussion value rather than being mechanically extracted passages.
- Add 300 published popular / encouraging famous-quote cards and 150 published
  Marxism-principles thinking cards. Every card must retain an attributable,
  publicly reachable source, its author or speaker, and a concrete reading
  guide; do not rely on common but unverifiable online attributions.
- Keep the existing Mao-related cards as the library's third strand. Their
  existing contextual interpretations are retained because they already focus
  on investigation, practice, strategy, and historical context rather than
  presenting bare quotations.
- Raise source content to `1.5.0`, with `expectedPublishedCards: 600`.
- Raise `minimumAppVersionCode` to `7` because the new cards include ancient
  thinkers with `authoredAt` values such as `前4世纪`, which require the updated
  app parser.
- Rebuild `app/src/main/assets/bootstrap.zip` so fresh installs contain all 600
  cards offline.

Additional acceptance criteria:

- [x] Formal content validation passes for 600 published cards.
- [x] The Android bundled bootstrap parses as content `1.5.0` with 600 cards.
- [x] The content build emits deterministic local `dist/content-v1.5.0.zip` and
  `dist/manifest.json`.
- [x] README and backend content-contract specs describe the new count and
  ancient-date format.
- [x] The 450 new cards are a curated 300/150 mix of popular encouraging and
  Marxism-principles quotes, with no remaining batch of mechanically selected
  pre-Qin excerpts.
