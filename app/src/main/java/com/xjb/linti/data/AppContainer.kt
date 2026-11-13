package com.xjb.linti.data

import android.content.Context
import com.xjb.linti.data.import.AiClient
import com.xjb.linti.data.import.ConfirmImportUseCase
import com.xjb.linti.data.import.CsvDocumentExtractor
import com.xjb.linti.data.import.DocxDocumentExtractor
import com.xjb.linti.data.import.DocumentExtractorRegistry
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.import.ImportPipeline
import com.xjb.linti.data.import.LocalQuestionParser
import com.xjb.linti.data.import.OpenAiCompatibleAiClient
import com.xjb.linti.data.import.PdfDocumentExtractor
import com.xjb.linti.data.import.TxtDocumentExtractor
import com.xjb.linti.data.import.UnavailableOcrEngine
import com.xjb.linti.data.import.XlsxDocumentExtractor
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.data.prefs.QuizPreferences
import com.xjb.linti.data.prefs.SecureSettingsStore
import com.xjb.linti.data.prefs.SettingsStore
import com.xjb.linti.data.repo.LintiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore: SettingsStore = SecureSettingsStore(applicationContext)
    val quizPreferences = QuizPreferences(applicationContext)
    val database = com.xjb.linti.data.db.LintiDatabase.create(applicationContext)
    val repository = LintiRepository(database)
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
    val confirmImportUseCase = ConfirmImportUseCase(repository)

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
            AiProvider.DEEPSEEK -> "deepseek-chat"
            AiProvider.OPENAI_COMPATIBLE, AiProvider.CUSTOM -> "gpt-4o-mini"
        }
        return OpenAiCompatibleAiClient(
            baseUrl = snapshot.baseUrl,
            apiKey = apiKey,
            model = snapshot.model.ifBlank { defaultModel },
        )
    }
}
