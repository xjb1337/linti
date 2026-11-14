package com.xjb.linti.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xjb.linti.data.repo.ExamRecord
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.SessionSnapshot
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.ExamPaperReview
import com.xjb.linti.domain.Grader
import com.xjb.linti.domain.MultiSelectRule
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamPaperPersistenceTest {
    @Test
    fun submitStoresReadablePaperExactlyOnceWithRollbackOnSnapshotFailure() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "linti-exam-paper-test.db"
        context.deleteDatabase(name)
        val database = Room.databaseBuilder(context, LintiDatabase::class.java, name).build()
        try {
            val repository = LintiRepository(database)
            database.questionBankDao().insert(QuestionBankEntity(id = 1, name = "题库", createdAt = 1, updatedAt = 1))
            listOf(1L, 2L).forEach { id ->
                database.questionDao().insert(
                    QuestionEntity(
                        id = id,
                        bankId = 1,
                        sourceNumber = id.toString(),
                        type = "SINGLE",
                        stem = "题干$id",
                        optionsJson = "[]",
                        answer = "A",
                        explanation = "",
                        sourceFileName = "test",
                        createdAt = 1,
                        updatedAt = 1,
                    ),
                )
            }
            // 考场字母与题库相反：正确答案是考场的 B
            val question = QuizQuestion(
                questionId = 1,
                bankId = 1,
                sourceNumber = "1",
                type = QuestionType.SINGLE,
                stem = "考场原题",
                options = listOf(QuestionOption("A", "原乙"), QuestionOption("B", "原甲")),
                correctAnswer = "B",
                explanation = "原解析",
                labelMap = mapOf("A" to "B", "B" to "A"),
            )
            val questions = listOf(question, question.copy(questionId = 2))
            val config = ExamConfig(singleCount = 2, durationMinutes = 5, passScore = 2.0)
            val answers = mapOf(1L to "B") // 2 未做
            val grading = Grader.grade(questions, answers, config)
            val snapshot = SessionSnapshot(
                bankId = 1,
                bankName = "题库",
                mode = PracticeMode.EXAM,
                config = config,
                questions = questions,
                answers = answers,
                flaggedIds = emptySet(),
                bookmarkedIds = emptySet(),
                startedAt = 1_000,
                endedAt = 2_000,
                grading = grading,
            )
            val draft = ExamDraftSnapshot(
                draftId = "exam-1",
                bankId = 1,
                bankName = "题库",
                config = config,
                questions = questions,
                answers = answers,
                committedIds = emptySet(),
                flaggedIds = emptySet(),
                bookmarkedIds = emptySet(),
                currentIndex = 0,
                startedAt = 1_000,
                revision = 0,
            )
            repository.createExamDraft(draft)

            // 交卷：成绩、原卷、答题记录同事务写入
            val sessionId = repository.submitExamSession(
                snapshot,
                listOf(ExamRecord(1, 1, "A", true)), // ExamRecord.answer 已映射回题库字母
                "exam-1",
            )
            assertNull(repository.getExamDraft(1))

            // 原卷逐题与考场现场一致；未做不出现在错题里
            val review = repository.getExamPaperReview(sessionId)
            assertNotNull(review)
            assertEquals(2, review!!.questions.size)
            assertEquals("B", review.questions[0].userAnswer)
            assertEquals("B", review.questions[0].correctAnswer)
            assertEquals(QuestionOutcome.CORRECT, review.questions[0].outcome)
            assertEquals("原解析", review.questions[0].explanation)
            assertNull(review.questions[1].userAnswer)
            assertEquals(QuestionOutcome.UNANSWERED, review.questions[1].outcome)
            assertEquals(review.questions.filter { it.outcome == QuestionOutcome.WRONG }.size, 0)
            assertEquals(1, database.questionAttemptDao().getAll().size)

            // 同一场考试只能提交一次：草稿已删，再次提交必须失败且不产生新成绩/新原卷
            val duplicate = runCatching {
                repository.submitExamSession(snapshot, emptyList(), "exam-1")
            }
            assertTrue(duplicate.isFailure)
            assertEquals(1, repository.getAllSessions().size)
            assertEquals(1, database.examPaperDao().getAll().size)

            // 普通练习场次与不存在的场次都读不出试卷
            val practiceId = database.quizSessionDao().insert(
                QuizSessionEntity(
                    bankId = 1,
                    bankName = "题库",
                    mode = "SEQUENCE",
                    configJson = "{}",
                    questionIdsJson = "[]",
                    answersJson = "{}",
                    flagsJson = "[]",
                    bookmarksJson = "[]",
                    startedAt = 3_000,
                    status = "COMPLETED",
                ),
            )
            assertNull(repository.getExamPaperReview(practiceId))
            assertNull(repository.getExamPaperReview(9999L))

            // 注入快照写入失败（NaN 分值编码时抛错）：整笔回滚，草稿保留，可重试
            repository.createExamDraft(draft.copy(draftId = "exam-2"))
            val broken = snapshot.copy(
                questions = listOf(question.copy(points = Double.NaN), question.copy(questionId = 2)),
                grading = Grader.grade(
                    listOf(question.copy(points = Double.NaN), question.copy(questionId = 2)),
                    answers,
                    config,
                ),
            )
            val rollback = runCatching {
                repository.submitExamSession(broken, emptyList(), "exam-2")
            }
            assertTrue("快照写入失败应使交卷失败", rollback.isFailure)
            assertNotNull("回滚后草稿仍可续考或重试", repository.getExamDraft(1))
            assertEquals(2, repository.getAllSessions().size)
            assertEquals("失败交卷不留下半条原卷", 1, database.examPaperDao().getAll().size)

            // 重试成功：只补上这一场的一条成绩与一份原卷
            val retryId = repository.submitExamSession(snapshot.copy(startedAt = 4_000), emptyList(), "exam-2")
            assertEquals(3, repository.getAllSessions().size)
            assertEquals(2, database.examPaperDao().getAll().size)
            assertNotNull(repository.getExamPaperReview(retryId))

            // 导出 v3 备份并恢复：原卷逐题一致
            val backup = repository.exportBackupJson()
            assertTrue(backup.contains("examPapers"))
            repository.restoreBackupJson(backup)
            assertEquals(3, repository.getAllSessions().size)
            assertEquals(2, database.examPaperDao().getAll().size)
            val restored = repository.getExamPaperReview(sessionId)!!
            assertEquals(review, restored)

            // 损坏的 v3 快照：恢复报错且设备原有数据原样保留，不会清空后只恢复一半
            val corrupted = JSONObject(backup)
                .put(
                    "examPaperChunks",
                    org.json.JSONArray()
                        .put(JSONObject().put("sessionId", sessionId).put("kind", "metadata").put("ordinal", 0).put("part", 0).put("content", "不是快照内容")),
                )
                .toString()
            val damaged = runCatching { repository.restoreBackupJson(corrupted) }
            assertTrue("损坏备份必须报错", damaged.isFailure)
            assertEquals(3, repository.getAllSessions().size)
            assertEquals(1, database.questionBankDao().getAll().size)
            assertNotNull(repository.getExamPaperReview(sessionId))

            // v1/v2 旧备份恢复：旧成绩照常可看，明确没有原卷
            val legacy = """
                {
                  "version": 2,
                  "exportedAt": 10,
                  "banks": [{"id": 5, "name": "旧备份题库", "createdAt": 1, "updatedAt": 1}],
                  "questions": [],
                  "progress": [],
                  "questionAttempts": []
                }
            """.trimIndent()
            repository.restoreBackupJson(legacy)
            assertEquals(0, repository.getAllSessions().size)
            assertEquals(0, database.examPaperDao().getAll().size)
            assertNull(repository.getExamPaperReview(sessionId))

            // 删除会话级联清理原卷，不外挂到题库或题目
            database.questionBankDao().insert(QuestionBankEntity(id = 2, name = "另一题库", createdAt = 1, updatedAt = 1))
            val examId = database.quizSessionDao().insert(
                QuizSessionEntity(
                    bankId = 2,
                    bankName = "另一题库",
                    mode = "EXAM",
                    configJson = "{}",
                    questionIdsJson = "[]",
                    answersJson = "{}",
                    flagsJson = "[]",
                    bookmarksJson = "[]",
                    startedAt = 5_000,
                    status = "COMPLETED",
                ),
            )
            database.examPaperDao().insert(ExamPaperEntity(examId, ExamPaperCodec.FORMAT_VERSION, 1))
            assertEquals(1, database.examPaperDao().getAll().size)
            database.quizSessionDao().deleteAll()
            assertEquals("会话删除应级联删除原卷", 0, database.examPaperDao().getAll().size)
            assertEquals("题库仍存在", 2, database.questionBankDao().getAll().size)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun restoreRejectsInvalidPaperBackupsWithoutOverwritingDeviceData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "linti-exam-paper-backup-test.db"
        context.deleteDatabase(name)
        val database = Room.databaseBuilder(context, LintiDatabase::class.java, name).build()
        try {
            val repository = LintiRepository(database)
            database.questionBankDao().insert(QuestionBankEntity(id = 1, name = "题库", createdAt = 1, updatedAt = 1))
            database.questionDao().insert(
                QuestionEntity(
                    id = 1,
                    bankId = 1,
                    sourceNumber = "1",
                    type = "SINGLE",
                    stem = "题干",
                    optionsJson = "[]",
                    answer = "A",
                    explanation = "",
                    sourceFileName = "test",
                    createdAt = 1,
                    updatedAt = 1,
                ),
            )
            // 一场已交卷模考 + 全未做的原卷快照（userAnswer/explanation 为显式 null）
            val question = QuizQuestion(
                questionId = 1,
                bankId = 1,
                sourceNumber = "1",
                type = QuestionType.SINGLE,
                stem = "考题",
                options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")),
                correctAnswer = "A",
                explanation = "",
            )
            val config = ExamConfig(singleCount = 1, durationMinutes = 5, passScore = 1.0)
            val sessionId = database.quizSessionDao().insert(
                QuizSessionEntity(
                    id = 7,
                    bankId = 1,
                    bankName = "题库",
                    mode = "EXAM",
                    configJson = "{}",
                    questionIdsJson = "[1]",
                    answersJson = "{}",
                    flagsJson = "[]",
                    bookmarksJson = "[]",
                    startedAt = 1_000,
                    endedAt = 2_000,
                    score = 0.0,
                    passed = false,
                    status = "COMPLETED",
                ),
            )
            val grading = Grader.grade(listOf(question), emptyMap(), config)
            val (paper, chunks) = ExamPaperCodec.encode(sessionId, listOf(question), grading)
            database.examPaperDao().insert(paper)
            database.examPaperDao().insertChunks(chunks)

            val backupJson = repository.exportBackupJson()
            val paperBefore = repository.getExamPaperReview(sessionId)!!

            fun corrupted(transform: (JSONObject) -> Unit): String {
                val root = JSONObject(backupJson)
                transform(root)
                return root.toString()
            }

            // 缺 examPapers 数组：v3 必须显式提供（允许空数组）
            val missingArray = runCatching {
                repository.restoreBackupJson(corrupted { it.remove("examPapers") })
            }
            assertTrue("缺数组的 v3 备份应拒绝恢复", missingArray.isFailure)
            assertDeviceDataIntact(repository, database, sessionId, paperBefore)

            // 多余分块（题序超出 questionCount）：拒绝
            val extraChunk = runCatching {
                repository.restoreBackupJson(
                    corrupted { root ->
                        root.getJSONArray("examPaperChunks").put(
                            JSONObject()
                                .put("sessionId", sessionId)
                                .put("kind", "question")
                                .put("ordinal", 1)
                                .put("part", 0)
                                .put("content", "{}"),
                        )
                    },
                )
            }
            assertTrue("多余分块的备份应拒绝恢复", extraChunk.isFailure)
            assertDeviceDataIntact(repository, database, sessionId, paperBefore)

            // 题块缺 userAnswer 字段：不允许默认值补齐，拒绝
            val missingUserAnswer = runCatching {
                repository.restoreBackupJson(
                    corrupted { root ->
                        val chunkArray = root.getJSONArray("examPaperChunks")
                        for (index in 0 until chunkArray.length()) {
                            val chunk = chunkArray.getJSONObject(index)
                            if (chunk.getString("kind") == "question") {
                                val content = JSONObject(chunk.getString("content"))
                                content.remove("userAnswer")
                                chunk.put("content", content.toString())
                            }
                        }
                    },
                )
            }
            assertTrue("缺作答字段的备份应拒绝恢复", missingUserAnswer.isFailure)
            assertDeviceDataIntact(repository, database, sessionId, paperBefore)

            // 原卷头/分块数组元素换成非法 JSON 值：null、字符串、数字、嵌套数组
            // 一律拒绝恢复、不跳过，原有题库、成绩和原卷保持原样
            val invalidElements: List<Pair<String, (org.json.JSONArray) -> Unit>> = listOf(
                "null" to { array -> array.put(0, JSONObject.NULL) },
                "字符串" to { array -> array.put(0, "不是对象") },
                "数字" to { array -> array.put(0, 42) },
                "嵌套数组" to { array -> array.put(0, org.json.JSONArray().put("嵌套")) },
            )
            for ((label, mutate) in invalidElements) {
                for (arrayKey in listOf("examPapers", "examPaperChunks")) {
                    val attempt = runCatching {
                        repository.restoreBackupJson(
                            corrupted { root -> mutate(root.getJSONArray(arrayKey)) },
                        )
                    }
                    assertTrue("$arrayKey 的 $label 元素应拒绝恢复", attempt.isFailure)
                    assertDeviceDataIntact(repository, database, sessionId, paperBefore)
                }
            }

            // 快照归属普通练习场次：拒绝
            database.quizSessionDao().insert(
                QuizSessionEntity(
                    id = 8,
                    bankId = 1,
                    bankName = "题库",
                    mode = "SEQUENCE",
                    configJson = "{}",
                    questionIdsJson = "[]",
                    answersJson = "{}",
                    flagsJson = "[]",
                    bookmarksJson = "[]",
                    startedAt = 3_000,
                    status = "COMPLETED",
                ),
            )
            val practiceOwner = runCatching {
                repository.restoreBackupJson(
                    corrupted { root ->
                        root.getJSONArray("examPapers").getJSONObject(0).put("sessionId", 8)
                        val chunkArray = root.getJSONArray("examPaperChunks")
                        for (index in 0 until chunkArray.length()) {
                            chunkArray.getJSONObject(index).put("sessionId", 8)
                        }
                    },
                )
            }
            assertTrue("归属非模考场次的快照应拒绝恢复", practiceOwner.isFailure)
            assertDeviceDataIntact(repository, database, sessionId, paperBefore, expectedSessions = 2)

            // 对照：同样的备份原样恢复成功且原卷一致
            repository.restoreBackupJson(backupJson)
            assertEquals(paperBefore, repository.getExamPaperReview(sessionId))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}

private suspend fun assertDeviceDataIntact(
    repository: LintiRepository,
    database: LintiDatabase,
    sessionId: Long,
    expectedPaper: ExamPaperReview,
    expectedSessions: Int = 1,
) {
    assertEquals("题库未被覆盖", 1, database.questionBankDao().getAll().size)
    assertEquals("成绩未被覆盖", expectedSessions, repository.getAllSessions().size)
    assertEquals(expectedPaper, repository.getExamPaperReview(sessionId))
}
