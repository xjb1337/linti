package com.xjb.linti.data.aigeneration

import com.xjb.linti.domain.QuestionOption
import org.junit.Assert.*
import org.junit.Test

class AiDraftValidatorTest {
    private val options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙"), QuestionOption("C", "丙"))
    private val valid = AiDraftFields("SINGLE", "虚构题干", options, "A", "虚构解析")
    private fun invalid(fields: AiDraftFields, problem: String) = assertTrue(AiDraftValidator.validate(fields).toString(), AiDraftValidator.validate(fields).any { it.contains(problem) })

    @Test fun validatesEachFieldAndNeverFillsMissingAnswer() {
        assertTrue(AiDraftValidator.validate(valid).isEmpty())
        invalid(valid.copy(type = "FILL"), "题型")
        invalid(valid.copy(type = "UNKNOWN"), "题型")
        invalid(valid.copy(stem = "  "), "题干")
        invalid(valid.copy(explanation = ""), "解析")
        invalid(valid.copy(options = emptyList()), "数量")
        invalid(valid.copy(options = List(9) { QuestionOption("A", "甲") }), "数量")
        invalid(valid.copy(options = listOf(QuestionOption("A", "甲"), QuestionOption("a", "乙"))), "重复")
        invalid(valid.copy(options = listOf(QuestionOption("", "甲"), QuestionOption("B", "乙"))), "标签")
        invalid(valid.copy(options = listOf(QuestionOption("Z", "甲"), QuestionOption("B", "乙"))), "标签")
        invalid(valid.copy(options = listOf(QuestionOption("A", ""), QuestionOption("B", "乙"))), "内容")
        invalid(valid.copy(invalidOptions = true), "格式")
        invalid(valid.copy(expectedType = "MULTI"), "不一致")
        val missing = AiDraftCodec.parseResponse("""{"questions":[{"type":"SINGLE","stem":"虚构题干","options":[{"label":"A","text":"甲"},{"label":"B","text":"乙"}],"explanation":"虚构解析"}]}""", 1).single()
        assertEquals("", missing.answer)
        invalid(missing, "待补答案")
        assertEquals(missing, AiDraftCodec.decode(AiDraftCodec.encode(missing)))
    }

    @Test fun validatesSingleMultiAndJudgeWithoutLooseAnswerGuessing() {
        invalid(valid.copy(answer = "AB"), "单选")
        invalid(valid.copy(answer = "D"), "不在选项")
        invalid(valid.copy(answer = "答案 A"), "格式")
        invalid(valid.copy(answer = "AA"), "重复")
        val multi = valid.copy(type = "MULTI", answer = "A,B")
        assertTrue(AiDraftValidator.validate(multi).isEmpty())
        invalid(multi.copy(answer = "A"), "至少")
        invalid(multi.copy(answer = "AD"), "不在选项")
        val judge = valid.copy(type = "JUDGE", options = emptyList(), answer = "true")
        assertTrue(AiDraftValidator.validate(judge).isEmpty())
        assertTrue(AiDraftValidator.validate(judge.copy(answer = "错误")).isEmpty())
        invalid(judge.copy(answer = "正确但不完整"), "答案无效")
        invalid(judge.copy(options = options), "不能有选项")
        invalid(judge.copy(answer = ""), "待补答案")
    }

    @Test fun malformedEnvelopeRejectedButBadItemRemainsEditable() {
        for (body in listOf("不是 JSON", "[]", "{}", "{\"questions\":null}", "{\"questions\":[]}", "{\"questions\":[{}]} trailing", "{questions:[{}]}", "{'questions':[{}]}", "{\"questions\":[{},]}", "{\"questions\":[{}],\"questions\":[]}")) {
            assertTrue(body, runCatching { AiDraftCodec.parseResponse(body, 1) }.isFailure)
        }
        for (body in listOf("{\"questions\":[null]}", "{\"questions\":[{}]}", "{\"questions\":[{\"options\":42}]}")) {
            val draft = AiDraftCodec.parseResponse(body, 1).single()
            assertTrue(AiDraftValidator.validate(draft).isNotEmpty())
            assertEquals("", draft.answer)
        }
    }
}
