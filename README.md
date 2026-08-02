# 星火摘读

星火摘读是一款本地优先的 Android 名言卡片应用。它以纵向逐卡阅读为主，支持查看原文上下文、时代背景和相关故事，也支持本地个性化推荐、点赞、收藏、个人笔记、本地搜索与生成分享图。个人阅读状态、兴趣偏好和笔记只保存在手机中，内容更新与应用更新均由用户在“我的”页面手动触发。

## MVP 能力

- APK 当前内置 600 张正式卡片和 8 张原创背景图，其中包括 150 条毛泽东著作、讲话和诗词名句、300 条热门励志名人名言，以及 150 条马原思考卡，首次安装即可离线阅读。
- 新用户可从 12 个兴趣标签中选择最多 5 个，也可直接跳过；已有用户升级后不会被兴趣选择页打断。
- 现有阅读流会根据兴趣选择、点赞、收藏和关联笔记在本地调整未读内容顺序，同时保留约 20% 的探索内容和单轮不重复体验。
- 阅读页可对当前卡片选择“减少此类”；“我的”中的“兴趣偏好”支持随时修改标签和清除这类反馈。
- 同一轮次持久化保存推荐顺序，已看内容与回看位置保持不变，未读优先、不重复，支持连续回看。
- 卡片停留满 3 秒后记为已读；翻面可以滚动查看出处、背景、故事和来源。
- 点赞与收藏保持独立，在同一个“收藏与点赞”页面中切换查看。
- 支持独立笔记和卡片关联笔记，同一卡片可写多篇；标题可选、正文必填，并支持编辑和确认删除。
- 点赞、收藏、笔记、阅读进度、兴趣偏好、推荐反馈和内容快照均保存在本地 Room 数据库中，不会用于云端画像。
- 使用“阅读 / 收藏 / 笔记 / 我的”四栏底部导航；卡片操作区可直接写关联笔记。
- 可生成固定 1080×1440 分享图，并调用 Android 系统分享面板。
- 通过 GitHub Releases 分发完整内容快照，不运行常驻服务，也不上传个人数据。

## 项目结构

```text
app/                  Android 客户端
content/cards/        一张卡片一个 UTF-8 YAML 文件
content/images/       背景图片及独立许可元数据
content/templates/    卡片和图片模板
content-tool/         校验、报告和确定性构建工具
.github/workflows/    常规检查与内容发布工作流
```

内容同步不依赖连续数字编号。每张卡片使用稳定 UUID 标识，`revision` 表示同一卡片的修订；整个内容集合使用语义化 `contentVersion` 发布为完整快照。客户端据此可靠处理新增、修改、下架和恢复发布，同时保留本机点赞、收藏与关联笔记；下架卡片仍被任一笔记引用时会保留最后可信快照。

## 本地环境

- JDK 17
- Android SDK Platform 35；最低运行版本为 Android 9（API 28）
- Python 3.11 或更高版本；CI 使用 Python 3.12

Windows PowerShell 示例：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
python -m pip install -e ".\content-tool[dev]"
.\gradlew.bat :app:assembleDebug
```

调试 APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`。

个人本地安装使用独立的 `personal` 构建类型。它沿用 Release 的 R8 压缩和资源裁剪，同时用本机 Android 标准调试证书签名，因此 APK 可以直接安装：

```powershell
.\gradlew.bat :app:assemblePersonal
```

产物位于 `app/build/outputs/apk/personal/app-personal.apk`。后续覆盖安装必须继续使用同一份 `%USERPROFILE%\.android\debug.keystore`；该证书仅适合个人本地使用。

正式 `release` 构建使用长期保管的独立证书，只从以下环境变量读取签名配置：

```text
ANDROID_RELEASE_KEYSTORE_PATH
ANDROID_RELEASE_STORE_PASSWORD
ANDROID_RELEASE_KEY_ALIAS
ANDROID_RELEASE_KEY_PASSWORD
```

四项配置缺一时，Gradle 会在正式打包任务执行前失败；debug、personal 和 lint 不依赖正式密钥。keystore 与密码不得写入仓库、Gradle 属性或构建日志。当前 App 版本为 `1.8.0`（version code 10），APK 内置内容版本为 `1.5.0`，源码内容版本为 `1.5.0`；`dist/` 仍是忽略的本地构建目录，不提交 APK 到源码仓库。

客户端当前配置的内容地址为：

```text
https://github.com/Hi-prof/maoxuan/releases/latest/download/manifest.json
```

最近已发布的内容 Release 为 [`content-v1.4.1`](https://github.com/Hi-prof/maoxuan/releases/tag/content-v1.4.1)。源码当前准备 `content-v1.5.0`；发布对应标签前，稳定清单地址仍指向线上最新 Release。GitHub 暂时不可访问时仍不影响内置内容和本地阅读。

## 编辑内容

1. 参考 `content/templates/card.yaml` 新建卡片 YAML，使用不可复用的 UUID，并在修改既有卡片时递增 `revision`。
2. 为正式卡片记录准确系列、作者或卷次、篇名、日期和至少两个独立来源；引文须可追溯、可独立阅读且不超过 90 个 Unicode code point。现代作品日期使用 `YYYY`、`YYYY-MM` 或 `YYYY-MM-DD`，古代人物可使用 `前N` 或 `前N世纪`。
3. 新图片放入 `content/images/`，同时创建同名 YAML，记录来源、作者、许可和 `shareAllowed: true`。
4. 修改 `content/project.yaml` 中的 `contentVersion`、发布时间、发布说明和 `expectedPublishedCards`；正式校验要求声明数量与实际发布卡片数完全一致。

正式内容校验和报告：

```powershell
python -m ruff check content-tool
python -m pytest content-tool
python -m xinghuo_content validate content --formal
python -m xinghuo_content report content --output content-report.json --formal
python -m xinghuo_content build content --output dist --formal --verify-deterministic
```

如需同步更新 APK 内置内容，可在已经完成人工复核后执行：

```powershell
python -m xinghuo_content build content `
  --output dist `
  --bootstrap-output app/src/main/assets/bootstrap.zip `
  --formal `
  --verify-deterministic
```

## 发布与回滚

普通 push 和 pull request 只运行 Python、正式内容、Android 单元测试、lint 与构建检查，不会发布内容或 APK。

内容发布由与源版本完全一致的标签触发，例如 `content/project.yaml` 中为 `1.5.0` 时，标签必须是 `content-v1.5.0`。工作流先完成正式校验和确定性构建，再创建草稿 Release、上传 `content-v1.5.0.zip` 与 `manifest.json`，最后才公开 Release。创建标签和推送属于人工发布操作。

正式 APK 由与 Android `versionName` 完全一致的 `app-vX.Y.Z` 标签触发。工作流从 GitHub Secrets 临时恢复 keystore，运行单元测试、release lint、R8 构建、`apksigner` 和包版本校验，再上传 `xinghuo-zhaidu-vX.Y.Z.apk` 及 SHA-256。APK Release 明确设为非 latest，确保客户端的 `releases/latest/download/manifest.json` 始终继续指向内容 Release。

应用内更新通过 GitHub Releases 列表筛选最高的正式 `app-vX.Y.Z` 版本，不使用仓库 latest；用户确认后 APK 会流式下载到应用缓存，并在大小和发布附带的 SHA-256 均匹配后交给 Android 系统安装器。Android 8 及以上首次使用需要在系统设置中允许星火摘读“安装未知应用”。

已经发布的错误内容不覆盖历史 Release，也不降低版本号。回滚时恢复上一份可信内容源，提升 patch 版本并发布新的完整快照；客户端仍按正常升级路径处理。

## 内容与版权边界

本仓库只保存卡片阅读所需的适量短引文、出处、来源记录和原创说明，不镜像或重新发布完整著作。每条正式内容仍需人工核对连续原文、篇名卷次、双源独立性及背景叙述；自动校验不能代替事实审核。

背景图是本项目确定性生成的原创素材，图片元数据登记为 CC0 1.0。卡片正文使用随 APK 附带的 Noto Serif SC 字体，字体来源记录和 SIL Open Font License 1.1 全文位于 `app/src/main/assets/licenses/`。

本项目不包含账号、统计 SDK、广告、远端用户数据库、签名密钥或手机端个人数据。
