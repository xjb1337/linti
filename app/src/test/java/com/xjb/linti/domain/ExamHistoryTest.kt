package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamHistoryTest {

    private fun entry(
        sessionId: Long,
        submittedAt: Long?,
        accuracy: Double?,
        bankName: String = "题库",
    ): ExamHistoryEntry = ExamHistoryEntry(
        sessionId = sessionId,
        bankId = 1,
        bankName = bankName,
        submittedAt = submittedAt,
        score = null,
        maxScore = null,
        passed = null,
        accuracy = accuracy,
    )

    @Test
    fun `examAccuracy 与详情正确率同一规则`() {
        assertEquals(0.75, examAccuracy(6, 2, 0, 8)!!, 0.0)
        assertEquals(0.0, examAccuracy(0, 8, 0, 8)!!, 0.0)
        assertNull(examAccuracy(0, 0, 0, 8))
        assertNull(examAccuracy(6, 2, 0, null))
        assertNull(examAccuracy(6, 1, 0, 8))
    }

    @Test
    fun `超过 10 场时取最近 10 场并反转为从旧到新`() {
        // 模拟列表查询顺序：已按交卷时间倒序、同时间按会话 ID 倒序
        val entries = (12L downTo 1L).map { index ->
            entry(sessionId = index, submittedAt = index * 10_000L, accuracy = 0.5)
        }
        val points = entries.recentExamAccuracyPoints()

        assertEquals(10, points.size)
        // 最旧的两场被排除，其余按从旧到新排列
        assertEquals(3L, points.first().sessionId)
        assertEquals(12L, points.last().sessionId)
        assertEquals((3L..12L).toList(), points.map { it.sessionId })
    }

    @Test
    fun `同一交卷时间的场次按会话 ID 倒序参与取数`() {
        // 11 场，最旧的两场交卷时间相同：只保留会话 ID 较大（较新）的那一场
        val entries = (11L downTo 3L).map { index ->
            entry(sessionId = index, submittedAt = index * 10_000L, accuracy = 0.5)
        } + listOf(
            entry(sessionId = 2, submittedAt = 1_000L, accuracy = 0.2),
            entry(sessionId = 1, submittedAt = 1_000L, accuracy = 0.1),
        )
        val points = entries.recentExamAccuracyPoints()

        assertEquals(10, points.size)
        assertEquals(2L, points.first().sessionId)
        assertTrue(points.none { it.sessionId == 1L })
    }

    @Test
    fun `交卷时间无效的场次不参与图表`() {
        val entries = listOf(
            entry(sessionId = 1, submittedAt = null, accuracy = 0.9),
            entry(sessionId = 2, submittedAt = 5_000L, accuracy = 0.4),
            entry(sessionId = 3, submittedAt = 0L, accuracy = 0.6),
        )

        val points = entries.recentExamAccuracyPoints()
        assertEquals(listOf(2L), points.map { it.sessionId })
    }

    @Test
    fun `未知正确率占位断线，不用第 11 场顶替`() {
        // 最近 10 场中第 2 新的场（会话 10）计数不可信，更旧的第 11 场（会话 1）有效但不能顶替
        val entries = (11L downTo 1L).map { index ->
            entry(
                sessionId = index,
                submittedAt = index * 10_000L,
                accuracy = if (index == 10L) null else 0.5,
            )
        }
        val points = entries.recentExamAccuracyPoints()

        assertEquals(10, points.size)
        assertEquals((2L..11L).toList(), points.map { it.sessionId })
        assertNull(points.first { it.sessionId == 10L }.accuracy)
        assertTrue(points.none { it.sessionId == 1L })
        // 未知场次两侧的场仍然有效，折线在绘制层断开
        assertEquals(0.5, points.first { it.sessionId == 9L }.accuracy!!, 0.0)
        assertEquals(0.5, points.first { it.sessionId == 11L }.accuracy!!, 0.0)
    }

    @Test
    fun `60 到未知到 0 到 80 的序列反转后零值保留`() {
        // 交卷顺序从旧到新：60% → 未知 → 0% → 80%（列表顺序为倒序）
        val entries = listOf(
            entry(sessionId = 4, submittedAt = 40_000L, accuracy = 0.8),
            entry(sessionId = 3, submittedAt = 30_000L, accuracy = 0.0),
            entry(sessionId = 2, submittedAt = 20_000L, accuracy = null),
            entry(sessionId = 1, submittedAt = 10_000L, accuracy = 0.6),
        )
        val points = entries.recentExamAccuracyPoints()

        assertEquals(listOf(1L, 2L, 3L, 4L), points.map { it.sessionId })
        assertEquals(0.6, points[0].accuracy!!, 0.0)
        assertNull(points[1].accuracy)
        // 真实的 0% 是有效数据点，不与未知混同
        assertEquals(0.0, points[2].accuracy!!, 0.0)
        assertEquals(0.8, points[3].accuracy!!, 0.0)
    }

    @Test
    fun `零场、一场与全未知场的派生结果`() {
        assertTrue(emptyList<ExamHistoryEntry>().recentExamAccuracyPoints().isEmpty())

        val single = listOf(entry(sessionId = 1, submittedAt = 1_000L, accuracy = 0.7))
        val singlePoints = single.recentExamAccuracyPoints()
        assertEquals(1, singlePoints.size)
        assertEquals(0.7, singlePoints.single().accuracy!!, 0.0)

        val allUnknown = (3L downTo 1L).map { index ->
            entry(sessionId = index, submittedAt = index * 1_000L, accuracy = null)
        }
        val unknownPoints = allUnknown.recentExamAccuracyPoints()
        assertEquals(3, unknownPoints.size)
        assertTrue(unknownPoints.all { it.accuracy == null })
    }
}
