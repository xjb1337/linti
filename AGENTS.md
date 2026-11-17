# Linti 项目开发约束

## 1. Room 数据库与迁移
- 每次修改 `LintiDatabase` 表结构或递增 `version` 时，必须：
  1. 在 `Migrations.kt` 新增对应 `MIGRATION_X_Y` 并在 `LintiDatabase.create()` 中注册。
  2. 同步更新所有直接打开最新版本号 `LintiDatabase` 的迁移测试（如 `app/src/test/java/com/xjb/linti/data/db/ChapterMigrationTest.kt` 中的 `.addMigrations(...)`）。
  3. 提交由 KSP 导出的最新 `app/schemas/com.xjb.linti.data.db.LintiDatabase/<version>.json`。
- 数据库迁移测试统一维护在 `app/src/test/`（由 CI `./gradlew testDebugUnitTest` 执行），不要在 `app/src/androidTest/` 重复复制。

## 2. 图片存储与清理（`QuestionImageStore` / `cleanImages`）
- 任何新增或扩展的实体若引用了 `QuestionImage`（如草稿、原卷快照、待校对导入任务），必须在 `LintiRepository.cleanImages()` 中纳入其 `fileName` 引用，否则暂存图片会在启动或清理时被误删。
- `QuestionImageStore.FILE_NAME` 仅接受 64 位小写十六进制哈希文件名（`[0-9a-f]{64}\.(jpg|png)`）；测试中构造 `QuestionImage` 或调用 `JsonCodec.decodeImages()` 时需使用合法格式的文件名。

## 3. UI 与 ViewModel 架构边界
- `LintiViewModel`（`AppViewModel.kt`）是对外门面，新业务应优先拆入独立协调器（如 `ImportOcrCoordinator`、`QuizExamCoordinator`、`UpdateCoordinator`），并在 `UiStates.kt` 中集中定义状态类型，避免 `AppViewModel.kt` 再次膨胀。
- 重构 `LintiViewModel` 时必须保留其现有构造函数命名参数（如 `aiExplanationRepository`、`aiQuestionRepository`、`practiceSessionStore`），以免破坏测试实例化。
- 长耗时后台任务（如 AI 导题、OCR 上传、大文件解析）必须在 `viewModelScope` 中启动，严禁使用 Composable 内的 `rememberCoroutineScope()`，防止用户切换页面时协程被取消。

## 4. 发版规范（`.github/workflows/release.yml`）
- 推送 `vX.Y.Z` tag 触发自动发版前，必须确保 `app/build.gradle.kts` 中的 `appVersionName` 与 `X.Y.Z` 完全一致，且 `appVersionCode` 已递增为正整数。
