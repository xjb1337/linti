package com.xjb.linti.ui.components

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType

@Composable
internal fun QuestionDraftEditor(
    draft: DraftQuestion,
    index: Int,
    onChange: (DraftQuestion) -> Unit,
) {
    var acceptedText by remember { mutableStateOf(draft.acceptedAnswers.joinToString("\n") { it.joinToString(" | ") }) }
    LintiCard(
        shapeRadius = 15.dp,
        containerColor = if (draft.needsReview) {
            LocalLintiPalette.current.warningSoft
        } else {
            LocalLintiPalette.current.surface
        },
        borderColor = if (draft.needsReview) {
            LocalLintiPalette.current.warning
        } else {
            LocalLintiPalette.current.line
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 ${index + 1} 条 · 原题号 ${draft.sourceNumber.ifBlank { "—" }}",
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalLintiPalette.current.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (draft.needsReview) {
                    StatusPill("待核对", color = LocalLintiPalette.current.warning, background = LocalLintiPalette.current.warningSoft)
                } else {
                    StatusPill("已确认", color = LocalLintiPalette.current.success, background = LocalLintiPalette.current.successSoft)
                }
            }
            if (draft.reviewReasons.isNotEmpty()) {
                WarningNote(
                    message = "需核对：" + draft.reviewReasons.joinToString("；"),
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QuestionType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { onChange(draft.copy(type = type)) },
                        label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LocalLintiPalette.current.primarySoft,
                            selectedLabelColor = LocalLintiPalette.current.primary,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = draft.stem,
                onValueChange = { onChange(draft.copy(stem = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("题干") },
                minLines = 2,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(10.dp),
            )
            if (draft.type == QuestionType.SINGLE || draft.type == QuestionType.MULTI) {
                draft.options.forEachIndexed { optionIndex, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label.ifBlank {
                                ('A'.code + optionIndex).toChar().toString()
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalLintiPalette.current.textSecondary,
                            modifier = Modifier.width(22.dp),
                        )
                        OutlinedTextField(
                            value = option.text,
                            onValueChange = { text ->
                                val options = draft.options.toMutableList()
                                options[optionIndex] = option.copy(text = text)
                                onChange(draft.copy(options = options))
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("选项内容", style = MaterialTheme.typography.bodySmall) },
                            singleLine = false,
                            maxLines = 3,
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(9.dp),
                        )
                        if (draft.options.size > 2) {
                            IconButton(
                                onClick = {
                                    val options = draft.options
                                        .filterIndexed { current, _ -> current != optionIndex }
                                        .mapIndexed { current, old ->
                                            old.copy(
                                                label = ('A'.code + current).toChar().toString(),
                                            )
                                        }
                                    onChange(draft.copy(options = options))
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "删除选项",
                                    tint = LocalLintiPalette.current.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                if (draft.options.size < 8) {
                    TextButton(
                        onClick = {
                            val label = ('A'.code + draft.options.size).toChar().toString()
                            onChange(
                                draft.copy(
                                    options = draft.options + QuestionOption(label, ""),
                                ),
                            )
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("添加选项")
                    }
                }
            }
            if (draft.type == QuestionType.FILL) {
                OutlinedTextField(
                    value = acceptedText,
                    onValueChange = { value ->
                        acceptedText = value
                        val answers = value.lines().filter { it.isNotBlank() }.map { it.split("|").map(String::trim) }
                        onChange(draft.copy(acceptedAnswers = answers, answer = answers.joinToString("；") { it.firstOrNull().orEmpty() }))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("每空一行，同义写法用 | 分隔") },
                )
            }
            if (draft.type == QuestionType.JUDGE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.answer == "true",
                        onClick = { onChange(draft.copy(answer = "true")) },
                        label = { Text("正确") },
                    )
                    FilterChip(
                        selected = draft.answer == "false",
                        onClick = { onChange(draft.copy(answer = "false")) },
                        label = { Text("错误") },
                    )
                }
            } else if (draft.type != QuestionType.FILL || acceptedText.isBlank()) {
                OutlinedTextField(
                    value = draft.answer,
                    onValueChange = { onChange(draft.copy(answer = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(when (draft.type) { QuestionType.FILL -> "标准答案"; QuestionType.ESSAY -> "参考答案"; else -> "答案" })
                    },
                    placeholder = if (draft.type == QuestionType.MULTI) {
                        { Text("如 ABD", style = MaterialTheme.typography.bodySmall) }
                    } else {
                        null
                    },
                    singleLine = draft.type != QuestionType.ESSAY,
                    minLines = if (draft.type == QuestionType.ESSAY) 3 else 1,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp),
                )
            }
            OutlinedTextField(
                value = draft.explanation,
                onValueChange = { onChange(draft.copy(explanation = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("解析") },
                minLines = 2,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
            )
            OutlinedTextField(
                value = draft.knowledgePoint.orEmpty(),
                onValueChange = { onChange(draft.copy(knowledgePoint = it.ifBlank { null })) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("知识点 / 标签") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
            )
            if (draft.needsReview) {
                Button(
                    onClick = {
                        onChange(
                            draft.copy(
                                needsReview = false,
                                reviewReasons = emptyList(),
                                confidence = 1.0,
                            ),
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalLintiPalette.current.success,
                        contentColor = LocalLintiPalette.current.surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("确认此题")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = LocalLintiPalette.current.success,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            onChange(
                                draft.copy(
                                    needsReview = true,
                                    reviewReasons = listOf("用户重新标记为待核对"),
                                ),
                            )
                        },
                    ) {
                        Text("重新标记", color = LocalLintiPalette.current.warning)
                    }
                }
            }
        }
    }
}
