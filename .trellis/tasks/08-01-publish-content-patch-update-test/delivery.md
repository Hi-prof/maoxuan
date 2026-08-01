# 1.4.1 Content Patch Delivery

Release date: 2026-08-01

## Source And Release

- Work commit: `6aa4d4beeafaf651664770a0cc827ece9abe914f`
- Tag: `content-v1.4.1`
- Release: <https://github.com/Hi-prof/maoxuan/releases/tag/content-v1.4.1>
- Workflow: <https://github.com/Hi-prof/maoxuan/actions/runs/30693796810>
- Workflow result: completed successfully.

## Public Asset Verification

- Latest release tag: `content-v1.4.1`
- Latest manifest URL:
  <https://github.com/Hi-prof/maoxuan/releases/latest/download/manifest.json>
- Manifest `contentVersion`: `1.4.1`
- Manifest `packageUrl`:
  <https://github.com/Hi-prof/maoxuan/releases/download/content-v1.4.1/content-v1.4.1.zip>
- Public ZIP bytes: `2,574,072`
- Public ZIP SHA-256:
  `fcae9c903cc1e48d8eefdd49ca16d8afa2867a8fafd6f3f1203e7278bfa76b88`
- Independent download matched both manifest bytes and SHA-256.

## Local Validation

- `python -m ruff check content-tool`: passed.
- `python -m pytest content-tool`: `24 passed`.
- `python -m xinghuo_content validate content --formal`: `1.4.1 / 150 published / 0 withdrawals / 8 images`.
- `python -m xinghuo_content build content --output dist --formal --verify-deterministic`: passed.
- `.\gradlew.bat :app:testDebugUnitTest --no-daemon`: passed.
- `.\gradlew.bat :app:lintDebug --no-daemon`: passed.
- `.\gradlew.bat :app:assembleDebug --no-daemon`: passed.

## Pending

- No device was connected locally (`adb devices` returned no devices), so the
  final Mine-tab manual pull must be verified on the user's installed app.
