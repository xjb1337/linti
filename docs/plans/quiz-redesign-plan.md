# 临题 v0.2.0 · 做题页重构实施方案

> 读者：实施方（另一个大模型）。完成后由审查方按第 10 节逐条验收。
> 基线：`main` @ `c6f27c4`（= tag `v0.1.0`，已正式发布，用户手机上已有数据）。
> 规则：只做本文档写明的内容。遇到文档没覆盖或互相矛盾的地方，选最保守的做法，并在交付说明里列出来，不要自行扩大范围。

---

## 0. 硬性约束（违反任一条直接退回）

1. **v0.1.0 用户数据不得丢失**。数据库 version 1 → 2 必须写显式 `Migration(1, 2)`；禁止任何 `fallbackToDestructiveMigration*`。
2. 开启 Room schema 导出（`exportSchema = true`），提交 `app/schemas/com.xjb.linti.data.db.LintiDatabase/1.json` 与 `2.json`。`1.json` 必须由**未改动实体的 v0.1.0 代码**生成（见第 7 节第 1 步）。
3. 不改包名、`applicationId`、签名配置、`.github/workflows/*`；不提交 keystore、密码、`local.properties`。
4. 不新增权限（Manifest 仍只有 `INTERNET`），不新增任何网络请求，不新增第三方依赖（仅允许 `androidx.room:room-testing` 与 androidTest 所需的 `androidx.test` 系列）。
5. 范围外不做：全站统计、语音、分享、AI 深度讲解、会员/VIP、账号、OCR、试题纠错上报。
6. 现有单元测试全部保持通过；第 8 节要求的测试必须存在并通过。
7. 用户可见文字为简体中文；不加解释性注释；不留 `TODO`。
8. 在本地分支 `feat/quiz-redesign` 上按阶段提交（第 7 节），提交信息用 `feat:`/`fix:`/`refactor:`/`test:`/`docs:` + 中文。**不 push、不打 tag、不发 release。**
9. 不得提交 `.codegraph/`（本地索引目录）；本方案文件 `docs/plans/quiz-redesign-plan.md` 可随第一个 commit 提交，但不得修改其内容。

---

## 1. 必须解决的问题

| 编号 | 问题 | 现位置 |
|---|---|---|
| P1 | 练习模式选完可无限改；先错后改对，交卷按正确计、不进错题本 | `ui/screens/QuizScreens.kt:283`；`ui/AppViewModel.kt:530 selectAnswer` |
| P2 | 多选题点第一个选项就揭示答案；考试"答后显示答案"开启时，多选首次点击后锁死 | `QuizScreens.kt:159 revealAnswer` |
| P3 | 判断题同 P1；填空"核对"后改字可重新核对 | `QuizScreens.kt:312/324/343` |
| P4 | 底栏 4 个等宽按钮 + `OutlinedButton` 默认 24dp 水平内边距 → 文字竖排 | `QuizScreens.kt:397`；`ui/components/LintiComponents.kt:271/283` |
| P5 | 不能左右滑动切题 | `QuizScreen` |
| P6 | 作答只在交卷时落库；中途退出/进程被杀，错题与统计全丢 | `AppViewModel.submitQuiz` |
| P7 | 顺序进度 = `maxOf(旧已答数, 本次已答数)`，跨次不累计；再次进入看不到以前作答 | `AppViewModel.kt:356/642/649/687` |
| P8 | 系统返回/手势返回不弹确认、不走统一逻辑 | `QuizScreen` 无 `BackHandler` |
| P9 | 模拟考试无倒计时、超时不交卷 | 全局无计时 |
| P10 | 只有浅色主题，页面直接 import 顶层颜色常量 | `ui/theme/Color.kt`、`Theme.kt`、全部 screen/component |
| P11 | 数据库无迁移、`exportSchema = false` | `data/db/LintiDatabase.kt:19-20` |
| P12 | 无答题设置（自动下一题、字体、阅读模式、重练本题、试题编辑） | — |

---

## 2. 交互规格（与设计稿 05 冲突时以本节为准）

### 2.1 作答提交与锁定

"已提交"（committed）= 该题作答已判分并锁定。

**练习类模式**（`SEQUENCE`、`WRONG`、`FAVORITE`、单题练习）：

| 题型 | 何时提交 | 提交前 | 提交后 |
|---|---|---|---|
| 单选 | 第一次点选项即提交 | — | 锁定；立即显示对错与解析 |
| 判断 | 第一次点"正确/错误"即提交 | — | 同上 |
| 多选 | 点"确认答案"按钮 | 点选项只切换勾选，**不显示任何对错**；未选任何项时按钮禁用 | 同上 |
| 填空 | 点"确认答案"按钮 | 输入为空白时按钮禁用 | 输入框只读；同上 |

- 已提交的题，再点选项不改变任何状态。每道题最多提示一次 snackbar："本题已作答，可在设置里重练本题"。
- 唯一解锁方式：设置面板里的"重练本题"（2.4）。
- 提交即落库（4.3），不再有"交卷"。

**考试模式**（`EXAM`）：

- `showAnswerAfterAnswer = false`（默认）：交卷前可任意修改；从不显示对错；多选/填空**不显示**确认按钮，作答实时存在会话里；交卷时统一判分落库。
- `showAnswerAfterAnswer = true`：锁定规则与练习模式完全相同（单选/判断点即锁定，多选/填空需确认），但作答记录、错题仍在**交卷时**统一写入。

以上规则必须在 ViewModel/领域层强制执行，不能只靠 UI 不响应点击。

### 2.2 做题页结构

```
顶栏    ← | 标题（练习："顺序练习"等模式名；考试：倒计时 mm:ss）| 收藏 | 笔记 | ⋯
进度行  12 / 50  [细进度条]                          已做 30
Pager   每页一道题，可左右滑动，页内可垂直滚动
        ├ [单选/多选/判断/填空 胶囊] [考试：x 分] 题干
        ├ 选项 / 判断两项 / 填空输入框
        ├ 「确认答案」（仅多选/填空，且未提交、且需要确认时）
        └ 答案区（已揭示时）：
            正确答案 D（绿）  你的答案 B（红；答对时为绿）
            本人作答 N 次 · 正确率 xx%          ← 来自 question_attempts，含本次
            ── 解析 ──  无解析显示"暂无解析"
            ── 我的笔记 ──（有笔记时显示前 3 行，点击打开笔记弹窗）
底栏    练习：[☆ 收藏] [✓ 对数] [✗ 错数] [已做 x/y]   ← 点"已做"打开答题卡
        考试：[答题卡 已答 x/y] [交卷（主按钮）]
```

- 选项样式参考考试宝：左侧 36–40dp 圆形字母徽标；揭示后正确项徽标变绿底白色✓、所选错误项变红底白色✗、正确项文字变绿；未揭示时已选项徽标为主色实心。每项最小高度 48dp，整行可点。
- 底栏：高 56–64dp；所有文字 `maxLines = 1`；屏宽 ≥ 320dp 且系统字体最大时不得换行或竖排。对数/错数按本次会话计（含 4.4 预填的历史作答）。
- 顶栏旗标按钮移除；"标记本题"移到设置面板，答题卡继续显示标记。
- 练习模式答完最后一题（全部已提交）时，在最后一页答案区下方显示"本轮完成"卡片：对 N · 错 N · 正确率，按钮"查看错题"（有错题时）、"返回题库"。
- 删除原来页面中的"练习即时判分"胶囊、"本题已标记"提示卡、考试中"交卷后显示答案"提示卡（改为在考试开始时 snackbar 提示一次）。

### 2.3 左右滑动

- 用 `androidx.compose.foundation.pager.HorizontalPager`（当前 BOM 2024.09.03 对应 foundation 1.7，可直接用），`key = { questions[it].questionId }`，`beyondViewportPageCount = 1`。
- `pagerState.settledPage` 变化 → 调 `goToQuestion(page)`；`currentIndex` 被外部改变（答题卡、自动跳题）→ `animateScrollToPage`。用 `settledPage` 而不是 `currentPage`，避免滑动途中反复写库；两个方向的同步不得互相触发成循环。
- 首题不能再往右、末题不能再往左，不循环。
- 填空输入框聚焦、IME 弹出/收起时页码不得变化。

### 2.4 设置面板（顶栏"⋯" → `ModalBottomSheet`，需 `@OptIn(ExperimentalMaterial3Api::class)`）

| 项 | 控件 | 默认 | 持久化 | 练习 | 考试 |
|---|---|---|---|---|---|
| 试题编辑 | 跳转 | — | — | 显示 | **隐藏**（选项已打乱） |
| 重练本题 | 动作 + 二次确认 | — | — | 仅已提交时可点 | 隐藏 |
| 标记本题 | 开关 | 关 | 会话内 | 显示 | 显示 |
| 答对自动跳转下一题 | 开关 | 开 | 是 | 显示 | 隐藏 |
| 字体大小 | 小/标准/大/较大 | 标准 | 是 | 显示 | 显示 |
| 阅读模式 | 日间/夜间/护眼 | 日间 | 是 | 显示 | 显示 |

- 重练本题：仅清除本题在**当前会话**的作答与提交状态；历史 `question_attempts` 不删；再次提交会新增一条记录。
- 答对自动跳转：练习模式答对提交后 600ms `animateScrollToPage(next)`；答错不跳；最后一题不跳；用户在 600ms 内手动滑走则取消。
- 字体倍率：小 0.875 / 标准 1.0 / 大 1.125 / 较大 1.25，作用于题干、选项、答案区、解析（做题页与答题卡内的题目文字），不影响全局导航和其他页面。
- 阅读模式全 App 立即生效（5.2）。

### 2.5 考试倒计时

- 剩余时间 = `startedAt + durationMinutes × 60_000 − now`，`now` 来自可注入时钟；每秒刷新显示，**不得用累加 tick**。
- 剩余 ≤ 5 分钟：倒计时文字变警告色，snackbar 提示一次。
- 剩余 ≤ 0：自动交卷（不弹确认），跳成绩页；成绩页顶部显示"已到时自动交卷"。
- App 在后台时到点：回到前台立即自动交卷。
- `durationMinutes = 0` 视为不限时，不显示倒计时。

### 2.6 返回

- `BackHandler` 与左上角返回走同一个函数。
- 练习模式：直接返回，不弹窗（作答已实时落库）；返回前保存当前题位置。
- 考试模式：弹窗"退出考试？本次作答不会保存。" → "退出" / "继续作答"。

### 2.7 答题卡（`AnswerSheetScreen`）

- 练习：图例"正确/错误/未做/当前/已标记"；底部只有"继续作答"。
- 考试且未揭示：图例"已答/未做/当前/已标记"；底部"继续作答"+"交卷"。
- 6 列网格；点击格子回到做题页并定位到该题（Pager 同步）。

---

## 3. 不需要改的现有行为

- 导入、校对、题库、闪卡、背题、搜索、收藏页、笔记页、AI 设置的功能逻辑（仅做 5.2 的配色替换）。
- `Grader`、`ExamBuilder`、`AnswerNormalizer`、`WrongItemStateMachine` 的判分与抽题规则。
- 成绩页（只用于考试）的布局。

---

## 4. 数据层

### 4.1 新表 `question_attempts`

```kotlin
@Entity(
    tableName = "question_attempts",
    foreignKeys = [
        ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"],
            childColumns = ["question_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = QuestionBankEntity::class, parentColumns = ["id"],
            childColumns = ["bank_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("question_id"), Index("bank_id"), Index("answered_at")],
)
data class QuestionAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "question_id") val questionId: Long,
    @ColumnInfo(name = "bank_id") val bankId: Long,
    val mode: String,
    val answer: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
    @ColumnInfo(name = "session_id") val sessionId: Long? = null,
)
```

- `isCorrect` = `Grader.gradeQuestion(...).outcome == CORRECT`（多选部分正确为 false）。
- `answer` 存**原始选项标签**下的答案。考试打乱了选项时，必须先把作答映射回原标签再存（`QuizQuestion` 需增加 `labelMap: Map<String, String>`，即展示标签 → 原标签，由 `ExamBuilder.shuffleOptions` 填写；未打乱时为空 map，表示恒等）。

### 4.2 `practice_progress` 增加一列

- `round_started_at INTEGER NOT NULL DEFAULT 0`：本轮顺序练习的开始时间。
- `answered_count` 的含义改为：该题库在 SEQUENCE 模式下，`answered_at >= round_started_at` 且题目仍存在的**不同 question_id 数**。每次写入 attempt 后在同一事务内重算并写回。

### 4.3 写入时机

练习模式 `commitAnswer`：在**一个 `withTransaction`** 内完成：
1. 插入一条 attempt；
2. 答错 → `recordWrong`；答对且 mode == `WRONG` → `markWrongCorrected`；
3. mode == `SEQUENCE` → 写 `current_index`，重算 `answered_count`。

练习模式不再写 `quiz_sessions`。

考试交卷：一个事务内插入 `quiz_sessions`（拿到 id），再为每道非空作答插入 attempt（`session_id` = 该 id，答案已映射回原标签），写错题本。

### 4.4 进入顺序练习

- 读取本轮（`answered_at >= round_started_at`）每题**最近一次** SEQUENCE attempt，预填 `answers` 与 `committedIds`：以前做过的题保持"已作答、已揭示"，与考试宝一致。
- 从 `current_index` 开始。
- 本轮全部做完（`answered_count >= 题目数`）时，题库主页和练习中枢的按钮显示"重新开始一轮"；点击后二次确认 → `round_started_at = now`、`current_index = 0`、`answered_count = 0`，历史 attempt 不删。

### 4.5 依赖旧会话表的统计要同步改

以下读 `quiz_sessions` 的地方必须改为合并 attempt，否则练习不再写会话后统计会归零：

| 用途 | 现位置 | 新口径 |
|---|---|---|
| 学习小结：累计/今日答题数、正确率、连续天数 | `AppViewModel.studySummary` | 全部 `question_attempts`（按 `answered_at`），**加上** `id` 不在 `question_attempts.session_id` 中的已完成会话（即未回填的 v0.1.0 旧会话，主要是旧考试），用会话自带的计数字段，避免重复计数 |
| 考试"未做优先" | `LintiRepository.getAnsweredQuestionIds` | 该题库所有 attempt 的 `question_id` ∪ 上述未回填会话里的作答题 |

实施方需把新口径写成单元测试可覆盖的纯函数或 DAO 查询。

### 4.6 `Migration(1, 2)` 内容

用 Kotlin 写的 `Migration` 对象，按顺序：
1. `CREATE TABLE question_attempts ...` 与 3 个索引，SQL 必须与 Room 生成的 `2.json` 完全一致（列名、类型、NOT NULL、外键、索引名）。
2. `ALTER TABLE practice_progress ADD COLUMN round_started_at INTEGER NOT NULL DEFAULT 0`。
3. **回填**：遍历 `quiz_sessions` 中 `status = 'COMPLETED' AND mode != 'EXAM'` 的行，解析 `question_ids_json` 与 `answers_json`（用 `org.json`，与 `JsonCodec` 相同格式；建议直接复用 `JsonCodec` 的解码函数）。对每个非空作答、且题目仍存在的题，查 `questions` 表取类型与标准答案，用 `Grader.gradeQuestion` 判分，插入 attempt：`mode` = 会话 mode，`answered_at` = 会话 `ended_at`，`session_id` = 会话 id。
   - EXAM 会话**不回填**（v0.1.0 没存打乱映射，无法可靠判分）。
4. 对每个 `practice_progress` 中 mode = SEQUENCE 的行，按 4.2 规则用回填后的数据重算 `answered_count`。
5. 不得修改或删除任何已有表的其他数据。

**已知取舍（实施方照做，不要另想办法）**：v0.1.0 中只翻页、从未交卷的顺序练习作答本来就没存，迁移后这部分的"已做数"会变小；`current_index`（上次位置）保留不变。

### 4.7 备份

- `BackupCodec.CURRENT_VERSION = 2`；导出新增 `questionAttempts` 数组，`progress` 新增 `roundStartedAt`。
- 能解码 version 1：`questionAttempts` 为空、`roundStartedAt = 0`；恢复 v1 备份后，按 4.6 第 3–4 步同样的逻辑回填并重算（抽成可复用函数，迁移与恢复共用）。
- 恢复仍在单一事务中进行，删除顺序需包含新表。

---

## 5. 状态、主题与设置

### 5.1 作答状态

- 新建纯 Kotlin `domain/QuizSession.kt`（不依赖 Android），包含：`select`、`commit`、`retry`、`isRevealed`、`needsConfirm`、`canCommit`，输入为当前状态 + 模式 + 配置。ViewModel 只调用它。
- `QuizUiState`：删除 `verifiedIds`，新增 `committedIds: Set<Long>`、`attemptStats: Map<Long, AttemptStats>`（次数、正确次数，进入会话时读库，提交后本地 +1）、`autoSubmitted: Boolean`（倒计时自动交卷标记，供成绩页显示）。
- 揭示判断只能来自 `QuizSession.isRevealed`：练习 = `id in committedIds`；考试 = `config.showAnswerAfterAnswer && id in committedIds`。UI 中不得再自行拼揭示条件。
- 删除 `quizStartAnsweredCount` 字段及所有 `maxOf(...)` 进度逻辑；删除 `revealCurrentAnswer`。
- 编辑题目后：用新内容替换会话里该题；若标准答案变了，清除该题的提交状态与作答。

### 5.2 主题（日间/夜间/护眼）

- `ui/theme/Color.kt` 的 17 个顶层常量改为 `data class LintiPalette(...)`（字段与现有颜色同名，小驼峰），提供 `DayPalette`（数值与现在完全一致）、`NightPalette`、`EyeCarePalette`。
- 通过 `staticCompositionLocalOf<LintiPalette>` 下发，`LintiTheme(readingMode)` 同时生成对应的 Material `ColorScheme`（夜间用 `darkColorScheme`）。
- 夜间：深色背景，正文与背景对比度 ≥ 4.5:1；护眼：暖米黄背景（量级 `#F5EFD9`）、卡片 `#FBF6E6`、正文深褐灰，不使用纯白。
- `ui/` 下（`ui/theme/` 除外）所有对顶层颜色常量的引用改为 `LocalLintiPalette.current.xxx`（或 `LintiTheme.colors.xxx` 访问器）。目前有 10 个文件在用：`LintiRoot.kt`、`components/LintiComponents.kt`、`nav/BottomBar.kt` 和 7 个 screen。完成后下面命令必须无输出：
  ```powershell
  rg -l "^import com\.xjb\.linti\.ui\.theme\.(Primary|OnPrimary|PrimarySoft|PrimaryPale|Background|Surface|TextPrimary|TextSecondary|Line|Success|SuccessSoft|Error|ErrorSoft|Warning|WarningSoft|AiAccent|AiSoft)\b" app/src/main/java/com/xjb/linti/ui
  ```
- 状态栏/导航栏图标：夜间浅色、日间与护眼深色（`enableEdgeToEdge` 的 `SystemBarStyle` 随模式切换）。
- 阶段 4 完成时，日间模式下所有页面外观与 v0.1.0 一致（阶段 5 才改做题页布局）。

### 5.3 偏好存储

- 新建 `data/prefs/QuizPreferences.kt`：普通 `SharedPreferences`，文件名 `linti_quiz_prefs`，**不得**放进 API Key 所在的加密文件或 `linti_settings` 兜底文件。
- 字段：`autoNextOnCorrect: Boolean`、`fontScale: FontScale`（枚举 4 档）、`readingMode: ReadingMode`（`DAY`/`NIGHT`/`EYE_CARE`）。
- 以 `StateFlow` 暴露，`MainActivity` 收集 `readingMode` 传给 `LintiTheme`。挂在 `AppContainer` 上，与 `settingsStore` 并列。

### 5.4 试题编辑

- 新路由 `question_edit/{questionId}`（`Routes.questionEdit(id)`）。
- 把 `ImportReviewScreen.kt:399` 的 `QuestionDraftEditor` 移到 `ui/components/QuestionEditor.kt` 并改为 `internal`，导入校对页继续使用，行为不变。
- 新 `ui/screens/QuestionEditScreen.kt`：加载题目 → 转 `DraftQuestion` 编辑 → 保存时答案用 `AnswerNormalizer.normalizeForStorage` 归一化，调 `repository.saveQuestion`，更新 `updated_at`；题干为空或单选/多选无选项时禁止保存并提示。
- 保存后返回做题页，当前题立即显示新内容（5.1 最后一条）。

---

## 6. 其他必须修复

1. `PrimaryActionButton`、`OutlinedActionButton` 新增 `contentPadding` 参数（默认值保持现状），内部 `Text` 加 `maxLines = 1`、`overflow = TextOverflow.Ellipsis`。
2. 结果页路由只给考试使用；练习模式代码路径中不再 `navigate(Routes.Result)`。
3. README：更新功能说明与"当前边界"（新增设置、主题、实时记录、迁移说明）。
4. `app/build.gradle.kts`：`versionName` 默认值改 `0.2.0`、`versionCode` 默认值改 `2`；增加
   ```kotlin
   ksp { arg("room.schemaLocation", "$projectDir/schemas") }
   // android { } 块内：
   sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") }
   ```
   及 androidTest 依赖：`androidx.room:room-testing:2.6.1`、`androidx.test.ext:junit`、`androidx.test:runner`。

---

## 7. 实施阶段（每阶段一个 commit，按顺序）

1. **schema 基线**：在**不改任何实体**的前提下，仅把 `exportSchema` 改 `true` 并加 ksp 参数，构建一次生成 `1.json`，提交。
2. **数据层**：新实体/列、DAO、`Migration(1, 2)` + 回填、`2.json`、备份 v2、迁移仪器测试、DAO/备份单测。
3. **作答逻辑**：`QuizSession` + 单测；ViewModel 改造（提交、锁定、实时落库、顺序进度、重新一轮、重练、统计口径、标签映射）。
4. **主题系统**：palette、CompositionLocal、三套配色、全量替换引用。日间外观不变。
5. **做题页 UI**：Pager、新顶栏/底栏、确认按钮、答案区、本轮完成卡、BackHandler、倒计时、答题卡调整、按钮组件修复。
6. **设置面板与试题编辑**：BottomSheet、`QuizPreferences`、字体倍率、编辑路由与编辑器抽取。
7. **文档与版本号**。

---

## 8. 测试要求

单元测试（JVM，`testDebugUnitTest`）至少覆盖：

- **单选/判断**：首次选择即提交；提交后 `select` 返回原状态；对错判定正确。
- **多选**：提交前切换不揭示；空选 `canCommit = false`；确认后锁定；部分正确 → 非正确。
- **填空**：空白不能提交；提交后不可修改。
- **考试，答后不显示**：交卷前可反复修改；`isRevealed` 恒为 false；不需要确认。
- **考试，答后显示**：锁定行为与练习一致。
- **重练本题**：清除提交与作答后可再次提交。
- **顺序进度**：第一次会话答 50 题、第二次答另外 10 题 → `answered_count = 60`；同一题答两次只计 1；重新一轮后从 0 计且旧 attempt 仍在。
- **标签映射**：打乱选项的考试作答映射回原标签后，与原题标准答案判分一致。
- **倒计时**：注入时钟，剩余时间计算、≤5 分钟警告、≤0 触发自动交卷、`durationMinutes = 0` 不限时。
- **统计口径**：attempt 与旧会话合并，回填过的会话不重复计数。
- **回填函数**：给定 v0.1.0 会话 JSON + 题目，生成正确的 attempt；EXAM 会话被跳过；已删除的题被跳过。
- **备份**：v2 往返一致；v1 JSON 可解码。

仪器测试（`androidTest`，`MigrationTestHelper`）：
- 用 `1.json` 建库，每张表插入至少一行，包括一个 SEQUENCE 已完成会话（含 3 题作答：对、错、空）和一个 EXAM 会话 → `runMigrationsAndValidate(2, true, MIGRATION_1_2)` → 旧数据原样存在；`question_attempts` 恰有 2 行（对、错）；SEQUENCE 进度 `answered_count = 2`，`current_index` 不变。

---

## 9. 本机环境与命令（Windows）

- JDK 17.0.10 已在 PATH；`JAVA_HOME`、`ANDROID_HOME` 未设置。
- Android SDK 在 `E:\android-sdk\android-sdk`（含 platforms;android-35、build-tools 34/35、emulator、system-images;android-35;default;x86_64）。仓库没有 `local.properties`，自行创建一行 `sdk.dir=E:/android-sdk/android-sdk`（已被 gitignore，不得提交）。
- `adb` 不在 PATH，用 `E:\android-sdk\android-sdk\platform-tools\adb.exe`。目前**没有已创建的 AVD**，也没有连接设备；实施方不需要创建模拟器，仪器测试由审查方运行。
- 工作区文件是 CRLF（git `autocrlf`），提交后为 LF；不要整体改换行符。
- 必跑：
  ```powershell
  .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
  ```
- 迁移仪器测试至少要能编译：`.\gradlew.bat assembleDebugAndroidTest --no-daemon`。`connectedDebugAndroidTest` 在没有设备时如实写"未运行（无设备）"，**不得声称已通过**。

---

## 10. 交付物与验收

### 10.1 交付说明（实施方最终回复中给出）

1. 分支名与 commit 列表（hash + 标题）。
2. 实际改动文件清单（新增/修改/删除）。
3. P1–P12 每条对应的改动位置（文件:行）。
4. 运行过的命令及结果摘要（通过/失败数、lint 结果）。
5. 未完成项、偏离本方案处及原因、已知限制。

### 10.2 静态审查（审查方，"必须"项任一不过即退回）

- [ ] `LintiDatabase` version = 2、`exportSchema = true`、注册 `MIGRATION_1_2`、无 destructive fallback。
- [ ] `1.json` 与 v0.1.0 实体一致（审查方会 checkout `v0.1.0` 重新生成比对）；`2.json` 与迁移 SQL 一致。
- [ ] 迁移回填跳过 EXAM 会话；不修改旧数据。
- [ ] 锁定在 `QuizSession`/ViewModel 层强制执行；揭示只来自 `isRevealed`。
- [ ] 练习提交在单一事务内写 attempt + 错题 + 进度。
- [ ] `quizStartAnsweredCount`、`maxOf` 进度逻辑、`verifiedIds`、`revealCurrentAnswer` 已删除。
- [ ] 统计与"未做优先"已改为新口径，无重复计数。
- [ ] 考试作答按原标签存储。
- [ ] 5.2 的 `rg` 检查无结果；日间配色数值与原常量一致。
- [ ] 按钮组件 `Text` 有 `maxLines = 1`。
- [ ] 有 `BackHandler`；倒计时基于墙钟、可注入时钟。
- [ ] 偏好文件为 `linti_quiz_prefs`；Manifest 权限未变；未新增网络调用与第三方依赖。
- [ ] 未改签名、workflow、包名；无密钥、无 `local.properties` 入库。
- [ ] 无范围外功能。

### 10.3 自动化（审查方会自行重跑）

- [ ] `testDebugUnitTest` 全过，第 8 节用例均存在。
- [ ] `lintDebug` 无新增 error。
- [ ] `assembleDebug` 成功。
- [ ] `assembleDebugAndroidTest` 成功；审查方在模拟器上运行 `connectedDebugAndroidTest`（迁移测试）通过。

### 10.4 模拟器手测（审查方执行）

- [ ] 安装 v0.1.0 正式包 → 顺序练习答几题并交卷、收藏、写笔记、产生错题、做一次考试 → 覆盖安装新 debug 包（签名不同则改用：v0.1.0 debug 构建 → 新 debug 构建）→ 数据完整，顺序练习位置不变。
- [ ] 单选点错后再点其他选项无变化；错题本立即出现该题；练习页无"交卷"。
- [ ] 多选点第一项不揭示；确认后揭示并锁定。填空确认后只读。
- [ ] 左右滑动切题流畅；答题卡跳转后 Pager 与进度条同步。
- [ ] 底栏在 360dp 宽 + 系统最大字体下不换行不竖排。
- [ ] 自动跳题开/关生效；字体四档生效；日间/夜间/护眼全 App 生效且可读；杀进程重启后设置保留。
- [ ] 重练本题可重答；试题编辑保存后当前页立即更新。
- [ ] 顺序练习退出再进：以前答过的题仍显示答案，从上次位置继续，"已做"数正确；全部做完后可"重新开始一轮"。
- [ ] 答几题后杀进程：已提交的题在错题本与进度里。
- [ ] 考试倒计时可见，≤5 分钟变色，到时自动交卷并在成绩页提示；手势返回弹确认。
- [ ] 备份导出 → 清除应用数据 → 恢复：题库、作答记录、进度、错题、笔记、收藏一致；v0.1.0 导出的备份也能恢复。
