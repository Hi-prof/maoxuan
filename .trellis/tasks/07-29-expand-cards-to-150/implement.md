# 扩充毛选卡片至150条实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` to implement this plan inline, task by task. Do not dispatch sub-agents unless the user explicitly requests delegation.

**Goal:** 在保留现有 31 条内容的基础上新增 119 条经过来源和传播度核验的毛泽东经典名句，并生成包含精确 150 条正式卡片的内容包与 Android 内置快照。

**Architecture:** 保持现有 YAML -> Python 内容工具 -> 确定性 ZIP -> Android 严格消费者链路。新增一个实施期候选目录作为编审门槛，并在现有校验器与报告中补充完全重复和篇目计数检查，不扩展发布 schema。

**Tech Stack:** UTF-8 YAML、Python 3.11+、PyYAML、pytest、ruff、Kotlin、Android Gradle Plugin、Room、kotlinx.serialization。

## Global Constraints

- 最终正式卡片精确为 150 条：保留 31 条，新增 89 条著作或讲话名句和 30 条诗词名句。
- 作者仅限毛泽东；不纳入鲁迅或其他红色经典作者。
- 普通作品最多 3 条，目录批准的公认名篇最多 5 条，任何作品不得超过 5 条。
- 原句为不超过 90 code point 的 NFC 连续原文，不拼接、不改写、不用拆卡制造近重复。
- 每张正式卡片至少两个不同主机来源，且至少一个为 `original` 或 `authoritative`。
- 复用现有 8 张原创图片，不新增图片资产。
- 内容版本升级到 `1.4.0`，schema 保持 3，`minimumAppVersionCode` 保持 4；Android 应用升级到 `versionCode=5`、`versionName=1.4.0`。
- 不修改当前工作树中与 `CardActions` 单行布局相关的用户改动。
- 用户已明确批准提交到 GitHub并发布 1.4 APK；提交、标签和 Release 仅包含本任务范围，不纳入无关界面改动。

---

### Task 1: 固定119条候选目录

**Files:**
- Create: `.trellis/tasks/07-29-expand-cards-to-150/research/candidate-catalog.md`
- Read: `资料/01-毛泽东著作.md`
- Read: `资料/02-毛泽东诗词.md`
- Read: `.trellis/tasks/07-29-expand-cards-to-150/research/popular-quote-sources.md`

**Produces:** 119 条状态为 `accepted` 的目录记录，其中 `prose=89`、`poetry=30`，每条拥有作品、日期、传播度 URL、具体原文 URL和不同主机交叉来源 URL。

- [x] 从本地资料和五个机构传播度页面整理 180 条以上初始候选，先排除现有 31 条原句。
- [x] 对每个候选回查具体作品与连续上下文，记录系列、卷次、写作日期和三个证据 URL；无法定位具体作品的候选标为 `rejected`。
- [x] 计算 NFC 后长度并排除超过 90 code point、需要拼接或截断才能成立的候选。
- [x] 去除完全重复、去标点后包含关系明显且语义相同的候选，并标记需要人工判断的相似对。
- [x] 按传播度和阅读价值排序，固定 89 条 `prose` 与 30 条 `poetry`；检查普通作品最多 3 条、公认名篇最多 5 条。
- [x] 逐行复核 119 条接受项不存在空字段、版本待核或只有单一主机来源的情况。

### Task 2: 增加重复校验与篇目报告

**Files:**
- Modify: `content-tool/src/xinghuo_content/validator.py`
- Modify: `content-tool/src/xinghuo_content/report.py`
- Modify: `content-tool/tests/test_content_tool.py`

**Produces:** 正式内容完全重复原文会被拒绝；内容报告包含 `workTitles` 计数。

- [x] 在 `_write_fixture` 基础上新增测试：复制第二张 `published` 卡并只改变 UUID，断言 `validate_content` 报告重复原文及两个文件名。
- [x] 运行 `python -m pytest content-tool -k duplicate_published_quote -v`，确认测试先失败。
- [x] 在 `validate_content` 收集 `published` 卡的 NFC `quote` 到源文件映射，对出现两次以上的原文追加确定性排序的校验问题。
- [x] 扩展报告测试，断言单卡报告包含 `"workTitles": {"实践论": 1}`。
- [x] 在 `build_content_report` 使用现有 `_counts` 生成按 `workTitle` 排序的 `workTitles`。
- [x] 运行 `python -m pytest content-tool -v` 和 `python -m ruff check content-tool`，确认通过。

### Task 3: 编写89条著作与讲话卡片

**Files:**
- Create: `content/cards/032-*.yaml` through `content/cards/120-*.yaml`（每个编号恰好一个文件）
- Read: `content/templates/card.yaml`
- Read: `.trellis/tasks/07-29-expand-cards-to-150/research/candidate-catalog.md`

**Produces:** 89 张 schema 3 的 `published` 著作或讲话卡片。

- [x] 按目录编号 032 至 061 编写第一批 30 张卡；每张使用新 UUID、`revision: 1`、完整 literature、双来源、解释和背景字段。
- [x] 临时将 `expectedPublishedCards` 维持 31，运行非正式 `python -m xinghuo_content validate content`，修复第一批所有字段、长度、主机和图片引用错误。
- [x] 按目录编号 062 至 091 编写第二批 30 张卡，重复非正式校验。
- [x] 按目录编号 092 至 120 编写第三批 29 张卡，重复非正式校验。
- [x] 解析全部 YAML，确认新增 prose 恰为 89、编号无缺口、UUID 无重复、所有状态为 `published` 且 review 为 `verified`。
- [x] 审阅同篇多卡的 `background`、`story` 和 `explanation`，删除机械复制或把不同原句解释成同一个意思的内容。

### Task 4: 编写30条诗词卡片

**Files:**
- Create: `content/cards/121-*.yaml` through `content/cards/150-*.yaml`（每个编号恰好一个文件）
- Read: `资料/02-毛泽东诗词.md`
- Read: `.trellis/tasks/07-29-expand-cards-to-150/research/candidate-catalog.md`

**Produces:** 30 张 schema 3 的 `published` 毛泽东诗词卡片。

- [x] 按目录编号 121 至 135 编写第一批 15 张诗词卡，以选定权威版本统一简繁、异体字和标点。
- [x] 运行非正式 `python -m xinghuo_content validate content`，修复字段与长度问题。
- [x] 按目录编号 136 至 150 编写第二批 15 张诗词卡并再次运行非正式校验。
- [x] 对同一首诗词的多张卡检查上下句边界，确保每张语义独立且不是重叠拆分。
- [x] 解析全部 YAML，确认新增 poetry 恰为 30、编号无缺口、UUID 无重复、来源主机独立。

### Task 5: 执行全量编辑审计

**Files:**
- Modify: `.trellis/tasks/07-29-expand-cards-to-150/research/candidate-catalog.md`
- Inspect: `content/cards/*.yaml`

**Produces:** 候选目录的 119 条接受项与 119 个新 YAML 一一对应，重复和作品上限审计无未决项。

- [x] 用 PyYAML 读取 150 张发布卡，输出总数、prose/poetry 数、UUID 数、quote 数、workTitle 计数和图片使用计数。
- [x] 对原句去除 Unicode 标点和空白后做完全相等、包含关系及 `difflib.SequenceMatcher >= 0.82` 审计，逐对记录“保留”或“替换”结论。
- [x] 检查 `workTitles`：普通作品不超过 3，目录标记的公认名篇不超过 5，任何作品不超过 5。
- [x] 随机抽查每类至少 10 条并对全部风险标记项打开原文 URL，确认原句、篇名、日期和解释边界。
- [x] 将所有接受项状态更新为 `verified`；目录存在任一 `accepted`、`needs-review` 或缺失 URL 时不得进入版本构建。

### Task 6: 更新版本、README和内置内容测试

**Files:**
- Modify: `content/project.yaml`
- Modify: `README.md`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/data/AppRepositoryInstrumentedTest.kt`

**Produces:** 源内容声明 1.4.0/150，文档与内置包测试期望同步。

- [x] 将 `content/project.yaml` 更新为 design.md 第 6 节定义的五个精确值。
- [x] 将 README 的“31 张正式卡片”更新为“150 张正式卡片”，将内容编辑与发布示例改为 `1.4.0`，保留实际 GitHub Release 状态描述不冒充已发布。
- [x] 将 `initializeInstallsNewerBundledContentWithoutLosingPersonalState` 对内置内容版本的断言从 `1.3.0` 更新为 `1.4.0`。
- [x] 运行正式校验，期望输出 `publishedCards: 150`、`withdrawals: 0`、`images: 8`。

### Task 7: 构建内容报告与Android内置快照

**Files:**
- Modify: `app/src/main/assets/bootstrap.zip`
- Generate, ignored: `dist/content-report-1.4.0.json`
- Generate, ignored: `dist/content-v1.4.0.zip`
- Generate, ignored: `dist/manifest.json`

**Produces:** 确定性内容 1.4.0 ZIP、清单、报告和同字节 Android 内置 ZIP。

- [x] 运行 `python -m xinghuo_content report content --output dist/content-report-1.4.0.json --formal`。
- [x] 检查报告：`publishedCards=150`、所有阅读字段完整、`quoteLengths.maximum<=90`、`interpretations.complete=150`、8 张图片均被引用、`workTitles` 符合上限。
- [x] 运行带 `--bootstrap-output app/src/main/assets/bootstrap.zip --formal --verify-deterministic` 的正式构建命令。
- [x] 用 SHA-256 比较 `app/src/main/assets/bootstrap.zip` 和 `dist/content-v1.4.0.zip`，必须完全相同。
- [x] 解压只读检查 `package.json` 为 schema 3/content 1.4.0，`cards.json` 恰有 150 条。

### Task 8: 完整质量门禁

**Files:**
- Verify only: all task files

**Produces:** Python 内容工具、正式内容、Android 单元测试、lint 和 debug 构建均通过。

- [x] 运行 `python -m ruff check content-tool`。
- [x] 运行 `python -m pytest content-tool`。
- [x] 运行 `python -m xinghuo_content validate content --formal`。
- [x] 再运行一次确定性构建并确认 SHA-256 与 manifest 字节不变。
- [x] 运行 `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon`。
- [ ] 若有可用 API 28 和最新 API 模拟器，运行 `.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon`；否则在交付说明中记录未运行原因。（本机 `adb devices` 无可用设备，未运行。）
- [x] 运行 `git diff --check`，检查 `git status --short`，确保没有修改用户正在进行的 `CardActions` 相关文件，也没有把 `dist/`、缓存或临时检索输出加入 Git。
- [x] 汇总改动、验证和遗留问题；如用户要求提交，再按内容、工具和任务文档范围列出待提交文件并请求最终提交许可。

### Task 9: 发布内容与正式签名 APK

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/app-release.yml`
- Modify: `README.md`

**Produces:** GitHub 上可下载、可校验且可覆盖升级的 1.4.0 正式 APK；latest 仍指向内容 Release。

- [x] 将 Android 版本升级到 `versionCode=5`、`versionName=1.4.0`，同步 App Release 工作流版本断言。
- [ ] 重跑内容、Android 和 Trellis 完整质量门禁，仅提交本任务文件并推送 `main`。
- [ ] 推送 `content-v1.4.0` 标签，等待工作流成功并校验公开内容包与 manifest。
- [ ] 推送 `app-v1.4.0` 标签，等待工作流成功并校验 APK 签名、包名、版本、SHA-256 及 non-latest 状态。
- [ ] 更新发布记录并完成 Trellis 归档，不提交用户的 `CardActions` 相关改动。
