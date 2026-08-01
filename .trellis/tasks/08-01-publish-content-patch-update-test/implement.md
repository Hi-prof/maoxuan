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
- [ ] Commit only this task's content/Trellis changes.
- [ ] Create and push `content-v1.4.1` after approval.
- [ ] Verify GitHub Actions publishes the content release as latest.
- [ ] Verify public latest manifest and ZIP contract.
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
