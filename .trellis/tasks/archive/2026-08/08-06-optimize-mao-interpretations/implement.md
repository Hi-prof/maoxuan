# 毛选卡片解读与启示优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: use the project Trellis execution workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 逐条审阅 220 张《毛泽东选集》卡片，保留达标内容，重写薄弱解读与启示，并交付可通过正式内容校验的 YAML。

**Architecture:** 不改变代码或数据结构。以卡片 YAML 为独立编辑单元，先形成可复核的修改集合，再进行语义编辑、revision 核对和正式内容构建。

**Tech Stack:** UTF-8 YAML、Python 3.11+、PyYAML、`xinghuo_content`、Ruff、pytest。

## Global Constraints

- 只处理 `literature.series: 毛泽东选集` 的卡片。
- `inspiration <= 220`、`explanation <= 420`、两者合计 `<= 600` 个 Unicode code point。
- 只为实际修改的已发布卡片递增一次 `revision`。
- 不修改 UUID、引文、出处、图片、schema、内容版本或正式卡片数量。
- 不虚构作者心理或历史细节；不把特定历史结论直接套用到日常生活。

---

### Task 1: 建立逐卡质量审计

**Files:**
- Read: `content/cards/000-development-placeholder.yaml`
- Read: `content/cards/002-*.yaml` through `content/cards/120-*.yaml`
- Read: `content/cards/601-mao-v1.yaml` through `content/cards/700-mao-v4.yaml`

**Interfaces:**
- Consumes: schema-4 YAML 中的 `literature.series` 与 `interpretation` 字段。
- Produces: 保留集合与重写集合；后续只允许修改重写集合。

- [x] **Step 1: 用 PyYAML 筛出 220 张毛选卡片并核对范围**

Run: an inline Python audit that loads every `content/cards/*.yaml`, filters exact series `毛泽东选集`, and reports count, file ranges, revisions, and interpretation lengths.

Expected: exactly 220 published cards covering `000`, `002-120`, and `601-700`.

- [x] **Step 2: 按 design.md 的五项标准逐条判定**

重点标记重复原句、主题模板、泛化边界提醒、宏大说教和错误日常类比；不以 revision 或编号单独决定是否重写。

- [x] **Step 3: 抽查保留集合**

抽查不同卷次、年代和主题，确认保留内容同时具备直接释义、具体语境和自然启示。

### Task 2: 重写薄弱解读与启示

**Files:**
- Modify: Task 1 重写集合对应的 `content/cards/*.yaml`

**Interfaces:**
- Consumes: 每张卡片的引文、篇名、日期、背景、故事与来源。
- Produces: 符合设计结构的 `interpretation.explanation` 和 `interpretation.inspiration`。

- [x] **Step 1: 按卷次和文件编号分批重写**

每条先完成直接释义，再加入能被现有字段或原文来源支持的语境；启示独立写成轻松、具体的一段话。

- [x] **Step 2: 为每张实际修改卡片递增 revision**

对比 Git 基线，确保解释文本发生变化时 revision 恰好 `+1`，没有变化时 revision 为 `+0`。

- [x] **Step 3: 分批复核事实与文风**

每批检查原句含义、篇名语境、战争/政治内容的日常转化边界，以及是否出现相邻卡片句式重复。

### Task 3: 结构化审计与正式验证

**Files:**
- Verify: all modified `content/cards/*.yaml`
- Verify: `.trellis/tasks/08-06-optimize-mao-interpretations/*.md`

**Interfaces:**
- Consumes: 修改后的完整内容快照与 Git 基线。
- Produces: revision 审计、内容校验和确定性构建结果。

- [x] **Step 1: 运行差异审计**

用 PyYAML 读取基线和工作树，验证只有目标系列的 `interpretation` 与 `revision` 改变，并核对 revision 增量。

- [x] **Step 2: 扫描模板残留与长度**

检查设计中列出的训诫句式和现有高频模板；逐条确认字段不为空且不超过 schema 限制。

- [x] **Step 3: 运行内容工具质量门**

```powershell
$env:PYTHONPATH='content-tool/src'
python -m ruff check content-tool
python -m pytest content-tool
python -m xinghuo_content validate content --formal
python -m xinghuo_content build content --output dist --formal --verify-deterministic
```

Expected: all commands exit 0; formal validation reports exactly 700 published cards; deterministic build verification passes.

- [x] **Step 4: 最终抽查并提交**

抽查每卷的修改样本、最长字段和高风险语境；确认工作树只包含任务文件与目标 YAML 后，提交到 `task/5`，不合并、不变基、不推送 `main`。
