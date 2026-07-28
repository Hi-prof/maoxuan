# 星火摘读 MVP 交付记录

## Status

- 本地 MVP 实现完成。
- Android 客户端、内容工具、30 张正式卡片、8 张原创背景图、静态更新链路、CI 与文档均已落地。
- 已生成可直接安装的 `1.1.0` 个人版 APK；该构建使用 Release 优化和本机 Android 标准调试证书。
- 初始公共源码仓库已创建为 `Hi-prof/maoxuan`；本次交付仅推送源码，不创建内容标签或 GitHub Release。

## Delivered

- Android 9（API 28）起的本地优先 Compose 应用，名称为“星火摘读”。
- 纵向逐卡阅读、持久化随机轮次、双向回看、连续 3 秒已读和独立完成页。
- 卡片翻面、背面滚动、来源展开、点赞、收藏、搜索、详情和“阅读 / 收藏 / 笔记 / 我的”四栏导航。
- 收藏与点赞保持独立，并在同一页面通过分段控件切换，各自保留列表位置。
- 本地个人笔记支持独立笔记、卡片关联笔记、同卡多篇、编辑、确认删除和未保存返回提示；卡片操作区可直接新建关联笔记。
- 主阅读页与收藏/点赞/搜索详情页共用“解读”操作，以可滚动底部弹层离线展示“核心意思、理解重点、现实启示”。
- 搜索历史仅在提交时保存，跨重启保留最近 10 条，并支持单条删除和全部清空。
- 固定 `1080 x 1440` 分享图与 Android 系统分享面板。
- Room 保存内容、用户状态、个人笔记、轮次、下架快照和版本状态；笔记引用会参与下架快照保留与清理。
- 完整快照更新：手动检查、二次确认、取消、下载、SHA-256/ZIP/JSON/图片校验、原子导入、下架和恢复。
- Python YAML 校验、正式内容报告和确定性 ZIP/manifest 构建。
- GitHub Actions 普通检查工作流与 `content-vX.Y.Z` 内容发布工作流。
- 30 张双源核验正式卡片和 8 张 `CC0-1.0` 原创背景图，随 APK 内置。
- 完整 Noto Serif SC 字体、OFL 许可和来源记录。

## Latest Verification

2026-07-28 本机验证：

```text
Ruff: passed
pytest: 13 passed
formal content: 30 cards, 30 interpretations, 8 images
interpretation length: 202 to 228 code points
deterministic content build: passed
content ZIP SHA-256: 26bbf841d3637e1b79610a2f85e9379e2267db90a35a98f6dba437da87eba700
content ZIP bytes: 2,530,977

Debug JVM tests: 11 passed
Android Lint Debug: passed
Debug APK assembly: passed
Personal APK assembly with R8/resource shrinking: passed
API 28 instrumentation: 19 passed
API 35 instrumentation: 19 passed
Room 2 -> 3 migration with personal/search/round state: passed
Room 3 -> 4 migration with prior state and notes schema: passed
Personal APK interpretation open/scroll/detail smoke test on API 28: passed
Personal APK four-tab/note-list smoke test and retained note associations on API 35: passed
```

The final full-scope run passed `clean check`, Debug/Personal JVM tests, Debug/Personal lint, `assemblePersonal`, API 28/API 35 instrumentation, `actionlint 1.7.12`, formal content validation, deterministic content build, and release tag/source-version validation. A separate earlier gate also passed the unsigned `assembleRelease` build.

Visual artifacts are under `artifacts/screenshots/`, including minimum-height long-card front/back checks, three target viewports, system-dark fixed bars, and the final API 35 reader screen.

离线解读增量已在 `360 x 640`、`360 x 800` 和 `412 x 915` 视口检查操作栏、长篇名换行、弹层滚动与系统导航栏避让；阅读页和收藏详情页均保存了实机截图与 UI hierarchy。

搜索历史增量另完成了 Room `1 -> 2` migration 验证、大小写不敏感去重置顶、10 条裁剪、删除操作、IME Search 独立触发和 API 28 软键盘避让检查。API 28 上强制停止并重启 App 后，提交的历史记录仍可见。

收藏/点赞与个人笔记增量完成了 Room `3 -> 4` migration、独立及同卡多篇笔记、修改时间排序、编辑删除、下架快照保留/清理和保存中返回竞态回归。API 35 上实际创建独立笔记与关联笔记后，强制停止并重启 App，两篇正文及关联卡片摘要仍可见；`360 x 640`、`360 x 800` 和约 `412 x 915` 视口均已检查四栏导航、分段控件、卡片操作区、笔记列表和编辑器。

## Artifacts

- Current personal APK: `app/build/outputs/apk/personal/app-personal.apk`, 21,146,367 bytes, SHA-256 `4c141514f9ca45836c7af48d66cdd2b6283db80fbd290b361fca02e806a0c826`.
- Personal signer: Android Debug, RSA 2048, certificate SHA-256 `627f7ff3e7d35d0af6f7399dcd9dd1cdee2ad4905d3f3f55cf95754c5c4e1f57`, APK Signature Scheme v2.
- Reproducible Gradle output: `app/build/outputs/apk/personal/app-personal.apk` from `./gradlew :app:assemblePersonal`.
- Versioned local personal APK: `dist/xinghuo-zhaidu-v1.1.0-personal.apk`, same bytes and SHA-256 as the current personal APK (ignored build output).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 78,629,717 bytes, SHA-256 `32c50a80c80cdd03c22ecf36a74631659abb3ad624e11319bdc49306a9892891`.
- Bundled bootstrap: `app/src/main/assets/bootstrap.zip`, 2,530,977 bytes, SHA-256 `26bbf841d3637e1b79610a2f85e9379e2267db90a35a98f6dba437da87eba700`.
- Local content package: `dist/content-v1.1.0.zip` (ignored build output)
- Local manifest: `dist/manifest.json` (ignored build output)

## Not Published

- No tag has been pushed and no GitHub Release has been published.
- No production signing key has been created or used. The personal APK uses the local Android debug certificate; the public `release` build remains unsigned.
- The source repository is `Hi-prof/maoxuan`. The live update check returns HTTP 404 until the first `content-v1.1.0` Release publishes `manifest.json`.

## Release Preconditions

1. Confirm the initial `main` push passes GitHub Actions.
2. Configure secure Android release signing outside the repository if a distributable release APK is required.
3. Review the formal content report, then explicitly authorize a `content-v1.1.0` tag and Release.
