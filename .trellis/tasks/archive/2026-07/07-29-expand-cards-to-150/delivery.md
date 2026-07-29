# 1.4.0 发布记录

发布日期：2026-07-29

## 源码与标签

- 工作提交：`4fdff28238dfca4c8b1e88623cd4e32f795df283`
- `content-v1.4.0` 与 `app-v1.4.0` 均为 annotated tag，均指向上述提交。
- `main` 常规检查：<https://github.com/Hi-prof/maoxuan/actions/runs/30453456831>
- 内容发布检查：<https://github.com/Hi-prof/maoxuan/actions/runs/30453729231>
- APK 发布检查：<https://github.com/Hi-prof/maoxuan/actions/runs/30454030727>
- 三个工作流均为 `completed / success`。

## 内容 Release

- Release：<https://github.com/Hi-prof/maoxuan/releases/tag/content-v1.4.0>
- 状态：公开、非预发布，并保持仓库 latest。
- `content-v1.4.0.zip`：2,574,072 bytes
- 公开 ZIP SHA-256：`0360e999078c3435cf55ae4564a6a95598b0d0a32cf3dd4506229d6d40fe6fa4`
- `manifest.json` SHA-256：`fd098def771932e738acc53f5bc2c99242e717c35e8419f9c410024c70b72340`
- 独立下载后验证 manifest 的版本、字节数和 SHA-256 与 ZIP 一致；ZIP 内为 schema 3、内容版本 1.4.0、150 张卡片。
- Windows 本地确定性 ZIP 为 2,575,181 bytes、SHA-256 `f915f8dbf2fda72d7f6ac9d98e803dadeb0919a3d81c7552b2c4836f0b68bcc6`。跨平台 zlib 可产生不同压缩字节，公开 manifest 中的 Linux 构建值是远端下载合同。

## Android Release

- Release：<https://github.com/Hi-prof/maoxuan/releases/tag/app-v1.4.0>
- APK：<https://github.com/Hi-prof/maoxuan/releases/download/app-v1.4.0/xinghuo-zhaidu-v1.4.0.apk>
- 状态：公开、非预发布、non-latest；latest 仍为 `content-v1.4.0`。
- APK 大小：21,194,755 bytes
- APK SHA-256：`4afb4619893cd2b197ace09667058e65f05ec44a43b4ba7280db538f992ad465`
- 下载后的 APK、配套 `.sha256` 和 GitHub asset digest 三方一致。
- 包名：`com.xuhuangbin.xinghuozhaidu`
- 版本：`versionCode=5`、`versionName=1.4.0`
- `apksigner` 验证 APK Signature Scheme v2 有效，签名者数量为 1。
- 签名证书 SHA-256：`26cbe74325edda68654627cc1fdc7a71c0a4ce14f3b4875ded635ca0a23ba411`，与公开 1.3.0 APK 一致，可覆盖升级。

## 验证与遗留

- Python ruff 通过，pytest 为 `24 passed`。
- 正式内容校验为 `1.4.0 / 150 published / 0 withdrawals / 8 images`。
- Android debug 单测、lint 和 assemble 本地通过；GitHub Release 单测、release lint、R8 构建、签名和版本校验通过。
- 本机 `adb devices` 无可用设备，未运行 `connectedDebugAndroidTest`；这是唯一未执行的验证项。
- 未提交 `.trellis/spec/frontend/`、`InterpretationInstrumentedTest.kt` 与 `CardActions.kt` 的既有界面改动。
