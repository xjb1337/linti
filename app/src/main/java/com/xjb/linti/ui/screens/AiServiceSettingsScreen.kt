package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.ocr.OcrKeyResolver
import com.xjb.linti.data.prefs.AiExplanationReasoningEffort
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiFilterChip
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.SettingSwitchRow
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.components.WarningNote
import com.xjb.linti.ui.theme.LocalLintiPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiServiceSettingsScreen(viewModel: LintiViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var apiKey by remember { mutableStateOf("") }

    fun saveAiSettings() {
        viewModel.saveAiSettings(
            provider = settings.provider,
            apiKey = apiKey.takeIf { it.isNotBlank() },
            baseUrl = settings.baseUrl,
            model = settings.model,
        )
        apiKey = ""
    }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("AI 服务", pageListState, onBack = onBack) {
        LazyColumn(
            state = pageListState,
            modifier = Modifier.testTag("ai-settings-list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(title = "AI 服务", subtitle = "出题 · 解析 · 导入识别", onBack = onBack)
            }
            item {
                LintiCard(shapeRadius = 20.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            placeholder = {
                                Text(
                                    if (settings.hasApiKey) {
                                        settings.apiKeyHint
                                    } else {
                                        "sk-…"
                                    },
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
                        Text(
                            text = "只需填写 Key：默认接口 https://api.deepseek.com，模型 deepseek-flash",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                        val hasLegacyCustomEndpoint = settings.provider != AiProvider.DEEPSEEK ||
                            settings.baseUrl.isNotBlank() || settings.model.isNotBlank()
                        if (hasLegacyCustomEndpoint) {
                            val activeBase = settings.baseUrl.ifBlank { OcrKeyResolver.OFFICIAL_BASE_URL }
                            val activeModel = settings.model.ifBlank { "默认模型" }
                            WarningNote("当前保留了旧版配置（${settings.provider.label} · $activeBase · $activeModel），可恢复为默认 DeepSeek。")
                            OutlinedActionButton(
                                text = "恢复默认 DeepSeek",
                                modifier = Modifier.fillMaxWidth().testTag("reset-default-ai-endpoint"),
                                onClick = viewModel::resetAiEndpointToDefault,
                            )
                        }
                        AiExplanationReasoningSettings(
                            selected = settings.aiExplanationReasoningEffort,
                            onSelect = viewModel::setAiExplanationReasoningEffort,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedActionButton(
                                text = "保存 AI 设置",
                                modifier = Modifier.weight(1f),
                                onClick = ::saveAiSettings,
                            )
                            if (settings.hasApiKey) {
                                OutlinedActionButton(
                                    text = "清除 Key",
                                    onClick = viewModel::clearApiKey,
                                )
                            }
                        }
                        ThinDivider()
                        SettingSwitchRow(
                            title = "本地识别优先",
                            checked = settings.localFirst,
                            onCheckedChange = viewModel::setLocalFirst,
                        )
                        SettingSwitchRow(
                            title = "本地失败时才用 AI",
                            checked = settings.callAiOnlyWhenLocalFails,
                            onCheckedChange = viewModel::setCallAiOnlyWhenLocalFails,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = LocalLintiPalette.current.success,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "本机加密存储 · 仅 HTTPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiExplanationReasoningSettings(
    selected: AiExplanationReasoningEffort,
    onSelect: (AiExplanationReasoningEffort) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("思考强度", style = MaterialTheme.typography.bodyMedium, color = LocalLintiPalette.current.textPrimary)
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            AiExplanationReasoningEffort.entries.forEach { effort ->
                LintiFilterChip(
                    modifier = Modifier.testTag("reasoning-effort-${effort.apiValue}"),
                    selected = selected == effort,
                    onClick = { onSelect(effort) },
                    label = effort.label,
                )
            }
        }
    }
}