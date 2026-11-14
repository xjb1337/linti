package com.xjb.linti.data.import

import com.xjb.linti.domain.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQuestionParserTest {
    private val parser = LocalQuestionParser()

    @Test
    fun `parses standard single choice question`() {
        val result = parser.parse(resource("parser/standard_single.txt"))

        assertEquals(1, result.questions.size)
        val question = result.questions.single()
        assertEquals("1", question.sourceNumber)
        assertEquals(QuestionType.SINGLE, question.type)
        assertEquals(listOf("A", "B", "C", "D"), question.options.map { it.label })
        assertEquals("C", question.answer)
        assertTrue(question.explanation.contains("肌钙蛋白"))
        assertFalse(question.needsReview)
    }

    @Test
    fun `recognizes all supported question number variants`() {
        val text = """
            1、题干一是否完整？
            A. 选项甲
            B. 选项乙
            答案：A
            （2）题干二是否完整？
            A. 选项甲
            B. 选项乙
            答案：B
            第3题 题干三是否完整？
            A. 选项甲
            B. 选项乙
            答案：A
            4) 题干四是否完整？
            A. 选项甲
            B. 选项乙
            答案：B
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(listOf("1", "2", "3", "4"), result.questions.map { it.sourceNumber })
        assertTrue(result.questions.all { it.stem.startsWith("题干") })
    }

    @Test
    fun `normalizes multi select answer order`() {
        val text = """
            1. [多选题] 下列哪些属于虚构示例中的正确项？
            A. 甲项
            B. 乙项
            C. 丙项
            D. 丁项
            答案：B、A
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(QuestionType.MULTI, result.questions.single().type)
        assertEquals("AB", result.questions.single().answer)
        assertFalse(result.questions.single().needsReview)
    }

    @Test
    fun `normalizes judge answers`() {
        val text = """
            1. [判断题] 标准大气压下纯水在 100 摄氏度沸腾。
            答案：正确
            2. [判断题] 该虚构说法被标记为错误。
            答案：×
            3. [判断] 该虚构陈述为真。
            答案：T
            4. [判断] 该虚构陈述为假。
            答案：false
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(4, result.questions.size)
        assertEquals(listOf("true", "false", "true", "false"), result.questions.map { it.answer })
        assertTrue(result.questions.all { it.type == QuestionType.JUDGE })
    }

    @Test fun parsesEssayAndReferenceAnswer() {
        val result = parser.parse("1. [问答题] 虚构植物需要什么条件？\n参考答案：充足光照与水分。")
        assertEquals(QuestionType.ESSAY, result.questions.single().type)
        assertEquals("充足光照与水分。", result.questions.single().answer)
        assertFalse(result.questions.single().needsReview)
    }

    @Test
    fun `parses fill in the blank question`() {
        val text = """
            1. [填空题] 血液中运输氧气的主要蛋白是____。
            答案： 血红蛋白
        """.trimIndent()

        val result = parser.parse(text)
        val question = result.questions.single()

        assertEquals(QuestionType.FILL, question.type)
        assertEquals("血红蛋白", question.answer)
        assertFalse(question.needsReview)
    }

    @Test
    fun `parses option separator variants and continuation lines`() {
        val text = """
            1. 单选题 选项格式与跨行合并测试题干。
            A、选项一
            B) 选项二
            C）选项三
            D：选项四
            E. 选项五包含跨行
            继续内容
            答案：E
        """.trimIndent()

        val result = parser.parse(text)
        val question = result.questions.single()

        assertEquals(listOf("A", "B", "C", "D", "E"), question.options.map { it.label })
        assertTrue(question.options.last().text.contains("继续内容"))
        assertEquals("E", question.answer)
    }

    @Test
    fun `marks both neighboring questions when numbering jumps`() {
        val text = """
            14. 第一道虚构题干是否完整？
            A. 选项甲
            B. 选项乙
            答案：A
            16. 第二道虚构题干是否完整？
            A. 选项甲
            B. 选项乙
            答案：B
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(2, result.questions.size)
        assertTrue(result.questions.all { it.needsReview })
        assertTrue(result.questions.all { question ->
            question.reviewReasons.any { it.contains("14") && it.contains("16") }
        })
        assertFalse(result.questions.any { it.sourceNumber == "15" })
    }

    @Test
    fun `marks question without answer for review`() {
        val text = """
            1. 缺少答案的虚构题干是否完整？
            A. 选项甲
            B. 选项乙
        """.trimIndent()

        val question = parser.parse(text).questions.single()

        assertTrue(question.needsReview)
        assertTrue(question.reviewReasons.any { it.contains("未找到答案") })
    }

    @Test
    fun `marks single choice with too few options for review`() {
        val text = """
            1. [单选题] 只有一个选项的虚构题干是否完整？
            A. 唯一选项
            答案：A
        """.trimIndent()

        val question = parser.parse(text).questions.single()

        assertTrue(question.needsReview)
        assertTrue(question.reviewReasons.any { it.contains("选项不足") })
    }

    @Test
    fun `fills answers from end answer list by question number`() {
        val text = """
            1. 第一道虚构单选题题干是否完整？
            A. 甲
            B. 乙
            C. 丙
            2. 第二道虚构多选题题干是否完整？
            A. 甲
            B. 乙
            C. 丙
            3. [判断题] 第三道虚构判断题题干完整。
            参考答案：1.C 2.AB 3.正确
        """.trimIndent()

        val result = parser.parse(text)

        assertEquals(3, result.questions.size)
        assertEquals("C", result.questions[0].answer)
        assertEquals("AB", result.questions[1].answer)
        assertEquals("true", result.questions[2].answer)
        assertTrue(result.questions.none { it.reviewReasons.any { reason -> reason.contains("未找到答案") } })
    }

    @Test
    fun `splits a continuous mixed text with at least five questions`() {
        val result = parser.parse(resource("parser/mixed_questions.txt"))

        assertEquals(5, result.questions.size)
        assertEquals(
            listOf(
                QuestionType.SINGLE,
                QuestionType.MULTI,
                QuestionType.JUDGE,
                QuestionType.FILL,
                QuestionType.SINGLE,
            ),
            result.questions.map { it.type },
        )
        assertEquals(listOf("1", "2", "3", "4", "5"), result.questions.map { it.sourceNumber })
        assertTrue(result.questions.all { it.stem.isNotBlank() })
    }

    @Test
    fun `separates answer and explanation mixed on one line`() {
        val text = """
            1. 答案与解析混排的虚构题干是否完整？
            A. 甲
            B. 乙
            答案：B。解析：这是与答案同一行的解析内容。
        """.trimIndent()

        val question = parser.parse(text).questions.single()

        assertEquals("B", question.answer)
        assertEquals("这是与答案同一行的解析内容。", question.explanation)
        assertFalse(question.needsReview)
    }

    @Test
    fun `supports options through H`() {
        val text = """
            1. [多选题] 选项可扩展到 H 的虚构题干是否完整？
            A. 甲
            B. 乙
            C. 丙
            D. 丁
            E. 戊
            F. 己
            G. 庚
            H. 辛
            答案：H、A
        """.trimIndent()

        val question = parser.parse(text).questions.single()

        assertEquals(8, question.options.size)
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H"), question.options.map { it.label })
        assertEquals("AH", question.answer)
    }

    @Test
    fun `empty and header only inputs return zero questions without crashing`() {
        val empty = parser.parse("   \n\n ")
        val headerOnly = parser.parse("内部练习题\n仅供内部使用")

        assertEquals(0, empty.questions.size)
        assertTrue(empty.warnings.isNotEmpty())
        assertEquals(0, headerOnly.questions.size)
        assertTrue(headerOnly.warnings.isNotEmpty())
    }

    @Test
    fun `marks answer letter outside options for review`() {
        val text = """
            1. 答案字母越界的虚构题干是否完整？
            A. 甲
            B. 乙
            答案：D
        """.trimIndent()

        val question = parser.parse(text).questions.single()

        assertTrue(question.needsReview)
        assertTrue(question.reviewReasons.any { it.contains("不在选项中") })
    }

    @Test
    fun `handles inline options and answer on one line`() {
        val text = "1. 一行内的虚构题干是否完整？ A. 甲 B. 乙 C. 丙 答案：B 解析：同一行解析。"

        val question = parser.parse(text).questions.single()

        assertEquals(3, question.options.size)
        assertEquals("B", question.answer)
        assertEquals("同一行解析。", question.explanation)
    }

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing test resource $path"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
