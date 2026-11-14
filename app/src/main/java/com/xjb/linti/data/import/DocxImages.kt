package com.xjb.linti.data.import

import com.xjb.linti.data.images.QuestionImageStore
import com.xjb.linti.domain.DraftQuestion
import java.io.ByteArrayInputStream

/** Markers are only used during local parsing and are removed before any AI request. */
internal object DocxImages {
    private val markerPattern = Regex("\\uE000(image[0-9]+)\\uE001")
    fun marker(key: String) = "\uE000${key}\uE001"
    fun strip(text: String): String = text.replace(markerPattern, "")

    fun attach(
        parsed: List<DraftQuestion>,
        extraction: ExtractionResult,
        store: QuestionImageStore?,
        warnings: MutableList<String>,
    ): List<DraftQuestion> = parsed.map { draft ->
        val markers = markerPattern.findAll(draft.stem).map { it.groupValues[1] }.toList()
        if (markers.size > QuestionImageStore.MAX_IMAGES) warnings += "第 ${draft.sourceNumber} 题图片超过 4 张，已保留前 4 张"
        val images = if (store == null) emptyList() else markers.take(QuestionImageStore.MAX_IMAGES).mapNotNull { key ->
            extraction.embeddedImages[key]?.let { bytes ->
                runCatching { store.import(ByteArrayInputStream(bytes)) }.getOrElse {
                    warnings += "第 ${draft.sourceNumber} 题的图片无法读取"
                    null
                }
            }
        }
        draft.copy(
            stem = strip(draft.stem).trim(),
            options = draft.options.map { it.copy(text = strip(it.text).trim()) },
            answer = strip(draft.answer).trim(),
            explanation = strip(draft.explanation).trim(),
            rawSnippet = draft.rawSnippet?.let(::strip),
            images = images,
        )
    }
}
