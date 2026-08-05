# 赤印文摘视觉优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用历史档案照片和统一的无缩略图摘要列表完成“赤印文摘”视觉改造，同时打通图片署名与分享许可信息。

**Architecture:** 保持内容包 schema 3 与 Room schema 7 不变，复用 `ImageAssetEntity` 现有授权字段并通过 `AppRepository` 投影到新增领域值。UI 改动集中在共享卡片/摘要组件和两个页面，内容改动以新图片 ID、显式卡片映射和单调 revision 完成。

**Tech Stack:** Kotlin 2.x、Jetpack Compose Material 3、Room、Coil、Python 3、PyYAML、Pillow、Wikimedia Commons API、Gradle/JUnit/Compose UI Test。

## Global Constraints

- 最小视口为 `360 x 640 dp`，名言最长 90 个 Unicode code point。
- 卡片圆角保持 `8.dp`，四个图标操作保持 `48.dp` 点击目标，操作区仍在卡片外。
- 固定浅色主题；不添加渐变、仿古纹理、嵌套装饰卡片或压过文字的背景照片。
- 内容图片 ID 不可变；替换字节必须使用新 ID。
- 已发布卡片改变 `imageId` 时 revision 必须增加，内容版本必须单调升级。
- 不提交 `dist/`、APK、缓存、虚拟环境或本地数据库。

---

### Task 1: 建立档案照片清单与可分发素材

**Files:**
- Create: `.trellis/tasks/08-05-card-list-ui-refresh/research/archive-photo-manifest.md`
- Create: `content/images/archive-*.jpg`
- Create: `content/images/archive-*.yaml`
- Modify: `content/project.yaml`

**Interfaces:**
- Produces: 约 24 个稳定 `archive-<series>-<theme>-<index>` 图片 ID；每个 YAML 提供现有内容协议要求的八个字段。

- [x] 使用 Commons API 搜索设计文档列出的 24 个系列/主题槽位，记录候选的来源页、原图 URL、作者、许可和许可证据。
- [x] 只接受允许展示和分享再分发的素材；排除授权不明、仅限非商业使用、关键史实不匹配或分辨率低于 `720 x 720` 的候选。
- [x] 用 Pillow 保持纵横比裁切并缩放为统一竖版 JPG；保留清晰主体，不烘焙文字、渐变或仿古纹理，单文件小于 5 MiB。
- [x] 为每张图写入元数据 YAML，`sourceUrl` 指向稳定来源页，`licenseEvidence` 指向具体许可依据，`verifiedAt` 使用 `2026-08-05`。
- [x] 写研究清单，逐项记录主题槽位、最终图片 ID、选择理由和潜在误读检查结果。
- [x] 运行 `$env:PYTHONPATH='content-tool/src'; python -m xinghuo_content validate content`，确认新增图片可解码且授权字段有效。

### Task 2: 映射图片署名到领域与分享流程

**Files:**
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/model/Models.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/AppRepository.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/components/InterpretationSheet.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/share/ShareCardRenderer.kt`
- Test: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/data/AppRepositoryInstrumentedTest.kt`
- Test: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/components/InterpretationInstrumentedTest.kt`
- Test: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/share/ShareCardRendererInstrumentedTest.kt`

**Interfaces:**
- Produces: `ImageAttribution`; `QuoteCard.imageAttribution: ImageAttribution?`; `ShareCardRenderer.attributionText(card): String?`。

- [x] 先添加仓库投影测试，要求导入图片的作者、来源、许可和许可证据出现在 `QuoteCard.imageAttribution`。
- [x] 新增 `ImageAttribution` 并将 repository 的图片路径映射升级为完整 `ImageAssetEntity` 映射；缺失图片返回空路径和空署名。
- [x] 在背景 sheet 添加不嵌套卡片的“图片来源与许可”段落，来源页和许可页分别可点击并带中文 content description。
- [x] 在分享图底部绘制 `图片：<creator> · <license>`；分享 Intent 的 `EXTRA_TEXT` 附带完整 `sourceUrl` 与 `licenseEvidence`。
- [x] 运行 `\.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`，确保领域签名和所有测试夹具兼容。

### Task 3: 实现共享赤印摘要组件

**Files:**
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/components/CardSummaryList.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/theme/Theme.kt`
- Create: `app/src/test/java/com/xuhuangbin/xinghuozhaidu/ui/components/CardSummaryListTest.kt`
- Create or modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/components/CardSummaryListInstrumentedTest.kt`

**Interfaces:**
- Produces: `internal fun summaryIndexLabel(card: QuoteCard): String` 与 `internal fun summaryDateLabel(authoredAt: String): String`。

- [x] 写 JVM 测试覆盖四卷、诗词、马原、名言、未知系列、现代完整日期和 `前4世纪`。
- [x] 删除 `AsyncImage`、图片容器和逐项外框，改为索引列、`2.dp` 朱红竖线、三层正文和底部分隔线。
- [x] 新增 `ArchiveGreen`，仅用于系列/日期辅助层级，不改变 Material 主色。
- [x] 添加 Compose 边界测试，断言 `360.dp` 宽度下索引、名言、篇名和已下架状态可见且不重叠。
- [x] 运行 `\.\gradlew.bat :app:testDebugUnitTest --no-daemon`。

### Task 4: 优化主卡片、搜索和收藏/点赞页面

**Files:**
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/components/QuoteCard.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/search/SearchScreen.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/saved/SavedScreens.kt`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/components/QuoteCardInstrumentedTest.kt`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/search/SearchScreenInstrumentedTest.kt`

**Interfaces:**
- Consumes: Task 3 的摘要索引函数和共享列表。

- [x] 给真实照片增加低饱和 ColorMatrix 与统一纸白蒙层；正常卡片显示短系列索引，紧凑卡片隐藏索引。
- [x] 用 filled `TextField` 替代厚重 `OutlinedTextField`，保持 IME Search、自动聚焦和独立历史行为；增加 `找到 N 条`。
- [x] 将收藏/点赞分段控件收窄居中，使用收藏/点赞 Material 图标，并增加当前数量；保持两个独立 `LazyListState`。
- [x] 扩展 Compose 测试，验证 90 字紧凑卡片、结果数量、分段计数和现有手势合同。
- [x] 运行 `\.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon`。

### Task 5: 映射 600 张卡片并生成内容包

**Files:**
- Modify: `content/cards/*.yaml`
- Modify: `content/project.yaml`
- Modify: `app/src/main/assets/bootstrap.zip`
- Create: `.trellis/tasks/08-05-card-list-ui-refresh/research/image-mapping-report.md`

**Interfaces:**
- Consumes: Task 1 的图片 ID。
- Produces: 内容版本 `1.6.0`，600 张卡片显式引用系列/主题匹配图片。

- [x] 通过 PyYAML 读取每张卡片，以 `literature.series + themes` 选择确定图片 ID；只替换原文中的 `imageId` 行并保留其他 YAML 格式。
- [x] 每张改变 `imageId` 的 published 卡片 revision 增加 1；报告每个图片 ID 的引用数，并断言 600 张卡片全部映射、24 个目标槽位均有引用。
- [x] 将 `contentVersion` 更新为 `1.6.0`、`publishedAt` 更新为 `2026-08-05T00:00:00Z`，release notes 明确档案照片与 UI 署名支持。
- [x] 运行正式校验和确定性构建：`python -m xinghuo_content validate content --formal` 与 `python -m xinghuo_content build content --output dist --bootstrap-output app/src/main/assets/bootstrap.zip --formal --verify-deterministic`。
- [x] 检查 Git diff，确认除 `revision` 和 `imageId` 外没有批量重排 600 个卡片文件。

### Task 6: 全量质量门与设备视觉验证

**Files:**
- Create: `.trellis/tasks/08-05-card-list-ui-refresh/artifacts/` 下的截图和检查记录。

- [x] 运行 `$env:PYTHONPATH='content-tool/src'; python -m ruff check content-tool; python -m pytest content-tool`。
- [x] 运行 `\.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon`。
- [x] 在 API 28 与最新配置 API 运行 `\.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon`；若环境只有一个模拟器，明确记录未覆盖的 API。
- [x] 截取 `360 x 640`、`360 x 800`、`412 x 915` 的真实照片主卡片、搜索结果、收藏、点赞和缺图状态，检查文字、图标、索引和系统栏无重叠。
- [x] 用图片像素检查确认卡片背景非空但不压过正文，分享图严格为 `1080 x 1440` 且包含署名。
- [x] 完成 Trellis check、更新相关 spec、提交当前分支并归档任务。
