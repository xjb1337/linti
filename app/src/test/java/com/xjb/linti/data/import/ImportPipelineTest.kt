package com.xjb.linti.data.import

import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ImportPipelineTest {
    private val localText = """
        1. 流水线虚构题干是否完整？
        A. 甲
        B. 乙
        答案：A
    """.trimIndent()

    @Test
    fun `local only parses without calling AI`() = runTest {
        var calls = 0
        val pipeline = pipeline(
            aiClient = FakeAiClient {
                calls++
                emptyList()
            },
        )

        val run = pipeline.run(
            input = ByteArrayInputStream(localText.toByteArray()),
            fileName = "questions.txt",
            method = ImportMethod.LOCAL_ONLY,
        )

        assertEquals(1, run.questions.size)
        assertEquals(0, calls)
        assertEquals(ImportMethod.LOCAL_ONLY, run.method)
    }

    @Test
    fun `AI only uses fake client and always marks output for review`() = runTest {
        var receivedText = ""
        val pipeline = pipeline(
            aiClient = FakeAiClient {
                receivedText = it
                listOf(aiQuestion(needsReview = false))
            },
        )

        val run = pipeline.run(
            input = ByteArrayInputStream(localText.toByteArray()),
            fileName = "questions.txt",
            method = ImportMethod.AI_ONLY,
        )

        assertTrue(receivedText.contains("流水线"))
        assertEquals(1, run.questions.size)
        assertTrue(run.questions.single().needsReview)
        assertTrue(run.questions.single().reviewReasons.any { it.contains("AI 识别") })
    }

    @Test
    fun `local then AI only sends pending fragment and merges result`() = runTest {
        var calls = 0
        var receivedText = ""
        val missingAnswerText = """
            1. 缺少答案的流水线虚构题干是否完整？
            A. 甲
            B. 乙
        """.trimIndent()
        val pipeline = pipeline(
            aiClient = FakeAiClient {
                calls++
                receivedText = it
                listOf(aiQuestion(needsReview = false, answer = "B"))
            },
        )

        val run = pipeline.run(
            input = ByteArrayInputStream(missingAnswerText.toByteArray()),
            fileName = "questions.txt",
            method = ImportMethod.LOCAL_THEN_AI,
        )

        assertEquals(1, calls)
        assertTrue(receivedText.contains("缺少答案"))
        assertEquals("B", run.questions.single().answer)
        assertTrue(run.questions.single().needsReview)
    }

    @Test
    fun `local then AI does not call AI when local result is clean`() = runTest {
        var calls = 0
        val pipeline = pipeline(
            aiClient = FakeAiClient {
                calls++
                emptyList()
            },
        )

        val run = pipeline.run(
            input = ByteArrayInputStream(localText.toByteArray()),
            fileName = "questions.txt",
            method = ImportMethod.LOCAL_THEN_AI,
        )

        assertEquals(0, calls)
        assertFalse(run.questions.single().needsReview)
    }

    @Test
    fun `missing AI configuration keeps local results and adds warning`() = runTest {
        val pipeline = pipeline(aiClient = null)
        val missingAnswerText = """
            1. 缺少答案的流水线虚构题干是否完整？
            A. 甲
            B. 乙
        """.trimIndent()

        val run = pipeline.run(
            input = ByteArrayInputStream(missingAnswerText.toByteArray()),
            fileName = "questions.txt",
            method = ImportMethod.LOCAL_THEN_AI,
        )

        assertEquals(1, run.questions.size)
        assertTrue(run.warnings.any { it.contains("未配置 AI") })
    }

    @Test
    fun `AI exception is contained and reported`() = runTest {
        val pipeline = pipeline(
            aiClient = FakeAiClient { error("模拟网络失败") },
        )

        val run = pipeline.run(
            input = ByteArrayInputStream(localText.toByteArray()),
            fileName = "questions.txt",
            method = ImportMethod.AI_ONLY,
        )

        assertTrue(run.questions.isEmpty())
        assertTrue(run.warnings.any { it.contains("AI 识别失败") })
    }

    private fun pipeline(aiClient: AiClient?): ImportPipeline {
        val registry = DocumentExtractorRegistry(
            extractors = mapOf(DocumentKind.TXT to TxtDocumentExtractor()),
            ocrEngine = UnavailableOcrEngine(),
        )
        return ImportPipeline(
            extractorRegistry = registry,
            parser = LocalQuestionParser(),
            aiClientProvider = { aiClient },
        )
    }

    private fun aiQuestion(
        needsReview: Boolean,
        answer: String = "A",
    ) = DraftQuestion(
        sourceNumber = "1",
        type = QuestionType.SINGLE,
        stem = "AI 返回的虚构题干是否完整？",
        options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")),
        answer = answer,
        explanation = "AI 返回的虚构解析",
        needsReview = needsReview,
    )

    private class FakeAiClient(
        private val block: (String) -> List<DraftQuestion>,
    ) : AiClient {
        override suspend fun parseQuestions(text: String): List<DraftQuestion> = block(text)
    }
}
