package com.xjb.linti.data

import android.content.Context
import com.xjb.linti.data.import.AiClient
import com.xjb.linti.data.import.AnswerFileReader
import com.xjb.linti.data.import.ConfirmImportUseCase
import com.xjb.linti.data.import.CsvDocumentExtractor
import com.xjb.linti.data.import.DocxDocumentExtractor
import com.xjb.linti.data.import.DocumentExtractorRegistry
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.import.ImportPipeline
import com.xjb.linti.data.import.LocalQuestionParser
import com.xjb.linti.data.import.OpenAiCompatibleAiClient
import com.xjb.linti.data.import.PdfDocumentExtractor
import com.xjb.linti.data.import.SplitFileImporter
import com.xjb.linti.data.import.TxtDocumentExtractor
import com.xjb.linti.data.import.UnavailableOcrEngine
import com.xjb.linti.data.import.XlsxDocumentExtractor
import com.xjb.linti.data.aigeneration.AiQuestionRepository
import com.xjb.linti.data.aigeneration.OpenAiQuestionClient
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.explanation.AiExplanationRepository
import com.xjb.linti.data.explanation.OpenAiExplanationClient
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.data.ocr.DeepSeekOcrClient
import com.xjb.linti.data.ocr.OcrKeyResolver
import com.xjb.linti.data.ocr.OcrKeySource
import com.xjb.linti.data.prefs.QuizPreferences
import com.xjb.linti.data.prefs.SecureSettingsStore
import com.xjb.linti.data.prefs.SettingsStore
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.update.UpdateClient
import com.xjb.linti.data.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(
    context: Context,
    val database: LintiDatabase = LintiDatabase.create(context.applicationContext),
) {
    private val applicationContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore: SettingsStore = SecureSettingsStore(applicationContext)
    val quizPreferences = QuizPreferences(applicationContext)
    val repository = LintiRepository(database) { quizPreferences.masteryThreshold.value }
    val aiQuestionRepository = AiQuestionRepository(database, settingsStore, OpenAiQuestionClient())
    val aiExplanationRepository = AiExplanationRepository(database, settingsStore, OpenAiExplanationClient())
    val parser = LocalQuestionParser()
    val extractorRegistry = DocumentExtractorRegistry(
        extractors = mapOf(
            DocumentKind.TXT to TxtDocumentExtractor(),
            DocumentKind.CSV to CsvDocumentExtractor(),
            DocumentKind.DOCX to DocxDocumentExtractor(),
            DocumentKind.XLSX to XlsxDocumentExtractor(),
            DocumentKind.PDF to PdfDocumentExtractor(),
        ),
        ocrEngine = UnavailableOcrEngine(),
    )
    val importPipeline = ImportPipeline(
        extractorRegistry = extractorRegistry,
        parser = parser,
        aiClientProvider = ::createAiClient,
    )
    val splitFileImporter = SplitFileImporter(
        extractorRegistry = extractorRegistry,
        parser = parser,
        answerFileReader = AnswerFileReader(),
    )
    val confirmImportUseCase = ConfirmImportUseCase(repository)
    val updateClient = UpdateClient(applicationContext.cacheDir)
    val updateInstaller = UpdateInstaller(applicationContext)

    fun start() {
        appScope.launch {
            runCatching { repository.seedSampleDataIfEmpty() }
        }
    }

    private fun createAiClient(): AiClient? {
        val snapshot = settingsStore.snapshot()
        val apiKey = settingsStore.apiKeyForRequest()
        if (apiKey.isBlank() || snapshot.baseUrl.isBlank()) return null
        val defaultModel = when (snapshot.provider) {
            AiProvider.DEEPSEEK -> "deepseek-flash"
            AiProvider.OPENAI_COMPATIBLE, AiProvider.CUSTOM -> "gpt-4o-mini"
        }
        return OpenAiCompatibleAiClient(
            baseUrl = snapshot.baseUrl,
            apiKey = apiKey,
            model = snapshot.model.ifBlank { defaultModel },
        )
    }

    /**
     * DeepSeek OCR 独立 client 工厂：端点硬编码为官方地址，不吃自定义 Base URL。
     * 仅当 AI 配置明确指向官方 DeepSeek 时才复用 AI Key，
     * 否则必须使用独立配置的 DeepSeek OCR Key——第三方 Key 绝不发给 DeepSeek。
     * 无可用 Key 返回 null（调用方引导设置，不发请求）。
     */
    fun createOcrClient(): DeepSeekOcrClient? {
        val snapshot = settingsStore.snapshot()
        val dedicatedKey = settingsStore.ocrApiKeyForRequest()
        return when (
            OcrKeyResolver.resolve(
                provider = snapshot.provider,
                baseUrl = snapshot.baseUrl,
                aiKeyPresent = snapshot.hasApiKey,
                dedicatedKeyPresent = dedicatedKey.isNotBlank(),
            )
        ) {
            OcrKeySource.Dedicated -> DeepSeekOcrClient(dedicatedKey)
            OcrKeySource.ReuseAi -> DeepSeekOcrClient(settingsStore.apiKeyForRequest())
            OcrKeySource.None -> null
        }
    }
}
