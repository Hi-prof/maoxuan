# Visual Verification

## Environment

- APK: current `app-debug.apk` built from task branch
- Device: Android API 35 emulator
- Density override: `160 dpi`, so screenshot pixels match target dp
- Date: 2026-08-05

## Screenshots

| Artifact | Size | Result |
| --- | ---: | --- |
| `reader-360x640.png` | 360 x 640 | Compact card hides the short index; quote, title, source, actions, navigation, and system bars do not overlap. |
| `reader-360x800.png` | 360 x 800 | Archive photo and short index are visible; paper overlay keeps all text readable. |
| `reader-412x915.png` | 412 x 915 | Wide/tall layout remains framed correctly with stable card and action dimensions. |
| `search-360x800.png` | 360 x 800 | Filled search field, `找到 1 条`, ancient date, red rule, and text hierarchy are visible without a thumbnail. |
| `saved-favorites-360x800.png` | 360 x 800 | Compact centered segments, `收藏 1 条`, and the shared no-thumbnail summary row are visible. |
| `saved-liked-360x800.png` | 360 x 800 | Independent liked segment shows `点赞 1 条` with the same summary hierarchy. |
| `missing-image-360x800-api28.png` | 1080 x 1920 backing surface, 360 x 800 dp viewport | After deleting the emulator's imported image files and restarting, the card falls back to an unframed paper background with no broken-image placeholder; all text remains in bounds. |

## Pixel Checks

Pillow decoded all screenshots as RGB images. Each target had more than 700 sampled colors and non-zero channel standard deviation, so none is blank or a flat placeholder.

| Artifact | RGB standard deviation | Sampled colors |
| --- | --- | ---: |
| `reader-360x640.png` | 28.7 / 33.8 / 33.6 | 1127 |
| `reader-360x800.png` | 26.9 / 31.4 / 31.1 | 1168 |
| `reader-412x915.png` | 24.7 / 28.5 / 28.3 | 1151 |
| `search-360x800.png` | 22.3 / 26.1 / 25.8 | 724 |
| `saved-favorites-360x800.png` | 23.9 / 26.8 / 26.6 | 873 |
| `saved-liked-360x800.png` | 23.9 / 26.7 / 26.6 | 879 |

Reader-card crops also had RGB standard deviations of 27.0-37.6, confirming that the archive photograph remains present beneath the paper overlay. The share renderer instrumentation test separately asserts a nonblank `1080 x 1440` output with background pixels and attribution text.

The API 28 emulator returns its physical `1080 x 1920` backing surface from `screencap` even under the `360 x 800` logical viewport override. Its missing-image artifact was therefore checked by UI bounds and manual inspection instead of comparing file pixels to dp dimensions.
