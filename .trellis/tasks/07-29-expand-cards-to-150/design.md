# 扩充毛选卡片至150条：技术与编审设计

批准日期：2026-07-29

## 1. 目标与边界

保留现有 31 条正式卡片，在不改变内容 schema、Room 数据库或客户端交互的前提下新增 119 条毛泽东经典名句，使内容包精确包含 150 条正式卡片。新增内容固定为 89 条著作或讲话名句和 30 条诗词名句。

内容版本从 `1.3.0` 升级到 `1.4.0`，继续要求 `minimumAppVersionCode: 4`。根据发布要求，Android 应用同步升级到 `versionCode=5`、`versionName=1.4.0`；schema 3 和现有消费者兼容性不变。

## 2. 总体方案

现有数据流保持不变：

```text
机构传播度资料 + 原文/权威来源
  -> candidate-catalog.md（编审门槛）
  -> content/cards/*.yaml（119 个新文件）
  -> xinghuo_content validate/report/build
  -> content-v1.4.0.zip + manifest.json
  -> app/src/main/assets/bootstrap.zip
  -> Android 严格解析并导入 Room
```

不把网页内容自动抓取后直接发布。网页检索只建立候选和证据，最终 YAML 由逐条核验后的结构化内容组成。

## 3. 候选分级与选取

`research/candidate-catalog.md` 是实施期的编审清单，不进入内容包。每个候选必须记录：

- 顺序编号与拟用文件 slug；
- 连续原句、作品或讲话名称、写作日期、系列和卷次；
- 至少一个传播度证据 URL；
- 一个落到具体原文的 URL；
- 另一个不同主机的权威或交叉核验 URL；
- 类型（`prose` 或 `poetry`）、主题、拟用图片和审核状态；
- 版本异文、误传风险或上下文边界说明。

接受项必须满足以下顺序：先确认原句与作品，再确认传播度，随后检查 90 code point 限制、重复与作品占比，最后编写解释字段。无法定位全文的热门句宁可淘汰，不用相似表述替代。

最终目录精确接受 89 条 `prose` 和 30 条 `poetry`。普通作品最多 3 条；只有目录中明确标记 `majorWork: true` 的公认名篇可以达到 4 至 5 条，任何作品不得超过 5 条。

## 4. 卡片文件设计

新卡片沿用 `content/templates/card.yaml` 的 schema 3 字段：

- 文件编号使用 `032` 至 `150`，编号仅用于编辑排序，不参与同步身份；slug 使用稳定英文短名。
- 每张新卡片生成一次全新 UUID，写入后不可复用或改绑；`revision` 从 1 开始。
- `status: published` 与 `review.status: verified` 只在全部证据和文字复核完成后写入。
- `quote` 必须是 NFC、单段、连续原文且不超过 90 code point。
- `interpretation.inspiration` 面向当代行动建议，但不冒充作者原意；`interpretation.explanation` 解释原文语境、论证对象和适用边界。
- `historicalEvent` 不超过 100 code point；`background` 与 `story` 必须说明具体写作背景，避免为同篇多卡机械复制完全相同的文字。
- `sources` 至少两个不同主机，记录实际访问日期；传播度合集可作为其中一个权威来源，但另一来源必须支持原文与上下文。
- 仅复用现有 8 个原创图片 ID，按主题轮换并在报告中检查使用分布。

## 5. 重复与质量控制

在 Python 内容工具增加正式卡片原文的完全重复校验，重复时同时报告两个源文件。内容报告增加按 `workTitle` 的计数，支持检查普通作品 3 条、公认名篇 5 条的上限。

近重复不采用自动删除。实施期用去标点后的包含关系和相似度审计产生待复核列表，由编审逐对判断；自动相似度只能发现问题，不能替代语义判断。

流行度也不写入发布 schema。它属于编辑证据，保存在任务研究目录，避免客户端承担不可验证的评分字段。

## 6. 版本、构建与兼容性

`content/project.yaml` 更新为：

- `contentVersion: 1.4.0`
- `publishedAt: '2026-07-29T00:00:00Z'`
- `minimumAppVersionCode: 4`
- `expectedPublishedCards: 150`
- `releaseNotes: 新增119条经典著作、讲话与诗词名句`

Android 发布配置更新为：

- `versionCode: 5`
- `versionName: 1.4.0`
- `app-v1.4.0` Release 保持 non-latest，避免覆盖稳定内容清单地址。

构建命令同时输出忽略的 `dist/content-v1.4.0.zip`、`dist/manifest.json`，并覆盖受版本控制的 `app/src/main/assets/bootstrap.zip`。Android 仪器测试中“安装较新的内置内容”用例同步期望 `1.4.0`；其余解析和数据库合同不变。

README 只更新内置卡片数量和当前源码内容版本示例，不宣称尚未实际创建的 GitHub Release 已发布。

## 7. 失败与回滚

- 任一候选证据不足：从目录中替换该候选，不降低来源门槛。
- 单张 YAML 校验失败：只修复对应卡片，保持其 UUID；未发布前无需增加 revision。
- 正式数量不是 150：构建必须失败，不调整 `expectedPublishedCards` 迁就错误数量。
- 确定性构建失败或 Android 解析失败：不保留新的 `bootstrap.zip` 作为完成产物，修复源 YAML 后重新构建。
- 用户已于 2026-07-29 明确要求将 1.4 APK 提交到 GitHub，因此本任务获准提交、推送并依次发布 `content-v1.4.0` 与 `app-v1.4.0`；不提交无关界面改动。

## 8. 验证

必须完成：

```powershell
$env:PYTHONPATH='content-tool/src'
python -m ruff check content-tool
python -m pytest content-tool
python -m xinghuo_content validate content --formal
python -m xinghuo_content report content --output dist/content-report-1.4.0.json --formal
python -m xinghuo_content build content --output dist --bootstrap-output app/src/main/assets/bootstrap.zip --formal --verify-deterministic
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

如本机已有可用模拟器，再运行 `:app:connectedDebugAndroidTest`；没有模拟器时必须明确记录这一遗留验证项。
