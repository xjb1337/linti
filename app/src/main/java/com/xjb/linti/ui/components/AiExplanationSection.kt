package com.xjb.linti.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.explanation.AiExplanationRecord
import com.xjb.linti.ui.AiExplanationRequestState
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.theme.LocalLintiPalette

val LocalAiExplanationSettings = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun AiExplanationSection(questionId: Long, viewModel: LintiViewModel) {
    val record by remember(viewModel, questionId) { viewModel.observeAiExplanation(questionId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val requests by viewModel.aiExplanationRequests.collectAsStateWithLifecycle()
    val openSettings = LocalAiExplanationSettings.current
    AiExplanationContent(
        record = record,
        request = requests[questionId] ?: AiExplanationRequestState(),
        onGenerate = { viewModel.requestAiExplanation(questionId, openSettings) },
        onDelete = { viewModel.deleteAiExplanation(questionId) },
    )
}

@Composable
fun AiExplanationContent(
    record: AiExplanationRecord?,
    request: AiExplanationRequestState,
    onGenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    if (record == null && !request.loading && request.error == null) {
        OutlinedActionButton(text = "AI 解析", onClick = onGenerate, modifier = Modifier.testTag("ai-explanation-generate"))
        return
    }
    LintiCard(modifier = Modifier.fillMaxWidth().testTag("ai-explanation-card"), borderColor = palette.primarySoft) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI 解析", style = MaterialTheme.typography.titleSmall, color = palette.primary)
                if (record != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(record.explanation.model, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                }
            }
            if (record?.stale == true) Text("题目已修改", style = MaterialTheme.typography.labelMedium, color = palette.warning)
            if (record != null) Text(record.explanation.content, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
            if (request.loading) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("ai-explanation-loading")) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在生成", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
                }
            }
            request.error?.let { Text(it.message, color = palette.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("ai-explanation-error")) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onGenerate, enabled = !request.loading, modifier = Modifier.testTag("ai-explanation-generate")) {
                    Text(if (record == null) "AI 解析" else "重新生成")
                }
                if (record != null) TextButton(onClick = onDelete, enabled = !request.loading, modifier = Modifier.testTag("ai-explanation-delete")) { Text("删除") }
            }
        }
    }
}
