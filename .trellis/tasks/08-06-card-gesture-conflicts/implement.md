# 卡片手势冲突实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use the project Trellis implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让横向翻面与纵向换卡互斥，并让背面滚动到边缘后的同一次低速拖动可靠进入相邻卡片。

**Architecture:** 卡片在 `Initial` pointer pass 完成方向锁定并只消费明确的横向手势；阅读页根据 `flippedCardId` 为分页器选择默认或背面专用的吸附阈值。背面继续使用 Compose 原生嵌套滚动，不引入手工滚动同步。

**Tech Stack:** Kotlin 2.0.21、Jetpack Compose Foundation 1.7.6、Compose UI instrumentation tests。

## Global Constraints

- 不改变翻面距离 `22%`、速度 `900 dp/s`、动画和视觉设计。
- 不增加可见按钮、教学文案、设置项或依赖。
- 正面分页保持 Compose 默认 `50%` 低速吸附阈值。
- 背面长文必须完整可滚动；只有到达纵向边缘后的剩余位移进入分页器。
- 每次分页最多移动一张卡片。

---

### Task 1: 建立手势回归

**Files:**
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/components/QuoteCardInstrumentedTest.kt`
- Modify: `app/src/androidTest/java/com/xuhuangbin/xinghuozhaidu/ui/components/InterpretationInstrumentedTest.kt`

**Interfaces:**
- Consumes: `FlippableQuoteCard`、`ReaderScreen` 和现有 `testCard` fixtures。
- Produces: 可复现斜向双重触发和背面低速边缘回弹的仪器测试。

- [x] **Step 1: 把既有混合方向测试改成方向锁定断言**

在背面先注入超过 `touchSlop` 的短横拖，再加入较大纵向位移；断言“解读”顶部位置不变且卡片仍停在背面。该测试定义一次手势锁定横向后不能转交纵向滚动。

- [x] **Step 2: 新增阅读页斜向横拖回归**

渲染两张卡片，从第一张执行横向位移明显占优、同时包含足以触发分页 fling 的纵向位移；断言第一张翻到“解读”面、`onPositionChanged` 仍为 `0`，第二张名言未显示。

- [x] **Step 3: 新增长背面连续边缘交接回归**

第一张使用可滚动的中长解读。翻到背面后执行一次持续约 `900 ms` 的长上拖，使内容先到末尾，再把不足页面 `50%`、但超过 `25%` 的剩余位移交给分页器；断言位置变为 `1` 且第二张名言显示。

- [x] **Step 4: 在 API 35 设备上运行目标仪器测试并确认新增断言先失败**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xuhuangbin.xinghuozhaidu.ui.components.QuoteCardInstrumentedTest,com.xuhuangbin.xinghuozhaidu.ui.components.InterpretationInstrumentedTest --no-daemon
```

预期：新斜向手势断言或新背面连续交接断言在现有实现上失败；既有无关测试继续通过。

### Task 2: 实现方向锁定与边缘交接

**Files:**
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/components/QuoteCard.kt`
- Modify: `app/src/main/java/com/xuhuangbin/xinghuozhaidu/ui/reader/ReaderScreen.kt`
- Modify: `.trellis/spec/frontend/component-guidelines.md`

**Interfaces:**
- Consumes: `PointerEventPass.Initial`、`PointerInputChange.consume()`、`PagerDefaults.flingBehavior` 和 `flippedCardId`。
- Produces: 卡片级横向方向锁与背面专用 `25%` 分页吸附阈值。

- [x] **Step 1: 在卡片中实现横向方向锁**

增加私有方向优势常量 `1.25f`。在累计位移越过 `touchSlop` 时，仅当 `absX >= absY * 1.25f` 才启动横向翻面；通过 `PointerEventPass.Initial` 先于分页器判定，并在横向锁定后消费当前及后续变化。否则结束卡片识别，让纵向链完整处理手势。

- [x] **Step 2: 为背面配置较短的分页吸附距离**

在 `ReaderPager` 中用 `PagerDefaults.flingBehavior` 构造 `VerticalPager` 的 `flingBehavior`：正面传 `0.5f`，`flippedCardId != null` 时传 `0.25f`。保留 `PagerSnapDistance.atMost(1)` 默认行为。

- [x] **Step 3: 同步前端交互规范**

把“横向拖动不得消费纵向移动”的旧约束更新为方向锁定规则，并把必需回归改为斜向互斥和长背面连续边缘分页。

- [x] **Step 4: 运行目标仪器测试并调整 fixture，不放宽产品断言**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.xuhuangbin.xinghuozhaidu.ui.components.QuoteCardInstrumentedTest,com.xuhuangbin.xinghuozhaidu.ui.components.InterpretationInstrumentedTest --no-daemon
```

预期：两个测试类全部通过。若连续交接 fixture 的滚动范围不稳定，只调整测试文本长度和拖动距离，使其明确跨过 `25%` 且低于 `50%`；不得删除一次连续拖动的断言。

### Task 3: 全量质量验证

**Files:**
- Verify only: all task changes.

**Interfaces:**
- Consumes: 项目前端 Quality Check。
- Produces: JVM 测试、Lint、Debug APK 和 API 28/API 35 仪器验证结果。

- [x] **Step 1: 运行 JVM、Lint 和 Debug 构建**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
```

- [x] **Step 2: 在 API 28 与 API 35 上运行仪器测试**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

分别在 `Xinghuo_API_28` 和 `Xinghuo_API_35` AVD 上执行，确保兼容最低 API 与当前 API。

- [x] **Step 3: 检查最终差异与工作区**

确认仅包含本任务代码、测试、Trellis 任务和必要规范更新；不包含 APK、Gradle 缓存或设备运行文件。

## Verification Results

- `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: passed.
- Target gesture instrumentation on `Xinghuo_API_35`: 10/10 passed.
- Target gesture instrumentation on `Xinghuo_API_28`: 10/10 passed.
- Full instrumentation on each AVD: 38/39 passed. The same unrelated baseline
  assertion fails on both APIs because
  `AppRepositoryInstrumentedTest.initializeInstallsNewerBundledContentWithoutLosingPersonalState`
  still expects bundled content `1.6.0`, while the current bundled package is
  `1.7.0`. This task does not modify content or repository tests.
