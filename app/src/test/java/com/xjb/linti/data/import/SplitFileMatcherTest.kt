package com.xjb.linti.data.import

import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitFileMatcherTest {
    // 模拟解析器输出：无答案的题带「未找到答案」待核对原因
    private fun choice(
        number: String,
        stem: String = "分文件匹配用虚构题干是否完整？",
        options: List<QuestionOption> = listOf(
            QuestionOption("A", "甲"),
            QuestionOption("B", "乙"),
            QuestionOption("C", "丙"),
            QuestionOption("D", "丁"),
        ),
        answer: String = "",
        explicitType: QuestionType? = null,
    ) = DraftQuestion(
        sourceNumber = number,
        type = explicitType ?: QuestionType.SINGLE,
        explicitType = explicitType,
        stem = stem,
        options = options,
        answer = answer,
        needsReview = answer.isBlank(),
        reviewReasons = if (answer.isBlank()) listOf("未找到答案，请手动确认") else emptyList(),
    )

    private fun entry(number: Int, answer: String) =
        AnswerEntry(number = number, numberText = number.toString(), rawAnswer = answer)

    // ---- 按题号匹配 ----

    @Test
    fun `matches shuffled and gapped numbers without reordering questions`() {
        val questions = listOf(
            choice("1"),
            choice("2"),
            choice("4"),
        )
        val answers = listOf(entry(4, "D"), entry(1, "A"), entry(2, "B"))

        val output = SplitFileMatcher.match(questions, answers)

        assertTrue(output.problems.isEmpty())
        assertEquals(listOf("A", "B", "D"), output.drafts.map { it.answer })
        assertTrue(output.warnings.isEmpty())
    }

    @Test
    fun `full width and leading zero numbers match ascii numbers`() {
        val questions = listOf(choice("０１"), choice("02"))
        val answers = listOf(entry(1, "A"), entry(2, "B"))

        val output = SplitFileMatcher.match(questions, answers)

        assertTrue(output.problems.isEmpty())
        assertEquals(listOf("A", "B"), output.drafts.map { it.answer })
    }

    @Test
    fun `missing external answer keeps missing answer reason without guessing`() {
        val questions = listOf(choice("1"), choice("2"))
        val answers = listOf(entry(2, "B"))

        val output = SplitFileMatcher.match(questions, answers)

        assertTrue(output.problems.isEmpty())
        assertEquals("", output.drafts[0].answer)
        assertTrue(output.drafts[0].needsReview)
        assertTrue(output.drafts[0].reviewReasons.any { it.contains("未找到答案") })
        assertEquals("B", output.drafts[1].answer)
        assertFalse(output.drafts[1].needsReview)
    }

    // ---- 编号重复 ----

    @Test
    fun `duplicate question numbers never auto match and flag affected drafts`() {
        val questions = listOf(choice("01"), choice("1"), choice("2"))
        val answers = listOf(entry(1, "A"), entry(2, "B"))

        val output = SplitFileMatcher.match(questions, answers)

        assertTrue(output.problems[0] is SplitDraftProblem.DuplicateQuestionNumber)
        assertTrue(output.problems[1] is SplitDraftProblem.DuplicateQuestionNumber)
        assertNull(output.problems[2])
        assertEquals("", output.drafts[0].answer)
        assertEquals("B", output.drafts[2].answer)
        assertTrue(output.warnings.any { it.contains("试题文件题号 1 重复") })
    }

    @Test
    fun `duplicate answer numbers including identical values never auto match`() {
        val questions = listOf(choice("1"), choice("2"))
        val answers = listOf(entry(1, "A"), entry(1, "A"), entry(2, "B"))

        val output = SplitFileMatcher.match(questions, answers)

        val problem = output.problems[0]
        assertTrue(problem is SplitDraftProblem.DuplicateAnswerNumber)
        assertEquals(listOf("A", "A"), (problem as SplitDraftProblem.DuplicateAnswerNumber).values)
        assertEquals("", output.drafts[0].answer)
        assertTrue(output.drafts[0].needsReview)
        assertEquals("B", output.drafts[1].answer)
        assertTrue(output.warnings.any { it.contains("答案文件题号 1 重复") })
    }

    @Test
    fun `blank answer row with valid number counts into duplication`() {
        val questions = listOf(choice("1"), choice("2"))
        val answers = listOf(entry(1, "A"), AnswerEntry(1, "1", ""), entry(2, "B"))

        val output = SplitFileMatcher.match(questions, answers)

        val problem = output.problems[0]
        assertTrue(problem is SplitDraftProblem.DuplicateAnswerNumber)
        assertEquals(listOf("A", "空"), (problem as SplitDraftProblem.DuplicateAnswerNumber).values)
        assertEquals("", output.drafts[0].answer)
        assertEquals("B", output.drafts[1].answer)
    }

    @Test
    fun `blank answer only for a number leaves the question unanswered`() {
        val questions = listOf(choice("1"))
        val answers = listOf(AnswerEntry(1, "1", ""))

        val output = SplitFileMatcher.match(questions, answers)

        assertTrue(output.problems.isEmpty())
        assertEquals("", output.drafts[0].answer)
        assertTrue(output.drafts[0].needsReview)
    }

    // ---- 孤立答案 ----

    @Test
    fun `answer only numbers are reported and never become questions`() {
        val questions = listOf(choice("1"))
        val answers = listOf(entry(1, "A"), entry(7, "C"), entry(9, "D"))

        val output = SplitFileMatcher.match(questions, answers)

        assertEquals(1, output.drafts.size)
        assertEquals("A", output.drafts[0].answer)
        assertTrue(output.warnings.any { it.contains("题号 7、9") && it.contains("没有对应题目") })
    }

    // ---- 行内答案 ----

    @Test
    fun `inline answer equal to external value stays untouched`() {
        val questions = listOf(choice("1", answer = "A", explicitType = QuestionType.SINGLE))
        val answers = listOf(entry(1, "A"))

        val output = SplitFileMatcher.match(questions, answers)

        assertTrue(output.problems.isEmpty())
        assertEquals("A", output.drafts[0].answer)
    }

    @Test
    fun `conflicting external answer keeps inline answer and exposes both values`() {
        val questions = listOf(choice("1", answer = "A", explicitType = QuestionType.SINGLE))
        val answers = listOf(entry(1, "C"))

        val output = SplitFileMatcher.match(questions, answers)

        val problem = output.problems[0]
        assertTrue(problem is SplitDraftProblem.Conflict)
        assertEquals("A", (problem as SplitDraftProblem.Conflict).inlineAnswer)
        assertEquals("C", problem.externalAnswer)
        assertEquals("A", output.drafts[0].answer)
    }

    @Test
    fun `garbage external text is not treated as equal letters via loose normalization`() {
        val questions = listOf(choice("1", answer = "B", explicitType = QuestionType.SINGLE))
        val answers = listOf(entry(1, "本题选B"))

        val output = SplitFileMatcher.match(questions, answers)

        assertTrue(output.problems[0] is SplitDraftProblem.Conflict)
        assertEquals("B", output.drafts[0].answer)
    }

    // ---- 严格校验与推断 ----

    @Test
    fun `explicit single rejects multi letters and out of option letters`() {
        val single = listOf(choice("1", explicitType = QuestionType.SINGLE))
        val multiLetters = SplitFileMatcher.match(single, listOf(entry(1, "AC")))
        val outOfOptions = SplitFileMatcher.match(single, listOf(entry(1, "E")))

        assertTrue(multiLetters.problems[0] is SplitDraftProblem.InvalidExternal)
        assertEquals("", multiLetters.drafts[0].answer)
        assertTrue(outOfOptions.problems[0] is SplitDraftProblem.InvalidExternal)
        assertEquals("", outOfOptions.drafts[0].answer)
    }

    @Test
    fun `explicit multi rejects single letter downgrade`() {
        val questions = listOf(
            choice("1", explicitType = QuestionType.MULTI),
        )

        val output = SplitFileMatcher.match(questions, listOf(entry(1, "A")))

        assertTrue(output.problems[0] is SplitDraftProblem.InvalidExternal)
        assertTrue(
            (output.problems[0] as SplitDraftProblem.InvalidExternal).reason.contains("两个"),
        )
        assertEquals("", output.drafts[0].answer)
    }

    @Test
    fun `explicit judge only accepts existing aliases and fill keeps chinese`() {
        val judge = listOf(choice("1", explicitType = QuestionType.JUDGE, options = emptyList()))
        val judgeOk = SplitFileMatcher.match(judge, listOf(entry(1, "√")))
        val judgeBad = SplitFileMatcher.match(judge, listOf(entry(1, "A")))

        assertEquals("true", judgeOk.drafts[0].answer)
        assertFalse(judgeOk.drafts[0].needsReview)
        assertTrue(judgeBad.problems[0] is SplitDraftProblem.InvalidExternal)

        val fill = listOf(
            DraftQuestion(
                sourceNumber = "1",
                type = QuestionType.FILL,
                explicitType = QuestionType.FILL,
                stem = "中国第一个朝代是____。",
                answer = "",
            ),
        )
        val fillResult = SplitFileMatcher.match(fill, listOf(entry(1, "夏朝")))
        assertEquals("夏朝", fillResult.drafts[0].answer)
        assertFalse(fillResult.drafts[0].needsReview)
    }

    @Test
    fun `draft validation rejects judge answers carrying explanatory text`() {
        val judge = DraftQuestion(
            sourceNumber = "1",
            type = QuestionType.JUDGE,
            explicitType = QuestionType.JUDGE,
            stem = "判断题严格校验的虚构说法是否正确。",
            answer = "正确但原题另有条件",
        )
        val clean = judge.copy(answer = "对")

        assertTrue(DraftValidator.validate(judge).reasons.any { it.contains("无法归一为正确或错误") })
        assertTrue(DraftValidator.validate(clean).reasons.isEmpty())
    }

    @Test
    fun `implicit type inferred from structure and external answer`() {
        val questions = listOf(choice("1"))

        val multi = SplitFileMatcher.match(questions, listOf(entry(1, "AC")))
        assertEquals(QuestionType.MULTI, multi.drafts[0].type)
        assertEquals("AC", multi.drafts[0].answer)
        assertTrue(multi.problems.isEmpty())

        val single = SplitFileMatcher.match(questions, listOf(entry(1, "B")))
        assertEquals(QuestionType.SINGLE, single.drafts[0].type)
        assertEquals("B", single.drafts[0].answer)
    }

    @Test
    fun `tf answer with options is not forced to judge`() {
        val questions = listOf(choice("1"))

        val output = SplitFileMatcher.match(questions, listOf(entry(1, "T")))

        assertTrue(output.problems[0] is SplitDraftProblem.InvalidExternal)
        assertEquals("", output.drafts[0].answer)
        assertTrue(output.drafts[0].needsReview)
    }

    @Test
    fun `judge alias without options infers judge question`() {
        val questions = listOf(
            DraftQuestion(
                sourceNumber = "1",
                type = QuestionType.SINGLE,
                stem = "分文件判断题虚构说法是否正确？",
                answer = "",
            ),
        )

        val output = SplitFileMatcher.match(questions, listOf(entry(1, "正确")))

        assertEquals(QuestionType.JUDGE, output.drafts[0].type)
        assertEquals("true", output.drafts[0].answer)
        assertTrue(output.problems.isEmpty())
        assertFalse(output.drafts[0].needsReview)
    }

    @Test
    fun `unmarked optionless questions accept F X and cross as judge answers`() {
        val questions = listOf(
            DraftQuestion(
                sourceNumber = "1",
                type = QuestionType.SINGLE,
                stem = "无选项无标记的虚构说法一是否正确？",
                answer = "",
            ),
            DraftQuestion(
                sourceNumber = "2",
                type = QuestionType.SINGLE,
                stem = "无选项无标记的虚构说法二是否正确？",
                answer = "",
            ),
            DraftQuestion(
                sourceNumber = "3",
                type = QuestionType.SINGLE,
                stem = "无选项无标记的虚构说法三是否正确？",
                answer = "",
            ),
        )

        val output = SplitFileMatcher.match(
            questions,
            listOf(entry(1, "F"), entry(2, "X"), entry(3, "×")),
        )

        assertTrue(output.problems.isEmpty())
        assertEquals(QuestionType.JUDGE, output.drafts[0].type)
        assertEquals("false", output.drafts[0].answer)
        assertEquals("false", output.drafts[1].answer)
        assertEquals("false", output.drafts[2].answer)
        assertTrue(output.drafts.all { !it.needsReview })
    }

    @Test
    fun `letter F is validated against actual options when options exist`() {
        // 有 A–F 选项：F 是真实选项，按单选验证通过
        val withF = listOf(
            choice(
                "1",
                options = listOf(
                    QuestionOption("A", "甲"),
                    QuestionOption("B", "乙"),
                    QuestionOption("C", "丙"),
                    QuestionOption("D", "丁"),
                    QuestionOption("E", "戊"),
                    QuestionOption("F", "己"),
                ),
            ),
        )
        val matched = SplitFileMatcher.match(withF, listOf(entry(1, "F")))
        assertTrue(matched.problems.isEmpty())
        assertEquals(QuestionType.SINGLE, matched.drafts[0].type)
        assertEquals("F", matched.drafts[0].answer)

        // 只有 A–D 选项：F 不在选项中，拒绝且不强转判断
        val withoutF = listOf(choice("1"))
        val rejected = SplitFileMatcher.match(withoutF, listOf(entry(1, "F")))
        assertTrue(rejected.problems[0] is SplitDraftProblem.InvalidExternal)
        assertEquals("", rejected.drafts[0].answer)
    }

    @Test
    fun `statement stems without type or options judge errors from F cross and X`() {
        // 预审给定场景：未标题型的判断题「5. 这是一个测试陈述。」
        val parsed = LocalQuestionParser().parse(
            """
            5. 这是一个测试陈述一。
            6. 这是一个测试陈述二。
            7. 这是一个测试陈述三。
            """.trimIndent(),
            SplitParseOptions(sequentialAnswerFallback = false),
        )
        assertEquals(3, parsed.questions.size)

        val output = SplitFileMatcher.match(
            parsed.questions,
            listOf(entry(5, "F"), entry(6, "×"), entry(7, "X")),
        )

        assertTrue(output.problems.isEmpty())
        assertTrue(
            output.drafts.all { draft ->
                draft.type == QuestionType.JUDGE && draft.answer == "false" && !draft.needsReview
            },
        )
    }

    @Test
    fun `fill blank stem with english text stays fill and keeps text`() {
        val questions = listOf(
            DraftQuestion(
                sourceNumber = "1",
                type = QuestionType.SINGLE,
                stem = "The capital of the fictional test country is ____.",
                answer = "",
            ),
        )

        val output = SplitFileMatcher.match(questions, listOf(entry(1, "CAD")))

        assertEquals(QuestionType.FILL, output.drafts[0].type)
        assertEquals("CAD", output.drafts[0].answer)
        assertTrue(output.problems.isEmpty())
    }

    @Test
    fun `text with embedded letters never becomes option letters`() {
        val questions = listOf(choice("1"))
        val answers = listOf(entry(1, "本题选B"))

        val output = SplitFileMatcher.match(questions, answers)

        assertTrue(output.problems[0] is SplitDraftProblem.InvalidExternal)
        assertEquals("", output.drafts[0].answer)
    }

    @Test
    fun `applying valid external answer clears missing answer reason but keeps other issues`() {
        val questions = listOf(
            choice("1").copy(reviewReasons = listOf("未找到答案，请手动确认", "疑似包含页眉、页脚或试卷标题，请核对题目边界")),
        )

        val output = SplitFileMatcher.match(questions, listOf(entry(1, "A")))

        assertEquals("A", output.drafts[0].answer)
        assertFalse(output.drafts[0].reviewReasons.any { it.contains("未找到答案") })
        assertTrue(output.drafts[0].reviewReasons.any { it.contains("页眉") })
        assertTrue(output.drafts[0].needsReview)
    }

    @Test
    fun `strict letters only accepts pure option letter strings`() {
        assertNull(SplitFileMatcher.strictLetters("本题选B"))
        assertNull(SplitFileMatcher.strictLetters("B1"))
        assertNull(SplitFileMatcher.strictLetters("1.5"))
        assertNull(SplitFileMatcher.strictLetters(""))
        assertEquals("AC", SplitFileMatcher.strictLetters("Ａ、c"))
        assertEquals("AC", SplitFileMatcher.strictLetters("A C"))
        assertEquals("BD", SplitFileMatcher.strictLetters("db"))
    }

    @Test
    fun `strict judge only accepts explicit aliases`() {
        assertEquals("false", SplitFileMatcher.strictJudge("错误"))
        assertEquals("true", SplitFileMatcher.strictJudge("是。"))
        assertEquals("false", SplitFileMatcher.strictJudge("X"))
        assertNull(SplitFileMatcher.strictJudge("本题正确"))
        assertNull(SplitFileMatcher.strictJudge("A"))
        assertNull(SplitFileMatcher.strictJudge("可能正确"))
    }

    @Test
    fun `draft validation rejects text with embedded letters for choice questions`() {
        val multi = DraftQuestion(
            sourceNumber = "1",
            type = QuestionType.MULTI,
            explicitType = QuestionType.MULTI,
            stem = "严格校验人工改答的虚构题干是否完整？",
            options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")),
            answer = "本题选B",
        )
        val single = multi.copy(type = QuestionType.SINGLE, explicitType = QuestionType.SINGLE)
        val clean = multi.copy(answer = "a、B")

        assertTrue(DraftValidator.validate(multi).reasons.any { it.contains("无法识别为选项字母") })
        assertTrue(DraftValidator.validate(single).reasons.any { it.contains("无法识别为选项字母") })
        assertTrue(DraftValidator.validate(clean).reasons.isEmpty())
        // 分隔符写法按严格口径归一后可用
        val applied = SplitFileMatcher.applyExternalAnswer(clean.copy(answer = ""), "a、B")
        assertNotNull(applied)
        assertEquals("AB", applied?.answer)
    }

    // ---- 解析器分文件模式 ----

    @Test
    fun `parser split mode never fills end answers by row order`() {
        val text = """
            1. 顺序兜底必须关闭的虚构题干是否完整？
            A. 甲
            B. 乙
            2. 第二道顺序兜底虚构题干是否完整？
            A. 甲
            B. 乙
            参考答案
            9.A
            10.B
        """.trimIndent()

        val split = LocalQuestionParser().parse(
            text,
            SplitParseOptions(sequentialAnswerFallback = false),
        )
        val normal = LocalQuestionParser().parse(text)

        assertTrue(split.questions.all { it.answer.isBlank() })
        assertTrue(split.warnings.none { it.contains("按题目顺序回填") })
        assertTrue(normal.questions.all { it.answer.isNotBlank() })
        assertTrue(normal.warnings.any { it.contains("按题目顺序回填") })
    }

    @Test
    fun `parser split mode keeps uniquely numbered end answers and inline answers`() {
        val text = """
            1. 保留行内答案的虚构题干是否完整？
            A. 甲
            B. 乙
            答案：A
            2. 文末答案按题号回填的虚构题干是否完整？
            A. 甲
            B. 乙
            3. 没有任何答案来源的虚构题干是否完整？
            A. 甲
            B. 乙
            参考答案
            2.B
            5.C
        """.trimIndent()

        val split = LocalQuestionParser().parse(
            text,
            SplitParseOptions(sequentialAnswerFallback = false),
        )

        assertEquals("A", split.questions[0].answer)
        assertEquals("B", split.questions[1].answer)
        assertEquals("", split.questions[2].answer)
        assertTrue(split.warnings.any { it.contains("题号 5") && it.contains("未找到对应题目") })
    }

    @Test
    fun `parser split mode recognizes full width question numbers and keeps display form`() {
        val text = "０１．全角题号的虚构题干是否完整？\nA. 甲\nB. 乙\n"

        val split = LocalQuestionParser().parse(
            text,
            SplitParseOptions(fullWidthNumbers = true),
        )

        assertEquals(1, split.questions.size)
        assertEquals("０１", split.questions[0].sourceNumber)
        assertTrue(split.questions[0].reviewReasons.any { it.contains("未找到答案") })
    }

    @Test
    fun `parser default mode ignores full width question numbers`() {
        val text = "０１．全角题号的虚构题干是否完整？\n"

        val result = LocalQuestionParser().parse(text)

        assertTrue(result.questions.isEmpty())
    }

    @Test
    fun `split mode consumes a single trailing end answer pair without creating a question`() {
        val text = """
            1. 单条文末答案的虚构题干一是否完整？
            A. 甲
            B. 乙
            2. 单条文末答案的虚构题干二是否完整？
            A. 甲
            B. 乙
            参考答案
            2.B
        """.trimIndent()

        val split = LocalQuestionParser().parse(
            text,
            SplitParseOptions(
                sequentialAnswerFallback = false,
                consumeSingleEndAnswerPair = true,
                skipDuplicateNumberEndAnswers = true,
            ),
        )
        val normal = LocalQuestionParser().parse(text)

        assertEquals(2, split.questions.size)
        assertEquals("B", split.questions[1].answer)
        assertEquals("", split.questions[0].answer)
        assertTrue(normal.questions.size > 2)
    }

    @Test
    fun `split mode consumes full width trailing end answer pairs without creating questions`() {
        // 阻塞反例：全角文末答案行此前不被消费，「１.Ｂ」「３.Ａ」会经全角题号识别变成幻影题
        val text = """
            1. 全角文末答案的虚构题干一是否完整？
            A. 甲
            B. 乙
            参考答案
            １.Ｂ
            ３.Ａ
        """.trimIndent()

        val split = LocalQuestionParser().parse(
            text,
            SplitParseOptions(
                sequentialAnswerFallback = false,
                fullWidthNumbers = true,
                consumeSingleEndAnswerPair = true,
                skipDuplicateNumberEndAnswers = true,
            ),
        )
        val normal = LocalQuestionParser().parse(text)

        assertEquals(1, split.questions.size)
        assertEquals("1", split.questions[0].sourceNumber)
        assertEquals("B", split.questions[0].answer)
        assertFalse(split.questions[0].needsReview)
        assertTrue(split.warnings.any { it.contains("题号 3") && it.contains("未找到对应题目") })
        // 普通导入行为照旧：全角行不消费、不成新题，答案留空
        assertEquals(1, normal.questions.size)
        assertEquals("", normal.questions[0].answer)
        assertTrue(normal.warnings.none { it.contains("题号 3") })
    }

    @Test
    fun `split mode keeps sparse numbering confirmable and blocks duplicates via matcher`() {
        // 预审场景：０１/3/7/9/11 稀疏合法；重复的 9 由 matcher 阻断，其余照常匹配入库
        val parsed = LocalQuestionParser().parse(
            """
            ０１. 稀疏验收的虚构题干一是否完整？
            A. 甲
            B. 乙
            3. 稀疏验收的虚构题干三是否完整？
            A. 甲
            B. 乙
            7. 稀疏验收的虚构题干七是否完整？
            A. 甲
            B. 乙
            9. 稀疏验收的虚构题干九是否完整？
            A. 甲
            B. 乙
            9. 稀疏验收的重复题干是否完整？
            A. 甲
            B. 乙
            11. 稀疏验收的虚构题干十一是否完整？
            A. 甲
            B. 乙
            """.trimIndent(),
            SplitParseOptions(
                sequentialAnswerFallback = false,
                fullWidthNumbers = true,
                allowNumberGaps = true,
            ),
        )
        assertEquals(listOf("０１", "3", "7", "9", "9", "11"), parsed.questions.map { it.sourceNumber })

        val output = SplitFileMatcher.match(
            parsed.questions,
            listOf(entry(11, "B"), entry(1, "A"), entry(3, "B"), entry(7, "A")),
        )

        // 原序保留，乱序答案按题号对号
        assertEquals(listOf("０１", "3", "7", "9", "9", "11"), output.drafts.map { it.sourceNumber })
        assertEquals("A", output.drafts[0].answer)
        assertEquals("B", output.drafts[1].answer)
        assertEquals("A", output.drafts[2].answer)
        assertEquals("B", output.drafts[5].answer)
        // 稀疏跳号不加待校对；重复的 9 由 matcher 阻断
        assertTrue(output.drafts.none { it.reviewReasons.any { reason -> reason.contains("跳到") } })
        assertTrue(output.problems[3] is SplitDraftProblem.DuplicateQuestionNumber)
        assertTrue(output.problems[4] is SplitDraftProblem.DuplicateQuestionNumber)
        assertNull(output.problems[0])
        assertNull(output.problems[5])
        assertTrue(output.warnings.any { it.contains("试题文件题号 9 重复") })
        // 除重复的两题外全部可入库
        assertTrue(
            listOf(0, 1, 2, 5).all { index ->
                !output.drafts[index].needsReview && output.drafts[index].answer.isNotBlank()
            },
        )
    }

    @Test
    fun `split mode never back fills end answers for duplicated question numbers`() {
        val text = """
            1. 重复题号的第一条虚构题干是否完整？
            A. 甲
            B. 乙
            1. 重复题号的第二条虚构题干是否完整？
            A. 甲
            B. 乙
            2. 正常题号的虚构题干是否完整？
            A. 甲
            B. 乙
            参考答案
            1.A
            2.B
        """.trimIndent()

        val split = LocalQuestionParser().parse(
            text,
            SplitParseOptions(
                sequentialAnswerFallback = false,
                consumeSingleEndAnswerPair = true,
                skipDuplicateNumberEndAnswers = true,
            ),
        )
        val normal = LocalQuestionParser().parse(text)

        assertTrue(split.questions.filter { it.sourceNumber == "1" }.all { it.answer.isBlank() })
        assertEquals("B", split.questions.last { it.sourceNumber == "2" }.answer)
        // 普通导入保持既有行为：重复题号照样回填
        assertTrue(normal.questions.filter { it.sourceNumber == "1" }.all { it.answer.isNotBlank() })
    }

    @Test
    fun `split mode does not flag legal number gaps but normal mode does`() {
        val text = """
            1. 跳号验收的虚构题干一是否完整？
            A. 甲
            B. 乙
            2. 跳号验收的虚构题干二是否完整？
            A. 甲
            B. 乙
            4. 跳号验收的虚构题干四是否完整？
            A. 甲
            B. 乙
        """.trimIndent()

        val split = LocalQuestionParser().parse(
            text,
            SplitParseOptions(
                sequentialAnswerFallback = false,
                allowNumberGaps = true,
            ),
        )
        val normal = LocalQuestionParser().parse(text)

        assertTrue(split.questions.none { it.reviewReasons.any { reason -> reason.contains("跳到") } })
        assertTrue(normal.questions.any { it.reviewReasons.any { reason -> reason.contains("跳到") } })
    }

    @Test
    fun `split mode never steals next question after inline answer ending with letter line`() {
        // 反例：第 1 题题内答案后紧接「2. A 型血…」开头的新题
        val text = """
            1. 中间题内答案的虚构题干一是否完整？
            A. 甲
            B. 乙
            答案：A
            2. A 型血的虚构题干二是否完整？
            A. 甲型
            B. 乙型
            答案：B
        """.trimIndent()

        val split = LocalQuestionParser().parse(
            text,
            SplitParseOptions(
                sequentialAnswerFallback = false,
                consumeSingleEndAnswerPair = true,
                skipDuplicateNumberEndAnswers = true,
            ),
        )

        assertEquals(2, split.questions.size)
        assertEquals("A 型血的虚构题干二是否完整？", split.questions[1].stem)
        assertEquals("A", split.questions[0].answer)
        assertEquals("B", split.questions[1].answer)
    }

    @Test
    fun `split mode only consumes complete pair lines after answer marker`() {
        val text = """
            1. 文末答案前的虚构题干一是否完整？
            A. 甲
            B. 乙
            参考答案
            2. A 型血开头的虚构题干是否完整？
            A. 甲型
            B. 乙型
        """.trimIndent()

        val split = LocalQuestionParser().parse(
            text,
            SplitParseOptions(
                sequentialAnswerFallback = false,
                consumeSingleEndAnswerPair = true,
                skipDuplicateNumberEndAnswers = true,
            ),
        )

        // 「2. A 型血…」不是整行答案对：不得被截走，必须保留为新题
        assertEquals(2, split.questions.size)
        assertEquals("A 型血开头的虚构题干是否完整？", split.questions[1].stem)
        assertTrue(split.questions.all { it.answer.isBlank() })
    }
}
