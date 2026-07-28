# 星火摘读 MVP 技术设计

## Status

- 已按用户批准的 A 方案完成实现，并在 Android API 28 与 API 35 上通过本地质量门禁。
- 离线卡片解读、收藏/点赞同页与个人笔记增量均已实现并进入最终复验。
- 本文记录当前 MVP 的已实现边界；公开仓库、签名和内容 Release 仍待单独授权。
- 本文是 MVP 的实现边界；若实现中发现与 `prd.md` 冲突，以 `prd.md` 的用户需求为准，并先返回规划阶段修订。

## Architecture Summary

系统由三个边界清晰的部分组成：

1. Android 客户端：离线阅读、本地状态、手动内容同步和分享图生成。
2. 内容工具：把人工维护的 YAML 和图片校验、规范化并构建为客户端专用内容包。
3. GitHub Releases：公开托管稳定版本清单和不可变的版本化内容包，不运行 API 服务或数据库。

数据只从 GitHub 单向进入 Android。点赞、收藏、阅读轮次和当前位置从不上传。

## Chosen Stack

| Layer | Choice | Reason |
| --- | --- | --- |
| Android | Kotlin, single-activity | 只面向 Android，直接使用平台能力 |
| UI | Jetpack Compose, Material 3 primitives | 纵向分页、翻面、状态恢复和固定布局更直接 |
| Navigation | Navigation Compose | 三个一级页面、搜索和详情路由 |
| Local data | Room | 内容、轮次和用户状态需要事务一致性 |
| Async state | Kotlin Coroutines and Flow | 数据库观察、3 秒计时和前台下载 |
| Network | OkHttp | 手动下载、超时、流式写盘和测试替身成熟 |
| JSON | kotlinx.serialization | 严格解析客户端发布格式 |
| Images | Coil Compose | 本地文件、缩略图和缓存加载 |
| Content tools | Python 3.12 compatible CLI | YAML、图片和确定性发布产物易于校验 |
| CI | GitHub Actions | 标签触发校验、构建和 Release |

Android 最低版本为 API 28，编译和目标版本使用实施时可用的最新稳定 SDK。项目使用 Gradle Wrapper、Kotlin DSL 和 version catalog，不依赖全局 Gradle。

## Repository Layout

```text
app/                         Android application module
content/
  cards/                     one UTF-8 YAML file per card
  images/                    source images and per-image metadata
  templates/                 card and image templates
  bootstrap.yaml             initial content version metadata
content-tool/                Python validation and build package
dist/                        ignored local build output
.github/workflows/           Android checks and content release
.trellis/                    task and project guidance
```

Android 源码、内容源、来源记录和 Release 使用同一个公开仓库。`dist/`、签名材料、本地数据库、分享缓存和任何个人阅读状态均不得提交。

建议应用 ID 使用 `com.xuhuangbin.xinghuozhaidu`。在首次对外签名 APK 前固定该 ID，之后不再修改。

## Android Layers

保持单模块 MVP，按职责分包，不提前拆成多个 Gradle 模块：

| Package | Responsibility |
| --- | --- |
| `data.local` | Room entities, DAO, database, migrations |
| `data.content` | bundled import, package parsing, asset store, sync transaction |
| `data.network` | manifest and package download |
| `domain.model` | UI-facing card, note, source, round and update models |
| `domain.repository` | cards, user state, notes, rounds, search and update operations |
| `ui.reader` | vertical pager, flip state, actions and completion page |
| `ui.saved` | favorites and liked summary lists |
| `ui.notes` | note list and full-screen note editor |
| `ui.search` | local quote/title search |
| `ui.update` | version status, update summary and progress |
| `ui.share` | fixed 1080 x 1440 renderer and share sheet |
| `ui.theme` | visual tokens, typography and adaptive icon assets |

ViewModel 暴露不可变 UI state 和明确事件。Composable 不直接访问 DAO、网络或文件系统。时间、随机数和下载器通过小接口注入，以便确定性测试；不引入大型依赖注入框架，使用应用级容器即可。

## Navigation And Screens

底部导航固定为“阅读 / 收藏 / 笔记 / 我的”。切换一级页面时保留各自 back stack 和滚动位置。

| Route | Main content |
| --- | --- |
| Reader | 一屏一张纵向卡片、搜索入口、卡片外操作区 |
| Saved | “收藏 / 点赞”分段切换及各自最近优先的摘要列表 |
| Notes | 最近修改优先的笔记列表、独立笔记新增入口 |
| Note editor | 新建或编辑笔记；卡片入口自动携带不可变的关联 ID |
| Mine | 版本和更新区 |
| Search | 名言正文和篇名的本地关键词搜索 |
| Card detail | 从列表或搜索进入的单卡正反面详情 |
| Update summary | 版本、日期、大小、新增/修改/下架数量和确认操作 |

列表详情返回时恢复列表位置。详情阅读不改变主阅读页当前卡片或轮次位置。“收藏 / 点赞”两个分段分别保留列表位置。下架卡片可从仍保有相应状态的收藏、点赞或关联笔记进入，搜索不返回下架内容。

### Personal Notes

- 笔记使用独立 Room `notes` 表，而不是在 `cards` 或 `user_card_state` 上增加序列化文本字段；这是支持独立笔记、同一卡片多篇笔记和稳定排序的最小规范化模型。
- `NoteEntity` 保存自增 `id`、可空 `cardId`、可选标题、非空正文、`createdAt` 和 `updatedAt`。`cardId` 不使用级联删除外键，卡片快照生命周期由 Repository 事务显式维护。
- `PersonalNote` 是 UI 使用的不可变领域模型。Repository 暴露按 `updatedAt DESC, id DESC` 排序的 `Flow<List<PersonalNote>>`，并提供创建、更新和删除事件；Composable 不访问 DAO。
- 从卡片操作区进入编辑器时以路由参数传入 `cardId`，保存后创建一条新的关联笔记；从笔记页浮动新增按钮进入时 `cardId = null`。现有笔记的关联关系在编辑时不可变。
- 编辑器包含可选标题和必填正文。保存前对两者去除首尾空白，正文为空时阻止保存；保存成功后返回笔记列表。有本地修改时拦截返回并显示“继续编辑 / 放弃修改”确认。
- 笔记列表按最后修改时间倒序，展示标题或正文首行、正文摘要和修改时间；关联笔记额外展示名言摘要、篇名及下架标记，点击关联摘要进入卡片详情，点击笔记主体进入编辑器。
- 删除通过编辑页垃圾桶图标触发二次确认。删除关联笔记与可能的下架卡片/图片清理在同一数据库事务中判断，避免留下无引用快照或提前删除仍被其他笔记引用的卡片。
- 卡片下架时，只要仍被点赞、收藏或至少一篇笔记引用，就保留最后可信快照。删除最后一篇关联笔记后，仅当该卡片也未点赞、未收藏时才删除下架快照并清理未引用图片。
- 笔记是设备本地个人数据，不进入静态内容包、分享图片、搜索历史或任何网络请求。

### Search History

- `search_history` 是 Room 持久状态，字段包含自增 ID、使用 `NOCASE` 规则唯一的关键词和最近提交时间；数据库通过显式 `1 -> 2` migration 创建该表。
- 用户只有通过输入法的 Search action 提交非空关键词时才写入历史。写入前去除首尾空白；重复关键词替换原记录并移动到最前。
- DAO 以最近提交时间和自增 ID 倒序观察历史，保存后在同一事务中裁剪到最近 10 条。
- `AppRepository` 暴露历史 `Flow` 及保存、单条删除、全部清空操作；`MainViewModel` 将其作为不可变 UI state 暴露并接收明确事件。
- 搜索框为空时，`SearchScreen` 展示历史列表。点击关键词只回填搜索框并显示现有本地匹配结果，不再次保存；单条删除和全部清空立即写入 Room。
- 空白提交不保存；删除不存在的记录保持幂等；数据库迁移不得影响卡片、点赞、收藏或阅读轮次数据。

## Reader Interaction

- 使用纵向 `VerticalPager`，完整落页后才更新当前位置。
- 向上滑前进，向下滑可回看当前轮次的任意历史卡片。
- 正面点击“读背景”或向任一水平方向拖动执行 Y 轴翻面；背面保留明确返回操作，并支持横滑翻回正面。
- 横滑由共享卡片组件按水平方向锁定处理，拖动进度直接驱动 Y 轴角度；短拖回弹，越过距离阈值或达到甩动速度阈值后使用带阻尼的 spring 收束到下一面。
- 翻面在侧向角度附近轻微收窄并降低视觉重量，配合稳定透视距离形成实体纸卡的空间感；正背内容只在越过 90 度后切换并校正镜像。
- 背面显示出处、可选原文上下文、可选时代背景、可选故事和默认收起的参考来源。
- 背面处于打开状态时禁用外层 pager 手势，所有纵向拖动优先用于背面滚动。
- 水平拖动只消费横向手势，背面 `verticalScroll` 与外层 `VerticalPager` 的既有纵向优先级保持不变；点击翻面与横滑翻面共用同一个 `flipped` 状态契约。
- 点赞、收藏、笔记、分享、“解读”和“读背景”均位于卡片外，不遮挡正文；“解读”固定在“读背景”左侧。四个图标动作保持 `48.dp` 触控目标，并在 `360.dp` 最小宽度验证不挤压右侧文字动作。
- 切换到新卡片时默认显示正面；每张卡片的临时翻面和滚动位置不跨进程持久化。

### Offline Card Interpretation

- `CardActions` 增加明确的 `onInterpret` 回调；主阅读页和完整卡片详情页继续复用同一操作栏，避免两处交互漂移。
- “解读”在卡片正面和背面均可用。打开解读不修改 `flipped`，关闭后恢复原卡片、原正反面和原阅读位置。
- 新建共享 `InterpretationSheet`，使用 Material 3 modal bottom sheet 展示篇名，以及“核心意思、理解重点、现实启示”三个无嵌套卡片的内容区块。
- 弹层正文独立滚动；弹层存在时由模态层接管点击和纵向拖动，外层 `VerticalPager` 不接收换卡手势。
- 关闭按钮提供中文 `contentDescription`；遮罩、下滑和系统返回沿用 Material 3 标准 dismiss 行为。
- 操作栏保留三个 `48.dp` 图标触控目标，并为右侧两个文字操作使用紧凑、稳定的内容间距；必须在 `360.dp` 宽度和目标字体设置下验证不溢出。

解读数据沿用现有内容单向流：

```text
card YAML -> Python validator -> cards.json -> CardDto -> CardEntity
          -> QuoteCard(CardInterpretation) -> InterpretationSheet
```

`CardInterpretation` 是包含 `coreMeaning`、`keyPoint`、`contemporaryRelevance` 三个非空字符串的不可变领域值。Composable 只消费领域值，不解析 YAML、JSON 或数据库列。

### Three-second Read Rule

计时任务仅在以下条件同时成立时运行：当前 page 已 settled、App 生命周期至少为 `STARTED`、阅读 tab 可见、当前展示的是普通卡片而非完成页。任务以卡片 ID 为 key，翻面不会重启计时，切页、离开 tab、打开详情或进入后台会取消。连续满 3 秒后在 Room 事务中写入 `readAt`，重复写入保持幂等。

## Reading Round Model

每轮创建后立即持久化随机顺序，不依赖随机种子重算，因此应用重启和库版本变化都不会改变既有顺序。

1. 没有活动轮次时，将所有可用卡片洗牌并写入 `reading_round_items`。
2. 前进只消费尚未到达的既有顺序，不重新随机已读卡片。
3. 主动回看只移动当前位置，不清除 `readAt`。
4. 同步新增 ID 时，仅把新卡片随机插入当前位置之后的未读区，不改变历史区顺序。
5. 同步修改同一 ID 时更新内容，不新增轮次项。
6. 同步下架 ID 时从可继续浏览的集合隐藏；若当前项被下架，恢复到最近的有效位置。
7. 全部有效项已读后，在最后一张之后提供虚拟完成页；不会自动把用户从最后一张拉走。
8. 完成页点击“开始新一轮”后才创建新 round，并保留所有用户状态。
9. 已完成但尚未重开的轮次若同步到新卡片，则恢复为进行中，并把新卡片加入未读区。

完成页统计当前轮次有效项的已读数量，以及这些卡片当前处于点赞和收藏状态的数量。

## Room Data Model

| Table | Key fields | Notes |
| --- | --- | --- |
| `cards` | `id`, `revision`, quote and source metadata, optional sections, three interpretation columns, `imageId`, `availability` | 保留活动内容和需要展示的下架快照 |
| `card_sources` | `cardId`, `position`, name, URL, accessed date, evidence type | 至少两条，顺序稳定 |
| `image_assets` | `id`, content hash, local path, credit, source, license | 文件按 SHA-256 内容寻址 |
| `user_card_state` | `cardId`, liked/favorited flags and timestamps | 不随内容更新覆盖，不级联删除 |
| `reading_rounds` | `id`, state, current position, created/completed times | 同一时刻只有一个活动轮次 |
| `reading_round_items` | `roundId`, `position`, `cardId`, `readAt` | 唯一约束防止同轮重复 ID |
| `withdrawals` | `cardId`, revision, withdrawn time and reason | 防止 ID 被误复用，支持恢复发布 |
| `content_state` | installed version, published time, last checked/updated time | 单行应用内容状态 |
| `search_history` | auto ID, case-insensitive unique keyword, submitted time | 最近提交优先，最多保留 10 条 |
| `notes` | auto ID, nullable card ID, optional title, non-empty body, created/updated times | 本地个人数据，最近修改优先，同一卡片可多篇 |

`cards`、用户状态与笔记不使用会级联删除个人记录的外键。下架内容不再被点赞、收藏或任何笔记引用后，可以清除正文、来源和未引用图片，但保留 `withdrawals` 及必要的轮次 ID 记录。

当前数据库版本 2 已用于搜索历史。解读增量使用显式 `2 -> 3` migration，为 `cards` 增加 `interpretationCoreMeaning`、`interpretationKeyPoint`、`interpretationContemporaryRelevance` 三个 `TEXT NOT NULL DEFAULT ''` 列，并保留既有 `1 -> 2` migration，确保升级链为 `1 -> 2 -> 3`。空默认值只用于 migration 过渡，不能作为有效领域数据。

笔记增量使用显式 `3 -> 4` migration，只创建 `notes` 表和 `cardId`、`updatedAt` 索引，不改写现有卡片、用户状态、轮次或搜索历史。迁移后数据库链为 `1 -> 2 -> 3 -> 4`，不得使用 destructive migration。

内置内容版本提升到 `1.1.0`，所有活动卡片提升 `revision` 并写入完整解读。应用初始化时读取内置包元数据：数据库无内容时正常导入；已安装版本低于内置版本时从本地 asset 原子导入新版包。该导入不联网，并继续保留用户状态、阅读轮次与当前位置。若设备已安装更高内容版本，则不得用内置包降级覆盖。

简单搜索使用 Room 的参数化 `instr`/`LIKE` 查询匹配活动卡片的正文和篇名。MVP 数据量不引入 FTS；查询接口保留日后替换实现的边界。

## Authoring Schema

每张卡片一个 UTF-8 YAML 文件，字段分为四组：

| Group | Required content |
| --- | --- |
| Identity | UUID `id`, monotonically increasing `revision`, `status` |
| Quote | quote, series, volume, work title, authored/spoken date, themes |
| Interpretation | required `coreMeaning`, `keyPoint`, and `contemporaryRelevance` |
| Optional reading | context excerpt, historical background, related story |
| Evidence | at least two sources, verification record, image ID |

正文去除首尾空白并规范化为 Unicode NFC 后不得超过 90 个 code point，正文内部标点计入，不允许用换行绕过限制。脚本能检查长度和字段结构；是否连续引用、是否改写、来源是否真正独立仍由人工复核负责。

三个解读子字段分别去除首尾空白并规范化为 Unicode NFC，任一为空即拒绝。三段合计以 200～300 个汉字为写作目标，规范化后不得超过 600 个 Unicode code point。内容复核须确认：核心意思立足原句语境，理解重点避免断章取义，现实启示不机械套用历史命题；不得把新的事实主张伪装成原文信息。

这是发布契约的破坏性扩展：包内 `package.json`、`cards.json`、`images.json`、`withdrawals.json` 的 schema version 统一提升为 2，远端 `manifest.json` 自身仍保持 schema version 1。App 提升为 `versionCode = 2`、`versionName = "1.1.0"`，manifest 使用 `minimumAppVersionCode = 2`；旧客户端在下载前收到升级 App 的明确状态，而不是尝试解析新卡片字段。

可复用图片具有独立元数据，至少包含稳定 ID、文件、来源 URL、作者或机构、许可或公版依据、核验日期和 `shareAllowed: true`。正式包不接受来源不明、仅标注“网络图片”或不允许分享图再分发的资源。

卡片状态为 `draft`、`published` 或 `withdrawn`。曾发布的 YAML 不得直接删除；下架时保留同一 ID 并提升 revision，恢复发布仍使用原 ID 和更高 revision。

## Build Validation

内容 CLI 执行以下自动检查：

- YAML schema、未知字段、日期、URL、枚举和 UTF-8 编码。
- UUID 全局唯一、revision 合法、已发布 ID 不可复用。
- 正文长度不超过 90、必填出处齐全、选填空字符串被规范为缺失。
- 至少两个来源、至少一个标记为原文或权威出版依据、访问日期存在。
- 图片文件存在、格式可解码、尺寸和文件大小在限制内、哈希匹配、许可字段和 `shareAllowed` 有效。
- 引用的图片和卡片存在，孤立资源给出错误或明确告警。
- 首批包恰好包含 30 张活动卡片，并通过主题分布报告。
- 输出 JSON 可被 Android 共享的 JSON schema/golden fixture 重新解析。
- 相同输入生成字节一致的 JSON 和 ZIP，保证哈希可复现。

人工发布清单负责核对连续原文、篇名卷次、双源独立性、背景叙述、图片权利边界和主题均衡。自动化不得宣称替代人工事实核验。

## Distribution Contract

MVP 使用完整快照包，不实现增量补丁。几十到数百张卡片时，完整包更容易校验、回滚和跳版本同步。

固定检查地址使用 GitHub 的 latest release asset 形式：

```text
https://github.com/<owner>/<repo>/releases/latest/download/manifest.json
```

本仓库的 Release 在 MVP 阶段只用于内容版本，避免其他 Release 抢占 `latest`。未来若发布 APK Release，必须改为独立内容通道或确保稳定清单地址不受影响。

`manifest.json` 至少包含：

```json
{
  "schemaVersion": 1,
  "contentVersion": "1.1.0",
  "publishedAt": "2026-07-28T12:00:00Z",
  "minimumAppVersionCode": 1,
  "packageUrl": "https://github.com/.../content-v1.1.0.zip",
  "packageBytes": 12345678,
  "packageSha256": "...",
  "changes": { "added": 3, "updated": 2, "withdrawn": 1 },
  "releaseNotes": "..."
}
```

版本化 ZIP 至少包含 `package.json`、`cards.json`、`withdrawals.json`、`images.json` 和 `assets/`。客户端只解析发布 JSON，不解析 YAML。所有 JSON 使用 UTF-8、固定 schema version 和拒绝未知关键版本的严格解析策略。

首批 `1.0.0` 使用同一构建器生成，并作为 APK asset 内置。这样首次导入和网络更新走同一解析、校验与事务路径，避免维护两套格式。

## GitHub Release Flow

1. 本地执行内容格式化、校验、测试和预览报告。
2. 提交经过人工核验的 YAML、图片和许可记录。
3. 创建并推送 `content-vX.Y.Z` 标签。
4. GitHub Actions 校验标签与内容版本一致，并重新运行所有校验。
5. 工作流生成确定性 ZIP、SHA-256、变更统计和 `manifest.json`。
6. 工作流创建 draft Release，并把版本包与 `manifest.json` 全部上传为 assets。
7. 所有上传完成后才把 Release 发布为非草稿、非 prerelease；此前任一步失败时，`latest` 地址不会切换到新版本。

普通分支 push 只运行检查，不发布。内容回滚不把版本号倒退，而是发布更高 patch 版本，其内容快照恢复到上一份可信状态。

## Client Update Flow

联网只可能由“我的”页面中的“检查更新”操作触发：

1. 请求并严格解析 `manifest.json`，设置合理的连接、读取和总时限。
2. 比较语义版本和 `minimumAppVersionCode`；无更新时只更新“上次检查时间”。
3. 有更新时展示版本、日期、包大小、变更数量和发布说明，不自动下载。
4. 用户确认后流式下载到应用内部 staging 文件，同时展示进度。
5. 在解压前核对字节数和 SHA-256；限制 ZIP 总大小、文件数、单文件大小和路径。
6. 拒绝路径穿越、绝对路径、符号链接、未知图片格式和超大解码尺寸。
7. 严格解析并交叉校验所有 JSON、资源引用、卡片 ID/revision 和 tombstone。
8. 图片按内容哈希写入内部 asset store；同哈希文件直接复用。
9. 在一个 Room 事务内应用新增、修改、下架、来源、轮次和 `content_state` 变更。
10. 事务成功后删除 staging，并清理数据库不再引用的资源；失败或取消时保留旧内容版本。

文件系统无法与 Room 形成单一事务，因此先把已校验的内容寻址图片落盘，再提交数据库引用。若数据库失败，只会留下未引用文件，启动清理可安全删除；数据库永远不会指向尚未落盘的图片。

## Update And Withdrawal Semantics

- 稳定 ID 相同且 revision 增加表示修订，覆盖正文元数据但保留点赞、收藏和轮次状态。
- revision 未增加但内容改变属于非法包，客户端和发布脚本都拒绝。
- 下架必须有显式 tombstone，不能仅通过从快照中消失来表达。
- 下架卡立即退出主阅读流和搜索。
- 已点赞或已收藏的下架卡保留最后可信快照，并在相应列表和详情中标记“已下架”。
- 用户取消最后一个点赞或收藏后，详情关闭且快照可清理；个人状态和资源清理保持事务一致。
- 同一 ID 以更高 revision 恢复发布时，最新内容替换快照，原点赞和收藏自动恢复到普通状态。

## Visual System

视觉以已批准的 `reference-card-system-v6` 为唯一基线：

- 页面背景使用低彩度浅灰，纸张只带轻微暖色，避免整个界面成为单一米黄色。
- 主文字使用近黑灰，强调色使用克制的深红，辅助信息使用中性灰。
- 卡片圆角不超过 8 dp，不使用渐变、光斑、厚重边框、拟古纹样或嵌套卡片。
- 语录使用有明确开放许可的中文宋体/衬线字体并随 APK 附带字体许可；UI 使用稳定的系统无衬线字体。
- 正文字号按长度使用固定档位，建议 `1-32` 字为 34 sp、`33-60` 字为 31 sp、`61-90` 字为 28 sp，letter spacing 固定为 0。
- 图片在内容工具中完成灰度/低饱和预处理，卡片内视觉强度约 16% 到 17%，仅作底部背景水印。
- 操作图标使用 Material Symbols 或现有 Android 图标库，并提供 content description；不手绘常见功能图标。
- 固定浅色主题同时显式设置状态栏和导航栏图标颜色，防止系统深色模式自动反色。

卡片使用稳定宽高约束和内容安全区。实现后必须用目标设备截图校准字号档位，若 90 字在最小目标屏幕仍溢出，应调整卡片安全区或内容上限，不能无下限缩小字体。

## Share Rendering

- 使用独立的固定像素渲染器生成 1080 x 1440 的 3:4 图片，不截图当前 Activity。
- 渲染器复用卡片颜色、字体档位、背景图处理和出处格式，但不绘制按钮、系统栏、故事或来源 URL。
- 输出包含小号“星火摘读”品牌文字，不添加遮挡正文的大水印。
- 文件写入 `cacheDir/shares/`，通过 `FileProvider` 和 Android Sharesheet 发送，不请求媒体或外部存储权限。
- 旧分享缓存按时间和数量清理，分享取消不改变任何卡片状态。
- 测试必须验证像素尺寸、非空白输出、最长正文不截断以及背景图片确实被绘制。

## Network, Privacy And Security

- Manifest 和内容包仅允许 HTTPS，内容下载 host 默认限制为 GitHub 及其官方 release asset 重定向域名。
- App 只声明网络权限和分享所需的内部 `FileProvider`，不申请账号、联系人、位置、通知或外部存储权限。
- SHA-256 用于完整性校验，HTTPS 和 GitHub 仓库权限提供发布来源保证。MVP 不增加独立内容签名；若未来允许第三方源，再引入内置公钥签名。
- 外部来源链接只允许 `http`/`https`，由系统浏览器处理；App 内不嵌入 WebView。
- 不集成广告、统计 SDK、崩溃上报或用户行为采集。
- 日志不得记录完整内容包、来源访问令牌或本地用户状态。

## Compatibility And Local Prerequisites

当前机器检查结果：

- Android SDK 已存在，安装了 API 34、35、36 及 Build Tools 34、35。
- ADB 可用，但当前没有连接设备。
- Android Emulator 和 AVD 尚未安装。
- 默认 Java 为 8，不能运行现代 Android Gradle Plugin。
- Python 3.14 可用，内容工具需同时在 CI 的 Python 3.12 上验证兼容性。

实施前安装 JDK 17 并设置 Gradle 使用它；使用 Gradle Wrapper，不要求全局 Gradle。视觉与交互验收前还必须安装 Android Emulator 和至少一个 API 28、一个最新 API 的系统镜像，或连接相应实体设备。

## Test Strategy

### Content Tool

- YAML schema、ID/revision、双源、长度、日期和图片许可单元测试。
- 正常、缺字段、重复 ID、下架恢复和非法图片 fixtures。
- 确定性 JSON/ZIP golden tests 和 SHA-256 重复构建测试。
- 首批 30 张的主题分布、来源完整性和图片可分享报告。

### Android Unit And Integration

- 随机轮次无重复、回看、重启恢复、新增插入和完成后新增内容。
- 使用 fake clock 验证 3 秒连续计时、切页取消、后台取消和翻面不重置。
- Room 更新事务验证新增、修订、下架、恢复及用户状态不丢失。
- Manifest/ZIP 正常路径、超时、截断、错哈希、降级版本、未知 schema 和 zip-slip。
- 搜索、收藏/点赞分段、笔记增删改与排序、下架可见性和分享缓存清理。
- Room `3 -> 4` migration 保留全部既有数据；卡片下架和删除最后一篇关联笔记时验证快照保留/清理事务。

### UI And Visual

- Compose UI 测试覆盖双向滑动、翻面、背面滚动锁、点赞、收藏、笔记、搜索、四栏导航状态和更新流程。
- API 28 与最新 API 至少各运行一轮核心 instrumentation 测试。
- 目标视口至少覆盖 360 x 640 dp、360 x 800 dp、412 x 915 dp，并检查系统浅色/深色设置下固定浅色主题。
- 截图检查短、中、90 字正文、最长篇名、可选栏目缺失、超长背面和下架状态。
- 分享图片执行 1080 x 1440 像素、非空画布、背景存在和文本不越界的像素级断言。

## Rollback And Recovery

- APK 内置 `1.0.0` 永远是全新安装的可用回退起点。
- 下载、校验或导入失败时不更新 `content_state`，旧数据库和资源继续使用。
- 启动时清理过期 staging 和未被数据库引用的内容寻址文件。
- 已发布错误内容通过更高版本的修订或下架纠正，不向客户端发布版本降级。
- Room schema 变更必须提供 migration test；不得用 destructive migration 清除个人状态。

## Deliberate MVP Trade-offs

- 使用完整快照包而非增量补丁，优先保证一致性和可跨版本更新。
- 使用简单 SQL 子串搜索而非全文索引，适配首批和早期内容规模。
- 使用前台手动下载而非 WorkManager，严格遵守“不自动联网”。
- 不实现账号、备份恢复、APK 自动更新、第三方内容源和独立加密签名。
- 保持单 Android 模块，只有出现明确构建或所有权边界后再拆模块。

## Implementation-time Inputs

- 创建公开 GitHub 远端后，把实际 owner/repository 写入非敏感 BuildConfig 和发布工作流。
- 首次构建前确认最终应用 ID；默认采用 `com.xuhuangbin.xinghuozhaidu`。
- 选择并记录中文字体的开放许可，收集首批图片时逐项保存许可证据。
- 安装 JDK 17 和 Android 模拟器，或连接 API 28 及最新 API 的测试设备。
