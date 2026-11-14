package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.Question
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.AiExplanationSection
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.theme.LocalLintiPalette

@Composable
internal fun QuestionExplanationDetail(questionId: Long, viewModel: LintiViewModel, onDismiss: () -> Unit) {
    val question by produceState<Question?>(initialValue = null, questionId) {
        value = viewModel.loadQuestionForExplanation(questionId)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("题目详情") },
        text = {
            val item = question
            if (item != null) {
                QuestionDetailContent(item) { AiExplanationSection(item.id, viewModel) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
internal fun QuestionDetailContent(item: Question, extra: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.origin == com.xjb.linti.domain.QuestionOrigin.AI_GENERATED) {
            StatusPill("AI 出题", color = LocalLintiPalette.current.aiAccent, background = LocalLintiPalette.current.aiSoft)
        }
        Text(item.stem, style = MaterialTheme.typography.bodyLarge)
        item.options.forEach { Text("${it.label}. ${it.text}") }
        Text("答案：${AnswerNormalizer.displayAnswer(item.type, item.answer)}")
        item.sourceExcerpt?.takeIf { it.isNotBlank() }?.let { Text("出处：$it") }
        Text("解析", style = MaterialTheme.typography.labelMedium)
        Text(item.explanation.ifBlank { "暂无解析" })
        extra()
    }
}
