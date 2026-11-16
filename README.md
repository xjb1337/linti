# 临题

自用刷题 · 导入即练。临题是一个本地优先的安卓题库与刷题工具，当前版本为 v0.7.0：
支持多格式本地导题、图片与扫描 PDF 的在线 OCR、AI 出题与解析，以及顺序、专项、章节、历年真题、模拟考试、错题本、收藏、闪卡与背题等完整学习闭环。

## 本机构建

环境：JDK 17、Android SDK 35、仓库内的 Gradle 8.9 wrapper。

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --stacktrace
```

Windows PowerShell 下可执行 `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain`。
调试 APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`。
`local.properties` 仅用于本机 SDK 路径，已被 `.gitignore` 排除。

## 功能概览

- **多渠道导题与校对**：首页支持 TXT、DOCX、XLSX、CSV、PDF 本地规则解析、试题/答案分文件导入、手动录入，以及图片与扫描 PDF 的在线 OCR 识别；导题页支持查看原图/PDF 页并人工校对、编辑题干、选项、答案、解析与知识点后入库。
- **AI 出题与解析（可选）**：支持按主题、材料或参照已有题目生成单选、多选、判断、填空、简答及材料题；支持在答题与回顾中按题生成并缓存 AI 解析。默认接入官方 `https://api.deepseek.com`（`deepseek-flash`），只需填写用户自己的 API Key 即可启用。
- **题库与图片管理**：支持题库搜索、重命名、单题/批量整理与题库级联删除（保留已交卷模考快照与引用图片）；题图采用内容寻址去重存储、分档降采样与 LRU 内存缓存，缩略图与大图按需解码。
- **全模式刷题与模考**：支持顺序练习（断点续练、可重新开始一轮）、专项练习、章节练习、历年真题、错题本、收藏练习、背题模式与闪卡复习调度；模拟考试支持自定义各题型题量与分值、草稿保存与退出续考、倒计时自动交卷、成绩分析、原卷错题回看及最近模考趋势。
- **纸感/石墨护眼界面与无障碍**：提供温润纸感浅色、深石墨护眼深色及多套主题配色；首页、题库、练习、答题、我的采用 Bento 卡片结构与等宽数字排版；支持答题字号调节、答对自动跳题、轻微触觉反馈与完整读屏无障碍语义。
- **数据备份与示例体验**：支持将题库、图片与学习记录打包导出为 ZIP/JSON 归档并校验恢复（兼容旧版备份）；内置 10 道虚构临床基础示例题库供快速体验。

## 数据与隐私

- 题库、作答记录、错题、收藏、笔记、闪卡与模考快照均保存在应用私有目录的 `linti.db` 中（Room schema v13，全程使用非破坏性增量迁移，保留历史已交卷数据）。
- 练习偏好保存在 SharedPreferences `linti_quiz_prefs` 中；AI 与 OCR API Key 使用 `EncryptedSharedPreferences` 本机加密存储，仅支持 HTTPS Base URL，且请求禁止自动重定向，应用不内置任何 API Key。
- 应用无账号、无会员、无广告、无付费解锁，自身功能全部免费；用户自行配置的第三方 AI / OCR 服务可能有各自计费规则。
- 示例题与测试数据均为虚构教材内容，不含真实患者姓名、住院号、床号或其他身份信息。

## 安全与发版边界

- 仓库不包含签名 Release Keystore；正式包签名材料仅由 CI 从 GitHub Secrets（`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`、`EXPECTED_CERT_SHA256`）注入，不保存在仓库中。

## CI

`.github/workflows/android.yml` 在 `main` 分支 push 与 PR 时自动运行 `testDebugUnitTest` 与 `lintDebug`；
仅手动 `workflow_dispatch` 时构建并上传 debug APK，不构建签名的 release 包。

## 发版

维护者先在 `app/build.gradle.kts` 设置版本名并递增 Android 版本号，更新 `docs/releases/vX.Y.Z.md` 后在 `main` 上推送同名 `vX.Y.Z` tag（例如 `v0.7.0`）。
`.github/workflows/release.yml` 会运行单测与 lint，构建正式签名 APK，校验签名证书、包名和版本信息，然后创建 GitHub Release，并附带 `linti-X.Y.Z.apk` 与对应的 `.sha256` 文件。

手动 `workflow_dispatch` 重跑也必须选择已推送的 `v*` tag；在普通分支上触发会失败。
