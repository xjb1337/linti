package com.xjb.linti.domain

/**
 * 第 5 步正确率规则的唯一实现：仅当答题计数可信
 * （三项之和与会话保存的题数一致且分母 > 0）时计算「答对 ÷ 总题数」；
 * 无法确认时为 null，展示层显示「—」，真实的 0 照常保留。
 */
fun examAccuracy(
    correctCount: Int,
    wrongCount: Int,
    unansweredCount: Int,
    totalQuestions: Int?,
): Double? {
    val denominator = correctCount + wrongCount + unansweredCount
    if (totalQuestions == null || denominator <= 0 || denominator != totalQuestions) return null
    return correctCount.toDouble() / denominator
}

/**
 * 模考历史列表行。仅包含交卷时已保存、可直接展示的字段；
 * 缺失的字段为 null，展示层以「—」或省略处理。
 */
data class ExamHistoryEntry(
    val sessionId: Long,
    val bankId: Long?,
    val bankName: String,
    val submittedAt: Long?,
    val score: Double?,
    val maxScore: Double?,
    val passed: Boolean?,
    val accuracy: Double?,
)

/**
 * 模考历史详情。全部数据取自会话保存的交卷时快照，不读取现有题目重新判分：
 * - 满分仅在分题型统计完整（条目数值有效、各题型题数之和与会话保存的题数一致）时求和给出；
 * - 用时仅在起止时间都有效时计算，且只在保存了有效限时（> 0 分钟）时封顶；
 * - 正确率按 [examAccuracy] 的规则计算，见 [accuracy]；
 * - 及格线、起止时间、题数缺失时为 null，由展示层以「—」或省略处理。
 */
data class ExamHistoryDetail(
    val sessionId: Long,
    val bankId: Long?,
    val bankName: String,
    val startedAt: Long?,
    val submittedAt: Long?,
    val durationSeconds: Long?,
    val score: Double?,
    val maxScore: Double?,
    val passed: Boolean?,
    val passScore: Double?,
    val totalQuestions: Int?,
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    val typeStats: List<TypeStats>,
) {
    val accuracy: Double?
        get() = examAccuracy(correctCount, wrongCount, unansweredCount, totalQuestions)
}

/**
 * 「最近 10 次模考正确率」图的一个数据点。accuracy 为 null 表示该场
 * 计数不可信：保留横轴位置、折线在此断开，不得补 0 或被更旧的场次顶替。
 */
data class ExamAccuracyPoint(
    val sessionId: Long,
    val bankName: String,
    val submittedAt: Long,
    val accuracy: Double?,
)

/**
 * 从历史列表数据派生最近 [limit] 场的正确率点：
 * 只取交卷时间有效的模考，按交卷时间倒序（同时间按会话 ID 倒序）取最近 [limit] 场，
 * 先确定场次再判断正确率，绘图方向反转为从旧到新。列表顺序不受影响。
 */
fun List<ExamHistoryEntry>.recentExamAccuracyPoints(limit: Int = 10): List<ExamAccuracyPoint> =
    asSequence()
        .filter { (it.submittedAt ?: 0L) > 0 }
        .take(limit)
        .map { ExamAccuracyPoint(it.sessionId, it.bankName, it.submittedAt ?: 0L, it.accuracy) }
        .toList()
        .reversed()
