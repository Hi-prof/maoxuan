# App 在线更新

## Goal

让用户在“我的”页面主动检查、下载并安装正式签名的新版 APK，不再需要自行查找 GitHub Release；当内容包要求更高 App 版本时，可直接进入同一 App 更新流程。

## Confirmed Facts

- Android 最低版本为 API 28，当前应用为 `versionName 1.6.1`、`versionCode 8`。
- 正式 App Release 使用 `app-vX.Y.Z` 标签，包含 APK 与同名 `.sha256` 文件，并始终发布为 `non-latest`。
- 仓库的 `latest` Release 保留给内容更新清单，不能作为 App 最新版本查询入口。
- 正式版本使用同一签名证书，可覆盖安装并保留 Room 中的收藏、点赞、笔记和阅读状态。
- 现有“检查更新”只更新内容；网络操作必须由用户显式触发，不能在启动或后台自动执行。
- 现有 `FileProvider` 仅开放应用缓存中的分享图片目录，可扩展一个独立的 APK 更新缓存目录。

## Requirements

- “我的”页面分别显示当前应用版本和内容版本，并提供“检查应用更新”与“检查内容更新”两个明确入口。
- App 更新检查从 GitHub Releases API 获取发布列表，只接受非草稿、非预发布且标签符合 `app-vMAJOR.MINOR.PATCH` 的 Release。
- 只接受名称严格匹配版本号的 APK 与 `.sha256` 资产；按语义版本选择高于当前 `BuildConfig.VERSION_NAME` 的最高正式版本。
- 发现更新时显示版本号、发布日期、APK 大小和发布说明，用户确认后才开始下载。
- APK 必须流式写入应用缓存，限制最大体积，支持进度与取消；失败或取消时删除不完整文件。
- 下载后必须使用 Release 附带的 SHA-256 文件校验 APK，再交给 Android 系统安装器；系统签名校验仍作为覆盖安装的最终信任边界。
- Android 8 及以上未授权“安装未知应用”时，引导用户打开当前应用的系统授权页；授权返回后继续拉起安装器，拒绝授权时保留可重试入口。
- 内容清单的 `minimumAppVersionCode` 高于当前应用时，错误对话框提供“更新应用”操作并切换到 App 更新流程。
- 所有远端地址必须为 HTTPS 且属于允许的 GitHub 主机；错误提示使用可操作的中文，不暴露路径、响应正文或堆栈。
- 更新检查只由用户点击触发；不得自动检查、后台下载或静默安装。
- 新功能发布版本升级为 `versionName 1.7.0`、`versionCode 9`，App Release 工作流同步校验 `versionCode 9`。

## Acceptance Criteria

- [ ] 当前没有更高 App Release 时显示“当前已经是最新应用”。
- [ ] 存在更高正式 App Release 时展示准确版本、日期、大小和说明，草稿、预发布、内容标签及资产不完整的 Release 被忽略。
- [ ] 用户确认后可看到下载进度并可取消；取消、网络失败、体积超限和哈希错误均不会留下可安装的 APK。
- [ ] SHA-256 正确时可通过 `FileProvider` 拉起系统 APK 安装器；权限不足时先进入系统授权页，返回后可继续安装。
- [ ] “我的”页面两个更新入口语义清晰，在最小 `360 x 640 dp` 视口无文字或按钮重叠。
- [ ] 内容版本不兼容提示可直接进入 App 更新检查。
- [ ] Android manifest、FileProvider 路径和发布工作流与 `1.7.0 / 9` 保持一致。
- [ ] App 更新解析、筛选、下载、大小限制和 SHA-256 校验有 JVM 回归测试。
- [ ] Debug JVM tests、Android Lint 和 Debug APK 构建通过；可用设备存在时运行 instrumentation。

## Out Of Scope

- 启动时、后台或定时自动检查更新。
- 静默安装、root/设备管理器安装、应用内回滚或差分 APK。
- Google Play In-App Updates、应用商店渠道与多 ABI 拆分包。
- 本任务内提交、推送标签或发布 GitHub Release；这些外部操作仍需单独授权。
