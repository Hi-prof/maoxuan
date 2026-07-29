# 星火摘读 MVP 交付记录

## Status

- 本地 MVP 实现完成。
- Android 客户端、内容工具、31 张正式卡片、8 张原创背景图、静态更新链路、CI 与文档均已落地。
- 内容与 App `1.3.0` 已提交到 `main`，并通过两个独立标签完成公开发布。
- `content-v1.3.0` 是仓库 latest Release；`app-v1.3.0` 保持非 latest，稳定内容清单地址不变。
- 正式 APK 为 `versionCode = 4`、`versionName = 1.3.0`，继续使用既有正式签名证书。

## Delivered

- Android 9（API 28）起的本地优先 Compose 应用，名称为“星火摘读”。
- 纵向逐卡阅读、持久化随机轮次、双向回看、连续 3 秒已读和独立完成页。
- 卡片翻面、背面滚动、来源展开、点赞、收藏、搜索、详情和“阅读 / 收藏 / 笔记 / 我的”四栏导航。
- 收藏与点赞保持独立，并在同一页面通过分段控件切换，各自保留列表位置。
- 本地个人笔记支持独立笔记、卡片关联笔记、同卡多篇、编辑、确认删除和未保存返回提示；卡片操作区可直接新建关联笔记。
- 卡片背面依次离线展示“启示、解读”；“读背景”弹层依次展示历史节点、时代背景、篇名、出处、相关故事和参考来源。
- 搜索历史仅在提交时保存，跨重启保留最近 10 条，并支持单条删除和全部清空。
- 固定 `1080 x 1440` 分享图与 Android 系统分享面板。
- Room 保存内容、用户状态、个人笔记、轮次、下架快照和版本状态；笔记引用会参与下架快照保留与清理。
- 完整快照更新：手动检查、二次确认、取消、下载、SHA-256/ZIP/JSON/图片校验、原子导入、下架和恢复。
- Python YAML 校验、正式内容报告和确定性 ZIP/manifest 构建。
- GitHub Actions 普通检查、`content-vX.Y.Z` 内容发布与 `app-vX.Y.Z` 正式签名 APK 发布工作流。
- 31 张双源核验正式卡片和 8 张 `CC0-1.0` 原创背景图，随 APK 内置。
- 完整 Noto Serif SC 字体、OFL 许可和来源记录。

## Latest Verification

2026-07-29 `1.3.0` 内容与卡片结构增量：

```text
Ruff: passed
pytest: 23 passed
formal content: 31 cards, 8 images, 0 withdrawals
inspiration length: 108 to 126 code points, average 117.45
explanation length: 217 to 271 code points, average 253.87
historical event length: 34 to 44 code points, average 40.58
same-environment deterministic content build: passed
local bootstrap ZIP SHA-256: de93d59e78609d4895e04be1dbbde6e6d1d99b0cb1c08098f637b9e153b764a5
local bootstrap ZIP bytes: 2,537,118
published Linux ZIP SHA-256: b7c8be751f39f4518d79c3a1c6e1c3c7e23e44aec0e2132c19387a8ed6157582
published Linux ZIP bytes: 2,536,035

Debug JVM tests: passed
Android Lint Debug: passed
Debug APK assembly: passed
Personal APK assembly with R8/resource shrinking: passed
API 28 instrumentation: 19 passed
API 35 instrumentation: 19 passed
Fresh schema-5 bootstrap import on both emulators: passed
Room 4 -> 5 data migration: intentionally omitted; destructive rebuild approved
git diff --check: passed
main Checks workflow: passed (30441684996)
content Release workflow: passed (30441890196)
signed App Release workflow: passed (30442181639)
```

`contextExcerpt` and the three schema-2 interpretation children are absent from all content YAML, generated schema-3 payloads, Android production models, and Room schema 5. Their remaining source occurrence is limited to an explicit rejection test; historical Room schemas and migrations remain unchanged.

Windows 与 GitHub Ubuntu 构建的 ZIP 因 zlib 实现不同而压缩字节不同；逐条目核验确认 12 个条目的解压后 SHA-256、长度、时间戳与权限完全一致。公开 `manifest.json` 与 Ubuntu 生成的 ZIP 大小和哈希自洽，客户端以公开 manifest 为下载校验依据。

2026-07-28 本机验证：

```text
Ruff: passed
pytest: 20 passed
formal content: 31 cards, 31 interpretations, 8 images
interpretation length: 202 to 228 code points
deterministic content build: passed
content ZIP SHA-256: 570ba7c3c54efc2a5a9a21bbf7fbdca42dc61968ddee5b754d882bafe5059a32
content ZIP bytes: 2,531,665

Debug JVM tests: 12 passed
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

The prior full-scope run passed `clean check`, Debug/Personal JVM tests, Debug/Personal lint, `assemblePersonal`, API 28/API 35 instrumentation, `actionlint 1.7.12`, formal content validation, deterministic content build, and content release tag/source-version validation.

正式 APK 增量使用 Temurin 17.0.19 完成以下验证：修改前 `assembleRelease` 会生成 `app-release-unsigned.apk`；修改后无签名配置和部分签名配置均在任务执行前失败，且 debug、personal、release lint 不依赖正式密钥。`testDebugUnitTest`、`lintDebug`、`lintRelease`、`assembleDebug`、`assemblePersonal` 和带正式签名的 `assembleRelease` 均通过。`actionlint 1.7.12` 检查三个工作流无错误；秘密扫描覆盖 337 个仓库文本文件，未发现密码、Base64 keystore 或签名文件泄露。

本次重跑 API 28 instrumentation 时发现初始化回归仍把内置内容版本期望写死为 `1.1.0`；结构化检查确认当前 bootstrap 为 `1.2.0`、31 张卡片，目标卡 revision 仍为 2，因此只更新陈旧期望，不修改初始化逻辑。修复后 API 28 与 API 35 均为 19/19 通过。

Visual artifacts are under `artifacts/screenshots/`, including minimum-height long-card front/back checks, three target viewports, system-dark fixed bars, and the final API 35 reader screen.

离线解读增量已在 `360 x 640`、`360 x 800` 和 `412 x 915` 视口检查操作栏、长篇名换行、弹层滚动与系统导航栏避让；阅读页和收藏详情页均保存了实机截图与 UI hierarchy。

搜索历史增量另完成了 Room `1 -> 2` migration 验证、大小写不敏感去重置顶、10 条裁剪、删除操作、IME Search 独立触发和 API 28 软键盘避让检查。API 28 上强制停止并重启 App 后，提交的历史记录仍可见。

收藏/点赞与个人笔记增量完成了 Room `3 -> 4` migration、独立及同卡多篇笔记、修改时间排序、编辑删除、下架快照保留/清理和保存中返回竞态回归。API 35 上实际创建独立笔记与关联笔记后，强制停止并重启 App，两篇正文及关联卡片摘要仍可见；`360 x 640`、`360 x 800` 和约 `412 x 915` 视口均已检查四栏导航、分段控件、卡片操作区、笔记列表和编辑器。

内容 `1.2.0` 增量新增《关于重庆谈判》“前途是光明的，道路是曲折的。”卡片，并把正式卡片数量门禁改为由 `project.yaml` 的 `expectedPublishedCards` 精确声明。JVM 回归直接解析内置 `bootstrap.zip`，确认版本 `1.2.0`、31 张卡片和新增 UUID 均存在。

## Artifacts

- Published signed `1.3.0` APK: `xinghuo-zhaidu-v1.3.0.apk`, 21,156,643 bytes, SHA-256 `f0dd85b8b1e43252ff82666b37f209ff4c16b64b20841e40c1a8166977239060`.
- Release signer: Xinghuo Zhaidu, RSA 4096, certificate SHA-256 `26cbe74325edda68654627cc1fdc7a71c0a4ce14f3b4875ded635ca0a23ba411`, APK Signature Scheme v2.
- Release package metadata: `com.xuhuangbin.xinghuozhaidu`, version code `4`, version name `1.3.0`.
- Current `1.3.0` personal APK: `app/build/outputs/apk/personal/app-personal.apk`, 21,152,551 bytes, SHA-256 `78e5942b9a5029229e2db9417fcbf96163e3461d86fe98a2c397e734646cd022`.
- Personal signer: Android Debug, RSA 2048, certificate SHA-256 `627f7ff3e7d35d0af6f7399dcd9dd1cdee2ad4905d3f3f55cf95754c5c4e1f57`, APK Signature Scheme v2.
- Reproducible Gradle output: `app/build/outputs/apk/personal/app-personal.apk` from `./gradlew :app:assemblePersonal`.
- Versioned local personal APK: `dist/xinghuo-zhaidu-v1.1.0-personal.apk` remains the prior `1.1.0` delivery and was not overwritten by this increment.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 78,551,339 bytes, SHA-256 `c5b69092f120d0083aa7826e0a4bea3d14e8efb421458dd7ff5fc3b790f6a519`.
- Bundled bootstrap: `app/src/main/assets/bootstrap.zip`, 2,537,118 bytes, SHA-256 `de93d59e78609d4895e04be1dbbde6e6d1d99b0cb1c08098f637b9e153b764a5`.
- Local content package: `dist/content-v1.3.0.zip` (ignored build output)
- Local manifest: `dist/manifest.json` (ignored build output)

## Content Release

- Release tag: `content-v1.3.0`.
- Release page: `https://github.com/Hi-prof/maoxuan/releases/tag/content-v1.3.0`.
- Latest manifest: `https://github.com/Hi-prof/maoxuan/releases/latest/download/manifest.json`.
- The tag workflow validated Python 3.12 compatibility and formal content before uploading `content-v1.3.0.zip` and `manifest.json`; both public assets were downloaded and independently verified.
- App Release: `https://github.com/Hi-prof/maoxuan/releases/tag/app-v1.3.0` (non-latest).
- A production signing key exists only under the user's local Android directory and encrypted GitHub Secrets; no signing material is stored in the repository.
