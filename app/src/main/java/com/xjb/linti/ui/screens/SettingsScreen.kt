package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.BuildConfig
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.StudySummary
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.formatPercent
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(viewModel: LintiViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    var summary by remember { mutableStateOf<StudySummary?>(null) }
    var provider by remember(settings.provider) { mutableStateOf(settings.provider) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember(settings.baseUrl) { mutableStateOf(settings.baseUrl) }
    var model by remember(settings.model) { mutableStateOf(settings.model) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(banks.size, banks.sumOf { it.sequenceAnsweredCount }) {
        summary = viewModel.studySummary()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = viewModel.exportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                        writer.write(json)
                    } ?: error("无法写入备份文件")
                }
            }.onSuccess {
                viewModel.showMessage("JSON 备份已导出")
            }.onFailure {
                viewModel.showMessage("导出失败：${it.message ?: "无法写入文件"}")
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("无法读取所选文件")
                }
            }.onSuccess {
                pendingRestoreJson = it
            }.onFailure {
                viewModel.showMessage("读取备份失败：${it.message ?: "文件不可读"}")
            }
        }
    }

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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LintiPageHeader(
                title = "我的",
                actions = {
                    StatusPill("离线也能学", color = LocalLintiPalette.current.success, background = LocalLintiPalette.current.successSoft)
                },
            )
        }
        item {
            LintiCard(containerColor = LocalLintiPalette.current.primaryPale, borderColor = LocalLintiPalette.current.primarySoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(LocalLintiPalette.current.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = LocalLintiPalette.current.surface,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(
                            text = "临题 · 自用版",
                            style = MaterialTheme.typography.titleLarge,
                            color = LocalLintiPalette.current.textPrimary,
                        )
                        Text(
                            text = "全部功能免费，数据保存在本机",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                }
            }
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
            SectionHeader(
                title = "AI 服务",
                trailing = "可选补充",
                trailingColor = LocalLintiPalette.current.aiAccent,
            )
        }
        item {
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = LocalLintiPalette.current.aiAccent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "本地识别优先，不依赖 AI 也能导入与刷题。",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
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
                                    "已保存（${settings.apiKeyHint}），留空保持不变"
                                } else {
                                    "粘贴到本机，不预填、不上传"
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
                        label = { Text("模型名（可选）") },
                        placeholder = { Text("留空使用服务商默认模型") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(12.dp),
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
                        subtitle = "默认不让 AI 参与识别",
                        checked = settings.localFirst,
                        onCheckedChange = viewModel::setLocalFirst,
                    )
                    SettingSwitchRow(
                        title = "仅本地失败时调用 AI",
                        subtitle = "最小化发送待校对片段",
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
                            text = "Key 使用 Android 加密存储，仅保存在本机；只支持 HTTPS。",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                }
            }
        }
        item {
            SectionHeader(title = "数据", trailing = "文件由你自己保管")
        }
        item {
            LintiCard {
                Column {
                    DataMenuRow(
                        icon = Icons.Outlined.Upload,
                        title = "导出 JSON 备份",
                        subtitle = "题库、作答、错题、收藏、笔记与进度",
                        onClick = {
                            exportLauncher.launch("linti-backup-${LocalDate.now()}.json")
                        },
                    )
                    ThinDivider()
                    DataMenuRow(
                        icon = Icons.Outlined.Restore,
                        title = "从 JSON 备份恢复",
                        subtitle = "恢复会替换当前本机数据，建议先导出",
                        onClick = {
                            restoreLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream"),
                            )
                        },
                    )
                    ThinDivider()
                    DataMenuRow(
                        icon = Icons.Outlined.LibraryBooks,
                        title = "载入示例题库",
                        subtitle = "10 道虚构教材题，可随时编辑或删除",
                        onClick = viewModel::loadSampleData,
                    )
                }
            }
        }
        item {
            LintiCard(containerColor = LocalLintiPalette.current.background, borderColor = LocalLintiPalette.current.line) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = LocalLintiPalette.current.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "无账号、无会员、无广告、无付费解锁。所有学习和题库功能均在本机可用。",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                }
            }
        }
        item {
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = "关于临题",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalLintiPalette.current.textPrimary,
                    )
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME} · Batch 1 工程骨架",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                    Text(
                        text = "自用刷题工具，本地优先。示例题均为虚构教材内容，不包含真实身份信息；应用不收取任何费用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                }
            }
        }
    }

    val restoreJson = pendingRestoreJson
    if (restoreJson != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            title = { Text("恢复本机数据？") },
            text = {
                Text("这会替换当前题库、作答、错题、收藏、笔记和进度。该操作不可撤销，建议先导出当前数据。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreJson = null
                        scope.launch {
                            viewModel.restoreBackupJson(restoreJson)
                        }
                    },
                ) {
                    Text("确认恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreJson = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = LocalLintiPalette.current.textSecondary,
            )
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
