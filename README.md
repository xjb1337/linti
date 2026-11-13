# 临题

自用刷题 · 导入即练。临题是一个本地优先的安卓医学题库工具，当前为 Batch 1：
工程骨架、本地解析与校对入库、题库/错题/计分/考试数据层，以及可导航的 Compose UI 壳。

## 本机构建

环境：JDK 17、Android SDK 35、仓库内的 Gradle 8.9 wrapper。

```bash
export HTTP_PROXY=http://127.0.0.1:7891
export HTTPS_PROXY=http://127.0.0.1:7891
export NO_PROXY=127.0.0.1,localhost

JAVA_HOME=/home/box/jdk17 ANDROID_HOME=/home/box/android-sdk bash ./gradlew \
  testDebugUnitTest assembleDebug --no-daemon --stacktrace
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`。
`local.properties` 仅用于本机 SDK 路径，已被 `.gitignore` 排除。

## 功能范围

- 首页支持 TXT、DOCX、XLSX、CSV、PDF 文件选择，图片与拍照入口已接系统能力；图片 OCR
  目前返回明确的占位提示。
- 本地规则解析题号、题型、跨行题干/选项、行内/文末答案和解析；异常会保留待确认原因。
- 校对页可编辑题干、选项、答案、解析和知识点，可选择已有题库或新建题库后入库。
- Room 保存题库、题目、导入记录、收藏、笔记、错题、顺序进度与练习/考试会话。
- 顺序练习、模拟考试、错题重练、收藏练习、闪卡和背题均可进入；错题本为二级页。
- 答题支持单选、多选、判断、填空，包含答题卡、标记、收藏、笔记、交卷和成绩报告。
- 抽题、选项乱序、错题/未做优先、多选部分分、填空归一化判分和错题状态机均为纯 Kotlin，
  并有单元测试。
- JSON 备份/恢复通过系统文件选择器导出与读取；示例题库为 10 道虚构教材题。

## 数据与隐私

题库和学习记录保存在应用私有目录的 `linti.db` 中；AI Key 使用
EncryptedSharedPreferences 保存（设备密钥库异常时回退到应用私有存储），不会预填或回显
完整内容。AI 为可选补充能力，默认不启用，只接受 HTTPS Base URL；应用不内置任何 API Key。

应用无账号、无会员、无广告、无锁、无付费解锁。应用自身功能全部免费；用户自行配置的
第三方 AI 服务可能有独立计费规则。

示例题和测试数据均为虚构教材内容，不含真实患者姓名、住院号、床号或其他身份信息。

## 当前边界

- OCR、拍照裁切和扫描 PDF 识别将在后续批次接入。
- 试题/答案分文件目前进入统一校对入口并提示尚未完善。
- UI 已完成可导航与关键流程，间距、动效和完整视觉还原留待 Batch 2。
- 不包含签名 release 包；Release 签名仅读取 CI 环境中显式提供的
  `ANDROID_KEYSTORE_FILE`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、
  `ANDROID_KEY_PASSWORD`，仓库不提交 keystore、密码或真实密钥。

## CI

`.github/workflows/android.yml` 在 push/PR 运行 `testDebugUnitTest` 与 `lintDebug`；
仅手动 `workflow_dispatch` 时构建并上传 debug APK，不构建签名的 release 包。

## 发版

维护者在 `main` 上推送 `vX.Y.Z` tag（例如 `v0.1.0`）后，
`.github/workflows/release.yml` 会运行单测与 lint，构建正式签名 APK，校验签名证书、
包名和版本信息，然后创建 GitHub Release，并附带 `linti-X.Y.Z.apk` 与对应的 `.sha256`
文件。签名材料仅由 CI 从 GitHub Secrets 注入，不保存在仓库中。

手动 `workflow_dispatch` 重跑也必须选择已推送的 `v*` tag；在普通分支上触发会失败。
