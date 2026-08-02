# Implementation Plan

## Scope

Publish a content-only patch release `content-v1.4.1` so the current Android
app can exercise the existing manual update flow.

## Steps

- [x] Update `content/project.yaml` release metadata to `1.4.1`.
- [x] Run content-tool lint, tests, formal validation, and deterministic build.
- [x] Confirm generated manifest uses `content-v1.4.1` and expected GitHub
  asset URL.
- [x] Run Android debug unit tests for package parsing and content bootstrap
  coverage.
- [x] Ask before committing, tagging, or pushing.
- [x] Commit only this task's content/Trellis changes.
- [x] Create and push `content-v1.4.1` after approval.
- [x] Verify GitHub Actions publishes the content release as latest.
- [x] Verify public latest manifest and ZIP contract.
- [ ] Ask the user to test the Mine-tab update pull on device, or provide
  local adb instructions if a device is connected.

## Validation Commands

```powershell
python -m ruff check content-tool
python -m pytest content-tool
python -m xinghuo_content validate content --formal
python -m xinghuo_content build content --output dist --formal --verify-deterministic
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

## Risk Notes

- `dist/` outputs are ignored and must not be committed.
- Existing unrelated frontend/spec worktree changes must stay out of the release
  commit and tag.
- Publishing uses GitHub latest release routing; after the tag workflow, latest
  must point to `content-v1.4.1`, not an app release.

## 2026-08-02 Content Expansion Plan

- [x] Extend content authoring / Android parser date validation to accept
  ancient-thinker `authoredAt` labels such as `前4世纪`.
- [x] Replace the generated pre-Qin raw-text cards with 300 curated popular /
  encouraging famous-quote cards and 150 Marxism-principles thinking cards.
  Select quotes from publicly reachable, attributable pages and attach a
  specific reading guide instead of mechanically extracted prose.
- [x] Keep the existing 150 Mao-related cards and their evidence-based reading
  guides; do not replace them with generic quote text.
- [x] Raise `content/project.yaml` to `1.5.0`, `minimumAppVersionCode: 7`, and
  `expectedPublishedCards: 600`.
- [x] Run content-tool lint/tests, formal validation, report, and deterministic
  build with bootstrap output after the card replacement.
- [x] Run Android debug unit tests for content package parsing after the card
  replacement.
- [ ] Do not commit, tag, or push without explicit user approval.
