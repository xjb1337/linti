package com.xjb.linti.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.BuildConfig
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.data.prefs.AiExplanationReasoningEffort
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.ColorTheme
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.StudySummary
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.formatPercent
import com.xjb.linti.ui.theme.LocalLintiPalette
import com.xjb.linti.ui.theme.palette
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: LintiViewModel) {
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val appearanceMode by viewModel.appearanceMode.collectAsStateWithLifecycle()
    val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val masteryThreshold by viewModel.masteryThreshold.collectAsStateWithLifecycle()
    val dailyNewCards by viewModel.dailyNewCards.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var summary by remember { mutableStateOf<StudySummary?>(null) }
    var provider by remember(settings.provider) { mutableStateOf(settings.provider) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    var ocrApiKey by remember { mutableStateOf("") }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(banks.size, banks.sumOf { it.sequenceAnsweredCount }) {
        summary = viewModel.studySummary()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { viewModel.exportBackup(uri) }
                .onSuccess { viewModel.showMessage("备份已导出") }
                .onFailure { viewModel.showMessage("导出失败：${it.message ?: "无法写入文件"}") }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestoreUri = uri }

    fun saveAiSettings() {
        if (baseUrl.isNotBlank() && !baseUrl.trim().startsWith("https://")) {
            viewModel.showMessage("Base URL 仅支持 HTTPS，避免密钥与题目明文传输")
            return
        }
        viewModel.saveAiSettings(
            provider = provider,
            apiKey = apiKey.takeIf { it.isNotBlank() },
            baseUrl = baseUrl,
            model = model,
        )
        apiKey = ""
    }

    LazyColumn(
        modifier = Modifier.testTag("settings-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LintiPageHeader(title = "我的")
        }
        item {
            MetricStrip(
                items = listOf(
                    MetricItem("${summary?.totalAnswered ?: 0}", "累计答题"),
                    MetricItem(summary?.accuracy?.let(::formatPercent) ?: "0%", "练习正确率"),
                    MetricItem("${summary?.streakDays ?: 0}", "连续学习 / 天"),
                ),
            )
        }
        item {
            SectionHeader(title = "外观")
        }
        item {
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "模式",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        AppearanceMode.entries.forEach { mode ->
                            FilterChip(
                                selected = appearanceMode == mode,
                                onClick = { viewModel.setAppearanceMode(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    ThinDivider()
                    Text(
                        text = "配色",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                    ThemeSwatches(
                        selected = colorTheme,
                        onSelect = viewModel::setColorTheme,
                    )
                }
            }
        }
        item {
            SectionHeader(title = "练习")
        }
        item {
            LintiCard { MasterySettingsContent(masteryThreshold, viewModel::setMasteryThreshold) }
        }
        item {
            LintiCard { DailyNewCardsSettingsContent(dailyNewCards, viewModel::setDailyNewCards) }
        }
        item {
            SectionHeader(title = "AI 服务", trailing = "可选")
        }
        item {
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        AiProvider.entries.forEach { item ->
                            FilterChip(
                                selected = provider == item,
                                onClick = { provider = item },
                                label = { Text(item.label) },
                            )
                        }
                    }
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
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Base URL") },
                        placeholder = { Text("https://api.example.com/v1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("模型名") },
                        placeholder = { Text("服务商默认") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(12.dp),
                    )
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
        item {
            SectionHeader(title = "DeepSeek OCR", trailing = "图片 / 扫描 PDF 在线识别")
        }
        item {
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OcrKeySourceHint(
                        provider = provider,
                        baseUrl = baseUrl,
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
        item {
            SectionHeader(title = "数据")
        }
        item {
            LintiCard {
                Column {
                    DataMenuRow(
                        icon = Icons.Outlined.Upload,
                        title = "导出备份",
                        subtitle = "题库、作答、错题、收藏、笔记、进度",
                        onClick = {
                            exportLauncher.launch("linti-backup-${LocalDate.now()}.zip")
                        },
                    )
                    ThinDivider()
                    DataMenuRow(
                        icon = Icons.Outlined.Restore,
                        title = "恢复备份",
                        subtitle = "替换本机数据，不可撤销；建议先导出",
                        onClick = {
                            restoreLauncher.launch(
                                arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream"),
                            )
                        },
                    )
                    ThinDivider()
                    DataMenuRow(
                        icon = Icons.Outlined.LibraryBooks,
                        title = "载入示例题库",
                        subtitle = "10 题 · 虚构教材",
                        onClick = viewModel::loadSampleData,
                    )
                }
            }
        }
        item {
            SectionHeader(title = "应用更新")
        }
        item {
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            tint = LocalLintiPalette.current.primary,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "临题 v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleMedium,
                                color = LocalLintiPalette.current.textPrimary,
                            )
                            updateState.release?.let { release ->
                                Text(
                                    text = "新版本 v${release.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalLintiPalette.current.warning,
                                )
                            }
                        }
                        OutlinedActionButton(
                            text = if (updateState.checking) "检查中…" else "检查更新",
                            enabled = !updateState.checking && !updateState.downloading,
                            onClick = { viewModel.checkForUpdates() },
                        )
                    }
                    ThinDivider()
                    SettingSwitchRow(
                        title = "启动时检查更新",
                        checked = autoCheckUpdates,
                        onCheckedChange = viewModel::setAutoCheckUpdates,
                    )
                }
            }
        }
    }

    val restoreUri = pendingRestoreUri
    if (restoreUri != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("恢复本机数据？") },
            text = {
                Text("这会替换当前题库、作答、错题、收藏、笔记和进度。该操作不可撤销，建议先导出当前数据。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        scope.launch {
                            viewModel.restoreBackup(restoreUri)
                        }
                    },
                ) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("取消")
                }
            },
        )
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
                FilterChip(
                    modifier = Modifier.heightIn(min = 48.dp).testTag("reasoning-effort-${effort.apiValue}"),
                    selected = selected == effort,
                    onClick = { onSelect(effort) },
                    label = { Text(effort.label) },
                    leadingIcon = if (selected == effort) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MasterySettingsContent(threshold: Int?, onThresholdChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("掌握所需连对", style = MaterialTheme.typography.bodyMedium, color = LocalLintiPalette.current.textPrimary)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            (listOf<Int?>(null) + (1..5).toList()).forEach { value ->
                FilterChip(
                    selected = threshold == value,
                    onClick = { onThresholdChange(value) },
                    label = { Text(value?.let { "${it} 次" } ?: "关闭") },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DailyNewCardsSettingsContent(limit: Int?, onLimitChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("每日新卡", style = MaterialTheme.typography.bodyMedium, color = LocalLintiPalette.current.textPrimary)
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf<Int?>(10, 20, 50, null).forEach { value ->
                FilterChip(selected = limit == value, onClick = { onLimitChange(value) }, label = { Text(value?.toString() ?: "不限") })
            }
        }
    }
}

@Composable
private fun ThemeSwatches(
    selected: ColorTheme,
    onSelect: (ColorTheme) -> Unit,
) {
    val dark = LocalLintiPalette.current.background.luminance() < 0.5f
    val themes = ColorTheme.entries.filter {
        it != ColorTheme.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        themes.forEach { theme ->
            val palette = theme.palette(dark)
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(theme) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(palette.background)
                        .border(
                            width = if (selected == theme) 2.dp else 1.dp,
                            color = if (selected == theme) palette.primary else palette.line,
                            shape = RoundedCornerShape(13.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (theme == ColorTheme.DYNAMIC) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color(0xFF0F766E),
                                            Color(0xFF4338CA),
                                            Color(0xFFBE123C),
                                            Color(0xFFB45309),
                                            Color(0xFF0F766E),
                                        ),
                                    ),
                                ),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(palette.primary),
                        )
                    }
                    if (selected == theme) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = palette.onPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = theme.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected == theme) {
                        LocalLintiPalette.current.primary
                    } else {
                        LocalLintiPalette.current.textSecondary
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalLintiPalette.current.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DataMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(LocalLintiPalette.current.primaryPale),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = LocalLintiPalette.current.primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalLintiPalette.current.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = LocalLintiPalette.current.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = LocalLintiPalette.current.textSecondary,
            modifier = Modifier.size(18.dp),
        )
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
