package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.theme.LocalLintiPalette

@Composable
fun OcrSettingsScreen(viewModel: LintiViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var ocrApiKey by remember { mutableStateOf("") }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("OCR 识别", pageListState, onBack = onBack) {
        LazyColumn(
            state = pageListState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(title = "OCR 识别", subtitle = "图片 / 扫描 PDF 在线识别", onBack = onBack)
            }
            item {
                LintiCard(shapeRadius = 20.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OcrKeySourceHint(
                            provider = settings.provider,
                            baseUrl = settings.baseUrl,
                            hasAiKey = settings.hasApiKey,
                            dedicatedPresent = settings.hasOcrApiKey,
                        )
                        OutlinedTextField(
                            value = ocrApiKey,
                            onValueChange = { ocrApiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("DeepSeek OCR Key（独立）") },
                            placeholder = {
                                Text(
                                    if (settings.hasOcrApiKey) settings.ocrApiKeyHint else "sk-…",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Key,
                                    contentDescription = null,
                                    tint = LocalLintiPalette.current.textSecondary,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(12.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedActionButton(
                                text = "保存 OCR Key",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    viewModel.saveOcrApiKey(ocrApiKey)
                                    ocrApiKey = ""
                                },
                            )
                            if (settings.hasOcrApiKey) {
                                OutlinedActionButton(
                                    text = "清除",
                                    onClick = viewModel::clearOcrApiKey,
                                )
                            }
                        }
                        Text(
                            text = "识别仅调用官方 api.deepseek.com；AI 配置指向官方 DeepSeek 时可复用其 Key，" +
                                "第三方中转的 Key 不会发给 DeepSeek",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

// OCR Key 状态提示：与 OcrKeyResolver 决策一致（独立 Key 优先；仅官方 DeepSeek 配置复用 AI Key）
@Composable
private fun OcrKeySourceHint(
    provider: AiProvider,
    baseUrl: String,
    hasAiKey: Boolean,
    dedicatedPresent: Boolean,
) {
    val reuseOfficial = com.xjb.linti.data.ocr.OcrKeyResolver.isOfficialDeepSeekBaseUrl(baseUrl)
    val text = when {
        dedicatedPresent -> "当前使用独立配置的 DeepSeek OCR Key"
        provider == AiProvider.DEEPSEEK && hasAiKey && reuseOfficial ->
            "AI 配置指向官方 DeepSeek，将复用已保存的 AI Key"
        else -> "未配置：请填入独立的 DeepSeek OCR Key"
    }
    val color = when {
        dedicatedPresent -> LocalLintiPalette.current.success
        provider == AiProvider.DEEPSEEK && hasAiKey && reuseOfficial -> LocalLintiPalette.current.success
        else -> LocalLintiPalette.current.warning
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}