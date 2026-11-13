package com.xjb.linti.data.db

import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.TypeStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamHistoryMapperTest {

    private val fullStats = listOf(
        TypeStats(
            type = QuestionType.SINGLE,
            total = 30,
            correct = 25,
            wrong = 5,
            unanswered = 0,
            score = 50.0,
            maxScore = 60.0,
        ),
        TypeStats(
            type = QuestionType.JUDGE,
            total = 2,
            correct = 1,
            wrong = 0,
            unanswered = 1,
            score = 2.0,
            maxScore = 4.0,
        ),
    )
    private val fullStatsJson = JsonCodec.encodeTypeStats(fullStats)

    private val singleStatJson =
        """{"type":"SINGLE","total":30,"correct":25,"wrong":5,"unanswered":0,"score":50.0,"maxScore":60.0}"""

    private fun session(
        id: Long = 7,
        bankId: Long? = 3,
        bankName: String = "交卷时的题库名",
        mode: String = "EXAM",
        config: ExamConfig = ExamConfig(singleCount = 50, durationMinutes = 60, passScore = 60.0),
        configJson: String? = null,
        questionCount: Int = 32,
        startedAt: Long = 1_760_000_000_000,
        endedAt: Long? = null,
        score: Double? = null,
        passed: Boolean? = null,
        statsJson: String? = null,
        status: String = "COMPLETED",
        correctCount: Int = 0,
        wrongCount: Int = 0,
        unansweredCount: Int = 0,
    ): QuizSessionEntity = QuizSessionEntity(
        id = id,
        bankId = bankId,
        bankName = bankName,
        mode = mode,
        configJson = configJson ?: JsonCodec.encodeExamConfig(config),
        questionIdsJson = JsonCodec.encodeLongList((1..questionCount).map { it.toLong() }),
        answersJson = "{}",
        flagsJson = "[]",
        bookmarksJson = "[]",
        startedAt = startedAt,
        endedAt = endedAt,
        score = score,
        passed = passed,
        statsJson = statsJson,
        status = status,
        correctCount = correctCount,
        wrongCount = wrongCount,
        unansweredCount = unansweredCount,
    )

    @Test
    fun `满分取各题型 maxScore 之和而不是组卷配置的计划满分`() {
        val entity = session(
            endedAt = 1_760_000_000_000 + 59 * 60_000,
            score = 52.0,
            passed = true,
            statsJson = fullStatsJson,
            correctCount = 26,
            wrongCount = 5,
            unansweredCount = 1,
        )

        val detail = entity.toExamHistoryDetail()
        // 组卷配置 singleCount=50 × 2 分 = 计划满分 100；实际只抽到 32 题、满分 64
        assertEquals(64.0, detail.maxScore!!, 0.0)
        assertEquals(52.0, detail.score!!, 0.0)
        assertEquals(true, detail.passed)
        assertEquals(60.0, detail.passScore!!, 0.0)
        assertEquals(32, detail.totalQuestions)
        assertEquals(26, detail.correctCount)
        assertEquals(5, detail.wrongCount)
        assertEquals(1, detail.unansweredCount)
        assertEquals(59 * 60L, detail.durationSeconds)
        assertEquals(2, detail.typeStats.size)
        assertEquals(1_760_000_000_000L, detail.startedAt)
        assertEquals(entity.endedAt, detail.submittedAt)

        val entry = entity.toExamHistoryEntry()
        assertEquals(64.0, entry.maxScore!!, 0.0)
        assertEquals(entity.endedAt, entry.submittedAt)
        assertEquals("交卷时的题库名", entry.bankName)
    }

    @Test
    fun `真实的 0 分与 0 用时照常返回`() {
        val moment = 1_760_000_000_000
        val entity = session(
            questionCount = 5,
            startedAt = moment,
            endedAt = moment,
            score = 0.0,
            passed = false,
            statsJson = JsonCodec.encodeTypeStats(
                listOf(TypeStats(QuestionType.SINGLE, 5, 0, 5, 0, 0.0, 10.0)),
            ),
            correctCount = 0,
            wrongCount = 5,
            unansweredCount = 0,
        )

        val entry = entity.toExamHistoryEntry()
        assertEquals(0.0, entry.score!!, 0.0)
        assertEquals(false, entry.passed)

        val detail = entity.toExamHistoryDetail()
        assertEquals(0.0, detail.score!!, 0.0)
        assertEquals(10.0, detail.maxScore!!, 0.0)
        assertEquals(0L, detail.durationSeconds)
        assertEquals(0, detail.correctCount)
        assertEquals(5, detail.wrongCount)
        assertEquals(0, detail.unansweredCount)
    }

    @Test
    fun `缺少分题型统计时只保留可信字段`() {
        val entity = session(score = 40.0, passed = false, statsJson = null)

        val entry = entity.toExamHistoryEntry()
        assertEquals(40.0, entry.score!!, 0.0)
        assertNull(entry.maxScore)
        assertEquals(false, entry.passed)

        val detail = entity.toExamHistoryDetail()
        assertNull(detail.maxScore)
        assertTrue(detail.typeStats.isEmpty())
        assertEquals(40.0, detail.score!!, 0.0)
    }

    @Test
    fun `统计条目缺字段或值为 null 时整条丢弃，不求和不完整的满分`() {
        val entity = session(
            questionCount = 5,
            statsJson =
                """[{"type":"SINGLE","total":5,"correct":3,"wrong":1,"unanswered":1,"score":6.0},""" +
                    """{"type":"JUDGE","total":null,"correct":0,"wrong":0,"unanswered":0,"score":0.0,"maxScore":4.0}]""",
        )

        val detail = entity.toExamHistoryDetail()
        assertNull(detail.maxScore)
        assertTrue(detail.typeStats.isEmpty())
        assertNull(entity.toExamHistoryEntry().maxScore)
    }

    @Test
    fun `只解析出部分题型时不显示不完整的满分，已解析条目照常展示`() {
        // 会话保存了 35 题，但统计只覆盖 32 题（部分题型缺失或未解析）
        val entity = session(questionCount = 35, statsJson = fullStatsJson)

        val detail = entity.toExamHistoryDetail()
        assertNull(detail.maxScore)
        assertEquals(2, detail.typeStats.size)
        assertNull(entity.toExamHistoryEntry().maxScore)
    }

    @Test
    fun `答题计数不可信时不给正确率，真实零正确率保留`() {
        // 三项计数全 0 而会话有题 → 计数不可信，正确率不按 0% 展示
        val allZeroCounts = session(
            correctCount = 0,
            wrongCount = 0,
            unansweredCount = 0,
        )
        assertNull(allZeroCounts.toExamHistoryDetail().accuracy)

        // 三项之和与会话题数不一致 → 不可信
        val inconsistent = session(
            correctCount = 25,
            wrongCount = 5,
            unansweredCount = 1,
        )
        assertNull(inconsistent.toExamHistoryDetail().accuracy)

        // 题数缺失，无法交叉验证 → 不可信
        val noQuestionIds = session(
            correctCount = 26,
            wrongCount = 5,
            unansweredCount = 1,
        ).copy(questionIdsJson = "")
        assertNull(noQuestionIds.toExamHistoryDetail().accuracy)

        // 计数可信且答对 0 题 → 真实 0% 保留
        val allWrong = session(
            questionCount = 5,
            statsJson = JsonCodec.encodeTypeStats(
                listOf(TypeStats(QuestionType.SINGLE, 5, 0, 5, 0, 0.0, 10.0)),
            ),
            correctCount = 0,
            wrongCount = 5,
            unansweredCount = 0,
        )
        assertEquals(0.0, allWrong.toExamHistoryDetail().accuracy!!, 0.0)

        // 计数可信 → 正确计算
        val normal = session(
            correctCount = 26,
            wrongCount = 5,
            unansweredCount = 1,
        )
        assertEquals(26.0 / 32.0, normal.toExamHistoryDetail().accuracy!!, 0.0)
    }

    @Test
    fun `分题型统计遇非数字非有限值或无效计数时丢弃条目并按需隐藏满分`() {
        // 非数字（字符串不可解析）→ 条目丢弃 → 统计不完整 → 满分 —
        val nonNumeric = session(
            questionCount = 32,
            statsJson = """[$singleStatJson,""" +
                """{"type":"MULTI","total":"abc","correct":0,"wrong":0,"unanswered":0,"score":0.0,"maxScore":4.0}]""",
        )
        assertEquals(1, nonNumeric.toExamHistoryDetail().typeStats.size)
        assertNull(nonNumeric.toExamHistoryDetail().maxScore)

        // 非有限值（NaN / Infinity）→ 条目丢弃
        val nonFinite = session(
            questionCount = 32,
            statsJson = """[$singleStatJson,""" +
                """{"type":"MULTI","total":2,"correct":1,"wrong":1,"unanswered":0,"score":"NaN","maxScore":"Infinity"}]""",
        )
        assertEquals(1, nonFinite.toExamHistoryDetail().typeStats.size)
        assertNull(nonFinite.toExamHistoryDetail().maxScore)

        // 无效计数：负值 → 条目丢弃
        val negative = session(
            questionCount = 32,
            statsJson = """[$singleStatJson,""" +
                """{"type":"MULTI","total":-1,"correct":-1,"wrong":0,"unanswered":0,"score":0.0,"maxScore":4.0}]""",
        )
        assertEquals(1, negative.toExamHistoryDetail().typeStats.size)
        assertNull(negative.toExamHistoryDetail().maxScore)

        // 无效计数：三项之和与 total 不一致 → 条目丢弃
        val mismatched = session(
            questionCount = 32,
            statsJson = """[$singleStatJson,""" +
                """{"type":"MULTI","total":2,"correct":2,"wrong":1,"unanswered":0,"score":4.0,"maxScore":4.0}]""",
        )
        assertEquals(1, mismatched.toExamHistoryDetail().typeStats.size)
        assertNull(mismatched.toExamHistoryDetail().maxScore)

        // 数字文本仍可解析为对应数值，不按缺失处理
        val stringNumbers = session(
            questionCount = 30,
            statsJson = """
                [{"type":"SINGLE","total":"30","correct":"25","wrong":"5","unanswered":"0","score":"50.0","maxScore":"60.0"}]
            """.trimIndent(),
        )
        assertEquals(30, stringNumbers.toExamHistoryDetail().typeStats.single().total)
        assertEquals(60.0, stringNumbers.toExamHistoryDetail().maxScore!!, 0.0)
    }

    @Test
    fun `题数损坏无法交叉验证时不给满分`() {
        val entity = session(questionCount = 32, statsJson = fullStatsJson)
        val corruptedIds = entity.copy(questionIdsJson = "")

        assertNull(corruptedIds.toExamHistoryDetail().totalQuestions)
        assertNull(corruptedIds.toExamHistoryDetail().maxScore)
        assertNull(corruptedIds.toExamHistoryEntry().maxScore)
        // 题数缺失时详情省略题数，已保存的统计行仍展示
        assertEquals(2, corruptedIds.toExamHistoryDetail().typeStats.size)
    }

    @Test
    fun `缺配置时及格线不可信、用时不受未知限时封顶`() {
        val start = 1_760_000_000_000
        val missingConfig = session(
            configJson = "",
            startedAt = start,
            endedAt = start + 61 * 60_000,
        )
        assertNull(missingConfig.toExamHistoryDetail().passScore)
        // 配置缺失 → 没有可信限时 → 不封顶，直接展示真实用时
        assertEquals(61 * 60L, missingConfig.toExamHistoryDetail().durationSeconds)

        val configWithoutPassScore = session(
            configJson = """{"durationMinutes":60}""",
            startedAt = start,
            endedAt = start + 61 * 60_000,
        )
        assertNull(configWithoutPassScore.toExamHistoryDetail().passScore)
        // 已保存的限时仍可信 → 封顶生效
        assertEquals(60 * 60L, configWithoutPassScore.toExamHistoryDetail().durationSeconds)
    }

    @Test
    fun `缺起止时间时不计算用时，交卷时间按缺失处理`() {
        val entity = session(startedAt = 1_760_000_000_000, score = 30.0)

        assertNull(entity.toExamHistoryDetail().durationSeconds)
        assertNull(entity.toExamHistoryDetail().submittedAt)
        assertNull(entity.toExamHistoryEntry().submittedAt)
        assertEquals(1_760_000_000_000L, entity.toExamHistoryDetail().startedAt!!)

        val zeroStart = session(startedAt = 0, endedAt = 1_760_060_000_000)
        assertNull(zeroStart.toExamHistoryDetail().durationSeconds)
        assertNull(zeroStart.toExamHistoryDetail().startedAt)
        assertEquals(1_760_060_000_000L, zeroStart.toExamHistoryDetail().submittedAt!!)

        val reversed = session(
            startedAt = 1_760_060_000_000,
            endedAt = 1_760_000_000_000,
        )
        assertNull(reversed.toExamHistoryDetail().durationSeconds)
    }

    @Test
    fun `题库删除后仍显示会话保存的题库名`() {
        val entity = session(
            bankId = null,
            bankName = "已被删除的题库",
            score = 10.0,
        )

        val entry = entity.toExamHistoryEntry()
        assertNull(entry.bankId)
        assertEquals("已被删除的题库", entry.bankName)
        assertEquals("已被删除的题库", entity.toExamHistoryDetail().bankName)
    }

    @Test
    fun `decodeTypeStats 与 encodeTypeStats 往返一致`() {
        val stats = listOf(
            TypeStats(QuestionType.MULTI, 10, 6, 3, 1, 9.5, 20.0),
            TypeStats(QuestionType.FILL, 4, 4, 0, 0, 8.0, 8.0),
        )
        assertEquals(stats, JsonCodec.decodeTypeStats(JsonCodec.encodeTypeStats(stats)))
        assertEquals(emptyList<TypeStats>(), JsonCodec.decodeTypeStats(null))
        assertEquals(emptyList<TypeStats>(), JsonCodec.decodeTypeStats(""))
        assertEquals(emptyList<TypeStats>(), JsonCodec.decodeTypeStats("[]"))
        assertEquals(emptyList<TypeStats>(), JsonCodec.decodeTypeStats("not-json"))
        // 合法的 0 计数条目保留，真实的 0 不被丢弃或改写
        val zeroStat = JsonCodec.decodeTypeStats(
            """[{"type":"SINGLE","total":0,"correct":0,"wrong":0,"unanswered":0,"score":0.0,"maxScore":0.0}]""",
        )
        assertEquals(1, zeroStat.size)
        assertEquals(0, zeroStat.single().total)
        assertEquals(0.0, zeroStat.single().maxScore, 0.0)
    }

    @Test
    fun `decodeExamConfigSnapshot 只给出真实保存的字段`() {
        val snapshot = JsonCodec.decodeExamConfigSnapshot(
            JsonCodec.encodeExamConfig(ExamConfig(durationMinutes = 45, passScore = 36.0)),
        )
        assertEquals(36.0, snapshot.passScore!!, 0.0)
        assertEquals(45, snapshot.durationMinutes)

        assertNull(JsonCodec.decodeExamConfigSnapshot(null).passScore)
        assertNull(JsonCodec.decodeExamConfigSnapshot("").durationMinutes)
        assertNull(JsonCodec.decodeExamConfigSnapshot("not-json").passScore)

        val partial = JsonCodec.decodeExamConfigSnapshot("""{"passScore":null,"durationMinutes":30}""")
        assertNull(partial.passScore)
        assertEquals(30, partial.durationMinutes)
    }
}
