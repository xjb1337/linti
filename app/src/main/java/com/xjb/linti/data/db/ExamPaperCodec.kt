package com.xjb.linti.data.db

import com.xjb.linti.domain.ExamPaperQuestion
import com.xjb.linti.domain.GradingResult
import com.xjb.linti.domain.QuestionOrigin
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion
import org.json.JSONArray
import org.json.JSONObject

/**
 * 已交卷模考原卷的编解码：按题存储、长内容分块（复用考试草稿的分块方案）。
 *
 * - encode 输入是交卷那一刻的题目列表与判分结果，选项字母与作答、
 *   正确答案、得分都是考场现场值，不映射回题库；
 * - decode 严格校验：版本、题数与头行一致、每个序号分块连续齐全，
 *   任一字段缺失/损坏即抛错，由上层呈现「试卷读取失败」，不猜测补齐。
 */
object ExamPaperCodec {
    const val FORMAT_VERSION = 1
    private const val CHUNK_SIZE = 16_000
    private const val KIND_METADATA = "metadata"
    private const val KIND_QUESTION = "question"

    fun encode(
        sessionId: Long,
        questions: List<QuizQuestion>,
        grading: GradingResult,
    ): Pair<ExamPaperEntity, List<ExamPaperChunkEntity>> {
        val metadata = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("questionCount", questions.size)
        val rows = mutableListOf<ExamPaperChunkEntity>()
        rows += chunks(sessionId, KIND_METADATA, 0, metadata.toString())
        questions.forEachIndexed { index, question ->
            val grade = requireNotNull(grading.outcomes[question.questionId]) {
                "原卷缺少第 ${index + 1} 题的判分结果"
            }
            val options = JSONArray()
            question.options.forEach { option ->
                options.put(JSONObject().put("label", option.label).put("text", option.text))
            }
            val json = JSONObject()
                .put("questionId", question.questionId)
                .put("sourceNumber", question.sourceNumber)
                .put("type", question.type.name)
                .put("origin", question.origin.name)
                .put("stem", question.stem)
                .put("options", options)
                .put("userAnswer", grade.userAnswer ?: JSONObject.NULL)
                .put("correctAnswer", grade.correctAnswer)
                .put("explanation", question.explanation.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                .put("points", question.points)
                .put("outcome", grade.outcome.name)
                .put("score", grade.score)
                .put("maxScore", grade.maxScore)
            rows += chunks(sessionId, KIND_QUESTION, index, json.toString())
        }
        return ExamPaperEntity(
            sessionId = sessionId,
            formatVersion = FORMAT_VERSION,
            questionCount = questions.size,
        ) to rows
    }

    /** 解码整卷逐题快照；头行题数、版本、分块范围与完整性不一致时抛错。 */
    fun decode(entity: ExamPaperEntity, rows: List<ExamPaperChunkEntity>): List<ExamPaperQuestion> {
        check(entity.formatVersion == FORMAT_VERSION) { "不支持的原卷格式" }
        check(entity.questionCount > 0) { "原卷题数无效" }
        checkRowsWithinExpectation(rows, entity.questionCount)
        val grouped = rows.groupBy { it.kind to it.ordinal }
        val metadata = JSONObject(assemble(grouped, KIND_METADATA, 0))
        check(metadata.getInt("version") == FORMAT_VERSION) { "不支持的原卷格式" }
        check(metadata.getInt("questionCount") == entity.questionCount) { "原卷题数不一致" }
        return List(entity.questionCount) { index ->
            decodeQuestion(index, JSONObject(assemble(grouped, KIND_QUESTION, index)))
        }
    }

    // 分块只允许预期的类型与题序（metadata 仅 ordinal 0，question 仅 0..count-1），
    // 且 (kind, ordinal, part) 不得重复；多余的块一律视为损坏
    private fun checkRowsWithinExpectation(rows: List<ExamPaperChunkEntity>, questionCount: Int) {
        rows.forEach { row ->
            when (row.kind) {
                KIND_METADATA -> check(row.ordinal == 0) { "原卷快照包含多余的元数据分块" }
                KIND_QUESTION -> check(row.ordinal in 0 until questionCount) { "原卷快照包含多余的题目分块" }
                else -> check(false) { "原卷快照包含未知的分块类型" }
            }
        }
        val keys = rows.map { Triple(it.kind, it.ordinal, it.part) }
        check(keys.size == keys.distinct().size) { "原卷快照分块编号重复" }
    }

    /**
     * 备份恢复前的完整性校验：每份快照必须能完整解码、归属已知且已交卷的
     * 模考场次、分块只含预期类型与题序且编号连续无重复。
     * 恢复旧格式备份（无快照）时为空、直接通过。
     */
    fun validateBackup(
        papers: List<ExamPaperEntity>,
        chunks: List<ExamPaperChunkEntity>,
        sessions: List<QuizSessionEntity>,
    ) {
        val bySession = chunks.groupBy { it.sessionId }
        check(bySession.keys == papers.map { it.sessionId }.toSet()) { "原卷快照归属不一致" }
        val sessionById = sessions.associateBy { it.id }
        papers.forEach { paper ->
            val session = sessionById[paper.sessionId]
            checkNotNull(session) { "原卷快照缺少所属会话" }
            check(session.mode == SessionBackfill.EXAM && session.status == SessionBackfill.COMPLETED) {
                "原卷快照只能属于已交卷模考"
            }
            val questions = decode(paper, bySession.getValue(paper.sessionId))
            questions.forEachIndexed { index, question ->
                check(question.index == index) { "原卷题序不一致" }
            }
        }
    }

    private fun decodeQuestion(index: Int, json: JSONObject): ExamPaperQuestion = ExamPaperQuestion(
        index = index,
        questionId = json.requiredLong("questionId"),
        sourceNumber = json.requiredString("sourceNumber"),
        type = questionType(json.requiredString("type")),
        origin = if (json.has("origin")) QuestionOrigin.valueOf(json.requiredString("origin")) else QuestionOrigin.IMPORTED,
        stem = json.requiredString("stem"),
        options = json.requiredArray("options").let { array ->
            List(array.length()) { optionIndex ->
                val option = array.getJSONObject(optionIndex)
                QuestionOption(
                    label = option.requiredString("label"),
                    text = option.requiredString("text"),
                )
            }
        },
        // 必须存在但允许显式 null：缺失与「未做/无解析」是两回事，缺失即损坏
        userAnswer = json.requiredNullableString("userAnswer"),
        correctAnswer = json.requiredString("correctAnswer"),
        explanation = json.requiredNullableString("explanation"),
        points = json.requiredFiniteDouble("points"),
        outcome = outcome(json.requiredString("outcome")),
        score = json.requiredFiniteDouble("score"),
        maxScore = json.requiredFiniteDouble("maxScore"),
    )

    private fun questionType(value: String): QuestionType =
        QuestionType.entries.firstOrNull { it.name == value } ?: throw IllegalStateException("原卷题型无效")

    private fun outcome(value: String): QuestionOutcome =
        QuestionOutcome.entries.firstOrNull { it.name == value } ?: throw IllegalStateException("原卷判分无效")

    private fun chunks(sessionId: Long, kind: String, ordinal: Int, text: String): List<ExamPaperChunkEntity> {
        val rows = mutableListOf<ExamPaperChunkEntity>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + CHUNK_SIZE, text.length)
            if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) {
                end--
            }
            rows += ExamPaperChunkEntity(sessionId, kind, ordinal, rows.size, text.substring(start, end))
            start = end
        }
        return rows
    }

    private fun assemble(
        rows: Map<Pair<String, Int>, List<ExamPaperChunkEntity>>,
        kind: String,
        ordinal: Int,
    ): String {
        val parts = rows[kind to ordinal].orEmpty().sortedBy { it.part }
        check(parts.isNotEmpty() && parts.indices.all { parts[it].part == it }) { "原卷快照不完整" }
        return parts.joinToString("") { it.content }
    }

    private fun JSONObject.requiredString(name: String): String {
        if (!has(name) || isNull(name)) throw IllegalStateException("原卷缺少字段 $name")
        return getString(name)
    }

    private fun JSONObject.requiredLong(name: String): Long {
        if (!has(name) || isNull(name)) throw IllegalStateException("原卷缺少字段 $name")
        return getLong(name)
    }

    private fun JSONObject.requiredArray(name: String): JSONArray {
        if (!has(name) || isNull(name)) throw IllegalStateException("原卷缺少字段 $name")
        return getJSONArray(name)
    }

    private fun JSONObject.requiredFiniteDouble(name: String): Double {
        if (!has(name) || isNull(name)) throw IllegalStateException("原卷缺少字段 $name")
        val value = when (val raw = opt(name)) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull()
            else -> null
        }
        return value?.takeIf { it.isFinite() } ?: throw IllegalStateException("原卷字段 $name 无效")
    }

    private fun JSONObject.requiredNullableString(name: String): String? {
        if (!has(name)) throw IllegalStateException("原卷缺少字段 $name")
        if (isNull(name)) return null
        return getString(name)
    }
}
