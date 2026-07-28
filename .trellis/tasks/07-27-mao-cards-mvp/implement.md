# 星火摘读 MVP 实施计划

## Gate

- 用户已审阅并批准 A 方案，任务已通过 `task.py start` 进入实施阶段。
- 本地实现与质量门禁已完成；逐项结果、产物和未执行外部操作见 `delivery.md`。
- 开发前加载 `trellis-before-dev`，读取 Android、Python 内容工具和跨层相关 spec。
- 不创建公开 GitHub 仓库、不推送标签、不发布 Release、不签名正式 APK，除非用户在对应步骤明确同意外部操作。

## Definition Of Done

MVP 完成必须同时满足以下结果：

- Android 9 及以上可安装运行的“星火摘读”应用。
- APK 内置 30 张双源核验、出处清晰、图片许可满足分享要求的正式卡片。
- 阅读轮次、翻面、点赞、收藏、笔记、搜索、分享图和四栏导航按 PRD 工作。
- YAML 内容工具能够校验、构建确定性完整内容包。
- Android 能从 GitHub Releases 手动检查、确认、下载、校验并原子应用内容更新。
- GitHub Actions 能在合法 `content-vX.Y.Z` 标签上验证并构建 draft Release，在 assets 完整后发布。
- 所需 lint、编译、单元测试、集成测试和目标尺寸视觉检查通过。

## Phase 0: Execution Prerequisites

- [ ] 获取用户对全部规划文件和开始实现的明确批准。
- [ ] 运行 Trellis `task.py start`，确认状态从 `planning` 变为 `in_progress`。
- [ ] 安装 JDK 17，并确认 Gradle 使用该 JDK，而不是当前默认 Java 8。
- [ ] 安装 Android Emulator、API 28 与最新 API 系统镜像，或连接对应实体设备。
- [ ] 确认 Android SDK 路径可由 `local.properties` 或环境变量读取，且不提交机器绝对路径。
- [ ] 确认默认应用 ID `com.xuhuangbin.xinghuozhaidu`，并预留 GitHub owner/repository 的 BuildConfig 配置。
- [ ] 读取 `.trellis/spec/`；在项目模式出现后补齐 `00-bootstrap-guidelines`，不得用空模板替代真实约定。

Validation:

```powershell
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -version
adb version
adb devices -l
python --version
```

## Phase 1: Repository And Android Bootstrap

- [ ] 创建 Gradle Wrapper、Kotlin DSL 根配置、version catalog 和单一 `app` 模块。
- [ ] 设置 minSdk 28、稳定 compile/target SDK、Java/Kotlin toolchain 17 和固定浅色主题。
- [ ] 配置 Compose、Navigation、Room、Serialization、OkHttp、Coil 和测试依赖。
- [ ] 建立应用容器、单 Activity、四栏导航骨架和无业务逻辑的页面占位。
- [ ] 添加 `.gitignore` 规则，排除 SDK 路径、Gradle 缓存、构建产物、Python 环境、`dist/` 和本地签名材料。
- [ ] 添加自适应图标基础资源、应用名和无权限启动清单。
- [ ] 建立 Android CI 检查但暂不创建 Release。

Validation:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
```

Rollback point: 保持 bootstrap 只包含可编译空壳；依赖或插件不兼容时逐项回退版本，不修改用户全局 Gradle 配置。

## Phase 2: Content Tool And Contract

- [ ] 创建 `content-tool` Python 包、`pyproject.toml`、CLI 入口和 pytest fixtures。
- [ ] 定义 card、source、image、withdrawal、package 和 remote manifest 的严格模型。
- [ ] 添加一张卡一个 YAML、可复用图片元数据和首批版本元数据模板。
- [ ] 实现 Unicode NFC、90 字限制、UUID/revision、双源、出处、日期、URL 和许可校验。
- [ ] 实现图片解码、尺寸、格式、哈希、来源和 `shareAllowed` 校验。
- [ ] 实现 published/withdrawn/restore 状态规则和不可复用 ID 检查。
- [ ] 构建排序稳定、时间戳固定的 JSON 与 ZIP，并输出 SHA-256、大小和变更摘要。
- [ ] 生成供 Android 测试使用的正常包和一组恶意/损坏 fixtures。
- [ ] 生成 `1.0.0` bootstrap 包的占位开发版本，先用明确标记的测试内容，不冒充正式内容。

Validation:

```powershell
python -m ruff check content-tool
python -m pytest content-tool
python -m xinghuo_content validate content
python -m xinghuo_content build content --output dist
```

Quality gate: 连续构建两次的 ZIP 和 JSON 哈希完全一致；Android 共享 fixture 能严格解析。

## Phase 3: Local Persistence And Bootstrap Import

- [ ] 实现 Room entities、DAO、database、事务和第一版 migration test。
- [ ] 实现活动卡、来源、内容寻址图片、用户状态、轮次、下架和内容版本表。
- [ ] 实现发布 JSON 到 Room 模型的显式映射，不把网络 DTO 直接暴露给 UI。
- [ ] 实现应用首次启动时导入 APK 内置 `1.0.0` 内容包。
- [ ] 导入失败时提供可诊断错误，并保留重试能力，不创建半初始化数据库。
- [ ] 实现 asset store、staging 和未引用文件清理。
- [ ] 使用开发 fixture 验证新增、修订、下架、恢复和用户状态保留。

Validation:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Rollback point: Room 迁移不得使用 destructive fallback；数据契约变更先更新 fixture 和 migration test。

## Phase 4: Reading Round Engine

- [ ] 实现首轮随机顺序生成并完整持久化。
- [ ] 实现当前位置、双向回看、已读时间和轮次完成状态。
- [ ] 用可注入 clock 实现连续 3 秒已读规则。
- [ ] 实现应用后台、离开阅读 tab、快速划过和重新返回的计时语义。
- [ ] 实现同步新增卡插入未来未读区、修订不新增、下架隐藏和恢复发布。
- [ ] 实现完成页统计和明确“开始新一轮”操作。
- [ ] 覆盖空库、仅一张、全部下架、完成后新增和进程重启等边界。

Validation:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ReadingRound*"
```

## Phase 5: Approved Visual System And Reader UI

- [ ] 将 `reference-card-system-v6` 转换为 Compose 颜色、间距、圆角和排版 token。
- [ ] 选择开放许可中文衬线字体，提交字体许可文本；UI 使用系统无衬线字体。
- [ ] 实现短、中、长三档字号和最小目标屏幕的稳定卡片约束。
- [ ] 实现低对比度背景水印、浅灰页面背景和卡片外操作区。
- [ ] 实现 VerticalPager、完整落页、正反面翻转和背面可滚动布局。
- [ ] 背面打开时关闭 pager 手势，并提供明确翻回操作。
- [ ] 实现参考来源折叠区和系统浏览器跳转。
- [ ] 实现点赞、收藏状态与即时持久化。
- [ ] 实现虚拟轮次完成页，不自动跳过最后一张卡片。
- [ ] 完成星火与书页自适应图标，并验证常见 launcher mask。

Visual gate: 对短文、中等正文、90 字正文、最长出处、长背面和可选栏目缺失分别截图，不允许文字截断、按钮遮挡、尺寸跳动或背景图片缺失。

## Phase 6: Navigation, Lists And Search

- [ ] 完成“阅读 / 收藏 / 笔记 / 我的”四栏底部导航和各自状态保存。
- [ ] 实现“收藏 / 点赞”同页分段列表，分别按 `favoritedAt` 与 `likedAt` 倒序。
- [ ] 实现“我的”版本信息和检查更新区域；点赞列表不再放在“我的”。
- [ ] 实现摘要缩略图、两至三行正文、篇名和下架标记。
- [ ] 实现单卡详情及返回列表位置恢复，详情不改变主阅读位置。
- [ ] 实现仅匹配活动卡正文和篇名的本地搜索、清空、空结果和输入法状态。
- [ ] 为最近搜索新增 Room 表和显式 `1 -> 2` migration，保留既有内容及个人状态。
- [ ] 实现仅在输入法 Search action 上保存去空白关键词、忽略大小写去重置顶和最近 10 条裁剪。
- [ ] 搜索框为空时展示历史记录，支持点击回填、单条删除和全部清空；历史操作通过 Repository 与 ViewModel 明确事件完成。
- [ ] 添加持久化搜索历史的 instrumentation 回归，覆盖空白忽略、去重置顶、数量上限、单条删除和全部清空。
- [ ] 验证一级 tab 切换后阅读卡片、列表位置和搜索返回栈均正确。

Validation:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Phase 7: Fixed Share Image

- [ ] 实现与屏幕尺寸无关的 1080 x 1440 离屏渲染器。
- [ ] 复用字体档位、纸张色、背景水印和准确出处格式。
- [ ] 只绘制名言、出处、背景和小号“星火摘读”，不绘制 UI 控件或来源链接。
- [ ] 写入内部分享缓存，通过 FileProvider 调用 Android Sharesheet。
- [ ] 不申请媒体或外部存储权限，取消分享不改变用户状态。
- [ ] 实现缓存数量/时间清理及渲染错误状态。

Validation:

- [ ] 输出像素严格为 1080 x 1440。
- [ ] 画布像素检查确认不是空白图，背景确实存在。
- [ ] 90 字正文、最长篇名和出处均不越界。
- [ ] 至少在两个 Android API 级别调起系统分享面板。

## Phase 8: Manual Update Pipeline

- [ ] 实现 latest manifest 请求、严格解析、语义版本和最小 App 版本判断。
- [ ] 实现“检查中 / 已是最新 / 可更新 / 下载中 / 校验中 / 成功 / 失败”状态。
- [ ] 实现更新摘要，未经二次确认不下载。
- [ ] 流式下载到内部 staging，支持取消并报告清晰错误。
- [ ] 实现字节数、SHA-256、ZIP 限额、路径穿越、资源哈希和严格 schema 校验。
- [ ] 实现内容寻址资源落盘和 Room 单事务切换。
- [ ] 实现新增、修订、显式下架、恢复及当前轮次调整。
- [ ] 实现下架快照在收藏/点赞/关联笔记中保留，以及取消最后状态并删除最后关联笔记后的清理。
- [ ] 确认 App 启动、进入后台和普通页面切换不会产生网络请求。

Validation:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ContentUpdate*"
.\gradlew.bat :app:connectedDebugAndroidTest
```

Failure matrix: 无网、DNS/HTTPS 失败、404、超时、用户取消、空间不足、错哈希、ZIP 截断、未知 schema、版本降级、非法 revision、DB 事务异常和进程重启。

## Phase 9: First Formal Content Set

- [ ] 建立主题与卷次分布表，避免 30 张集中在少数文章或网络热句。
- [ ] 为每条名言保存连续原文、准确系列/卷次/篇名/时间和不少于两个来源。
- [ ] 确保至少一个来源能追溯原文或权威出版信息，记录访问日期和核验状态。
- [ ] 原创撰写可选背景、上下文和故事，不复制网络说明文。
- [ ] 为每张卡片选择可复用背景图，并保存作者、来源、许可或公版证据。
- [ ] 只采用允许 App 展示和分享图再分发的图片；模糊许可一律不用。
- [ ] 对每张卡执行人工连续引文、双源独立性、出处和图片许可复核。
- [ ] 用正式内容重新构建 bootstrap `1.0.0`，替换开发 fixture，但保留测试 fixture。
- [ ] 生成主题、来源、长度、图片复用和许可报告供最终审阅。

Content gate: 30 张全部通过自动校验和人工复核后，才能把 `1.0.0` 标记为正式 bootstrap。

## Phase 10: GitHub Actions And Release Dry Run

- [ ] 普通 push/PR 工作流运行 Python 校验、Android lint、编译和测试，不创建 Release。
- [ ] 标签工作流只接受与源内容版本一致的 `content-vX.Y.Z`。
- [ ] 在 draft Release 中上传版本 ZIP 和 `manifest.json`，所有 assets 完整后再发布。
- [ ] 使用最小 `contents: write` 权限，不使用长期个人访问令牌。
- [ ] 验证 Release assets 的公开 URL、大小和 SHA-256 与 manifest 一致。
- [ ] 在本地或临时 fixture 标签上验证工作流定义；未经用户确认不推送真实发布标签。
- [ ] 配置实际 GitHub owner/repository 后，在 App 中验证 latest asset 地址。

Rollback point: 发布错误内容时创建更高 patch 版本恢复可信快照，不覆盖历史 Release 或要求客户端降级。

## Phase 11: Full Quality Gate

- [ ] 运行内容工具格式、lint、测试、正式内容校验和确定性构建。
- [ ] 运行 Android compile、unit tests、lint 和 instrumentation tests。
- [ ] API 28 和最新 API 各完成首次离线启动、阅读、翻面、状态持久化和更新流程。
- [ ] 在 360 x 640、360 x 800、412 x 915 dp 检查所有核心截图。
- [ ] 系统浅色和深色设置下都验证固定浅色 App、状态栏与导航栏图标。
- [ ] 检查无网、GitHub 不可达、更新取消和错误内容包时旧内容可继续阅读。
- [ ] 检查下架、恢复发布、轮次完成、新增内容和分享缓存。
- [ ] 扫描 APK 权限，确认没有账号、位置、通知或外部存储权限。
- [ ] 检查仓库不包含个人数据、密钥、签名文件、构建输出或来源不明图片。
- [ ] 更新 README，写清构建、内容编辑、校验、发布、回滚和已知版权边界。

Final commands, subject to the generated project task names:

```powershell
python -m ruff check content-tool
python -m pytest content-tool
python -m xinghuo_content validate content --formal
python -m xinghuo_content build content --output dist --verify-deterministic
.\gradlew.bat clean check
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
git diff --check
git status --short
```

## Review And Finish

- [x] 运行 `trellis-check` 并修复所有阻塞问题。
- [x] 将真实、可复用的 Android 与内容工具约定更新到 `.trellis/spec/`。
- [x] 汇总 APK 路径、内容报告、截图、测试结果和尚未执行的真实发布步骤。
- [ ] 按项目规则在提交前询问用户，不提交无关文件。
- [ ] 用户确认提交后才创建提交；用户确认公开发布后才推送远端和内容标签。

## Highest-risk Areas

- 历史引文和图片许可的人工核验不能由自动化替代。
- 背面滚动与外层纵向 pager 的手势竞争必须在真机验证。
- Room 与文件系统的更新需要严格按“资源先落盘、数据库后切换”执行。
- `releases/latest` 依赖仓库不发布其他类型 Release；未来 APK Release 必须先拆分稳定内容通道。
- 当前机器没有 JDK 17、Android Emulator 或已连接设备，这些必须在声称 Android 验证通过前解决。

## Increment: Persistent Search History

### Task 1: Room persistence and migration

**Files:**

- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/Entities.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/AppDao.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/XinghuoDatabase.kt`
- Update exported Room schema under `app/schemas/`

**Steps:**

- [x] Add `SearchHistoryEntity` with an auto-generated ID, a case-insensitive unique keyword, and `searchedAt`.
- [x] Add observable, insert/replace, trim-to-limit, delete-one, and delete-all DAO operations.
- [x] Raise the database version to 2 and register an explicit migration that only creates the new table and index.
- [x] Build with KSP to export schema version 2 and verify migration SQL matches the entity contract.

### Task 2: Repository and ViewModel state

**Files:**

- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/AppRepository.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/MainViewModel.kt`

**Steps:**

- [x] Expose search history as `Flow<List<String>>` from the repository and immutable `StateFlow` from the ViewModel.
- [x] Save only trimmed, non-empty submitted queries; replace case-insensitive duplicates and prune in one Room transaction to 10 rows.
- [x] Add explicit ViewModel events for submit, delete one, and clear all without coupling UI components to Room.

### Task 3: Search interaction and history UI

**Files:**

- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/search/SearchScreen.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/XinghuoApp.kt`

**Steps:**

- [x] Configure the text field for the IME Search action and call the submit event only from that action.
- [x] When the query is blank, replace the result placeholder with a recent-history list headed by a clear-all action.
- [x] Make a history row fill the query without saving it; provide an accessible per-row delete icon and keep existing result navigation unchanged.

### Task 4: Regression and quality gate

**Files:**

- Modify `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/data/AppRepositoryInstrumentedTest.kt`
- Create or modify search UI instrumentation tests only where behavior is stable and emulator execution is available.

**Steps:**

- [x] Verify blank submissions are ignored, case-insensitive duplicates move to the front, and the 11th unique search removes the oldest.
- [x] Verify single deletion and clear-all update observed history immediately.
- [x] Run `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon`.
- [x] Run `./gradlew.bat :app:connectedDebugAndroidTest --no-daemon` on API 28 and API 35.
- [x] Inspect the final scoped files and check whitespace without committing; Git diff is unavailable because the repository has no initial commit and all files remain untracked.

## Increment: Offline Card Interpretation

### Task 1: Extend and validate the content contract

**Files:**

- Modify `content/templates/card.yaml`
- Modify all active card files under `content/cards/`
- Modify `content/project.yaml`
- Modify `content-tool/src/xinghuo_content/validator.py`
- Modify `content-tool/src/xinghuo_content/builder.py`
- Modify `content-tool/src/xinghuo_content/report.py`
- Modify `content-tool/tests/test_content_tool.py`

**Interfaces:**

- Produce YAML and JSON `interpretation` objects with required `coreMeaning`, `keyPoint`, and `contemporaryRelevance` strings.
- Produce package schema version 2 while keeping remote manifest schema version 1.

**Steps:**

- [x] Add failing Python tests for a missing interpretation object, each missing or blank child field, total text above the hard limit, schema version 2 output, and deterministic builds.
- [x] Implement strict interpretation validation with normalized, trimmed strings, a 600-code-point combined hard limit, and field-specific error paths; reject every published card that does not contain all three parts.
- [x] Update the deterministic builder and report to emit/count schema version 2 interpretations without changing manifest schema version 1.
- [x] Write natural 200～300-character interpretations for every active card, based only on its quote, work metadata, existing context/background/story, and recorded sources; raise every changed card revision.
- [x] Raise content version to `1.1.0`, update the card template, rebuild `app/src/main/assets/bootstrap.zip`, and verify every active card is covered.
- [x] Run `python -m ruff check content-tool`, `python -m pytest content-tool`, `python -m xinghuo_content validate content --formal`, and two deterministic package builds.

### Task 2: Carry interpretation through Android storage

**Files:**

- Modify `app/build.gradle.kts`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/content/ContentDtos.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/content/ContentPackageReader.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/Entities.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/XinghuoDatabase.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/AppRepository.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/model/Models.kt`
- Update exported Room schema under `app/schemas/`

**Interfaces:**

- Add serializable `InterpretationDto(coreMeaning, keyPoint, contemporaryRelevance)` to `CardDto`.
- Add immutable `CardInterpretation(coreMeaning, keyPoint, contemporaryRelevance)` to `QuoteCard`.
- Persist the three values in explicit non-null `CardEntity` columns.

**Steps:**

- [x] Add failing parser tests for schema version 2 interpretation decoding and blank/oversized values, plus repository tests proving bootstrap refresh preserves likes, favorites, round order, read timestamps, and position.
- [x] Add a failing Room migration regression that starts from schema 2, inserts card and user/round/search-history state, migrates to schema 3, and verifies both the new columns and all prior state.
- [x] Implement schema version 2 DTO/semantic validation and explicit DTO -> entity -> domain mapping; no UI code may parse serialized values.
- [x] Raise Room to version 3, register `MIGRATION_2_3` alongside `MIGRATION_1_2`, export schema 3, and keep empty migration defaults out of successfully mapped domain values.
- [x] Update initialization to import the bundled package when it is newer than installed local content, never when it would downgrade, and reuse the existing atomic package transaction.
- [x] Raise App metadata to `versionCode = 2` and `versionName = "1.1.0"`, and set the content manifest minimum App version to 2 so schema-1 clients reject schema-2 packages before download.

### Task 3: Add the shared interpretation interaction

**Files:**

- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/components/CardActions.kt`
- Create `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/components/InterpretationSheet.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/reader/ReaderScreen.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/detail/CardDetailScreen.kt`

**Interfaces:**

- Extend `CardActions` with `onInterpret: () -> Unit`.
- Add `InterpretationSheet(card: QuoteCard, onDismissRequest: () -> Unit, modifier: Modifier = Modifier)`.

**Steps:**

- [x] Add UI regressions that assert “解读” is immediately left of “读背景” at the `360 x 640` target, opens the current card, exposes all three section headings, scrolls long content, and dismisses without changing card or flip state.
- [x] Add the compact “解读” text action while retaining three accessible `48.dp` icon targets and the existing “读背景/返回正面” behavior.
- [x] Implement the shared Material 3 bottom sheet with work title, close action, three unframed sections, theme tokens, and independent scrolling.
- [x] Hoist only the selected sheet card as transient screen state in reader/detail; do not persist it or add ViewModel/Room UI state.
- [x] Verify modal gestures block pager movement and opening the sheet does not restart or cancel the current card's three-second read timer.

### Task 4: Full regression and visual gate

**Files:**

- Modify `app/src/test/java/com/xuhuangbin/xinghuozhaidu/data/content/ContentPackageReaderTest.kt`
- Modify `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/data/AppRepositoryInstrumentedTest.kt`
- Modify `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/data/local/XinghuoDatabaseMigrationInstrumentedTest.kt`
- Modify `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/components/QuoteCardInstrumentedTest.kt`
- Modify `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/share/ShareCardRendererInstrumentedTest.kt`
- Create or modify focused Compose instrumentation under `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/`
- Update `.trellis/tasks/07-27-mao-cards-mvp/artifacts/` with scoped screenshots and check output

**Steps:**

- [x] Run `python -m ruff check content-tool` and `python -m pytest content-tool`.
- [x] Run `python -m xinghuo_content validate content --formal` and `python -m xinghuo_content build content --output dist --verify-deterministic`.
- [x] Run `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon` with JDK 17.
- [x] Run `.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon` on API 28 and the latest configured API.
- [ ] Capture reader-front, reader-back, detail, short interpretation, and longest interpretation screenshots at `360 x 640`, `360 x 800`, and `412 x 915`; inspect action overflow, sheet scrolling, text clipping, and modal/pager gesture ownership.
- [x] Run `git diff --check`, review all changed content for placeholders or unsupported claims, and inspect the final scoped diff without committing.

## Increment: Interactive Horizontal Card Flip

- [x] 在共享 `FlippableQuoteCard` 中加入方向锁定的水平拖动，正反面均支持向左或向右翻转。
- [x] 以拖动距离实时驱动 Y 轴旋转、透视和侧面收窄，使用距离/速度阈值决定回弹或完成，并保留点击翻面入口。
- [x] 保证背面纵向滚动、阅读流纵向换卡、来源点击和无障碍自定义操作不受横滑影响。
- [x] 增加 Compose instrumentation 回归，覆盖左滑翻到背面、右滑返回正面和短拖回弹。
- [x] 在 API 28 与最新 API 的最小和常用视口上检查正反面动画、长背面滚动及主阅读流手势。

## Increment: Unified Saved Page And Personal Notes

### Task 1: Persist multiple standalone and card-linked notes

**Files:**

- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/Entities.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/AppDao.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/local/XinghuoDatabase.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/data/AppRepository.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/domain/model/Models.kt`
- Update exported Room schema under `app/schemas/`

**Steps:**

- [x] Add `NoteEntity(id, cardId?, title, body, createdAt, updatedAt)` plus indexes for card lookup and recent ordering; add immutable `PersonalNote` mapping.
- [x] Raise Room from 3 to 4 and register `MIGRATION_3_4` alongside the full `1 -> 2 -> 3 -> 4` chain without destructive fallback.
- [x] Add DAO/Repository flows and explicit create, update, and delete operations; trim title/body and reject blank bodies before persistence.
- [x] Make content withdrawal preserve a card snapshot while any note references it; after deleting the final linked note, remove a withdrawn snapshot only when neither liked nor favorited.
- [x] Add migration and repository instrumentation tests covering prior-data preservation, multiple notes per card, standalone notes, ordering, update, deletion, and withdrawn snapshot lifecycle.

### Task 2: Unify favorites and likes without merging state

**Files:**

- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/saved/SavedScreens.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/MainViewModel.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/XinghuoApp.kt`

**Steps:**

- [x] Replace separate favorites/mine list presentation with one “收藏与点赞” screen using a Material segmented control for independent “收藏 / 点赞” lists.
- [x] Preserve existing like/favorite state, sorting, detail navigation, withdrawn labels, per-segment selection and list position.
- [x] Reduce “我的” to content version, timestamps and manual update controls.
- [x] Change bottom navigation to “阅读 / 收藏 / 笔记 / 我的” while preserving reader state and top-level saved-state restoration.

### Task 3: Add note list, editor and card entry

**Files:**

- Create `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/notes/NotesScreen.kt`
- Create `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/notes/NoteEditorScreen.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/components/CardActions.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/reader/ReaderScreen.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/detail/CardDetailScreen.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/MainViewModel.kt`
- Modify `app/src/main/java/com/xuhuangbin/xinghuozhaidu/XinghuoApp.kt`

**Steps:**

- [x] Add an accessible `48.dp` note icon action to the shared card action row and navigate with the current card ID; keep all controls usable at `360 x 640 dp`.
- [x] Build the notes tab with empty state, updated-time ordering, standalone/card-linked summaries, withdrawn marker, editor navigation and a floating add action for standalone notes.
- [x] Build a full-screen plain-text editor with optional title, required body, explicit save, delete confirmation and unsaved-change confirmation on back.
- [x] Keep a saved note's card association immutable; allow linked-card summaries to open card detail without changing reader position.

### Task 4: Regression and visual gate

- [x] Run `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon`.
- [x] Run `./gradlew.bat :app:connectedDebugAndroidTest --no-daemon` on API 28 and the latest configured API.
- [x] Verify database migration `3 -> 4`, note CRUD/order/restart behavior, multiple notes per card and withdrawn snapshot retention/cleanup.
- [x] Inspect “收藏 / 点赞” switching, the four-item bottom bar, note empty/list/editor/delete/unsaved states and card actions at `360 x 640`, `360 x 800` and `412 x 915`.
- [x] Run `git diff --check` and review all scoped changes without committing or including unrelated untracked files.
