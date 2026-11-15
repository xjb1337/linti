package com.xjb.linti.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xjb.linti.data.aigeneration.MaterialBatch
import com.xjb.linti.data.aigeneration.MaterialChunks
import com.xjb.linti.data.explanation.AiExplanationConfig
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.components.LintiConfirmationSheet
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.MaterialFileResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class MaterialBatchStatus(val batch: MaterialBatch, val state: String = "待发送", val error: AiExplanationError? = null)

internal class MaterialQuestionState {
    val material = mutableStateOf("")
    val fileName = mutableStateOf("")
    val counts = mutableStateOf(QuestionType.entries.associateWith { if (it == QuestionType.SINGLE) 5 else 0 })
    val loading = mutableStateOf(false)
    val fileError = mutableStateOf<MaterialFileResult.Failed?>(null)
    val ocrConfirm = mutableStateOf<MaterialFileResult.NeedsOcr?>(null)
    val confirm = mutableStateOf(false)
    val host = mutableStateOf("")
    val batches = mutableStateOf<List<MaterialBatchStatus>>(emptyList())
    val config = mutableStateOf<AiExplanationConfig?>(null)
    val generationError = mutableStateOf<AiExplanationError?>(null)
}

@Composable
internal fun MaterialQuestionScreen(viewModel: LintiViewModel, state: MaterialQuestionState, scope: CoroutineScope, onSettings: () -> Unit, onOtherBasis: () -> Unit,
    onGenerateBatch: suspend (MaterialBatch, AiExplanationConfig) -> Unit, canReference: Boolean) {
    var material by state.material
    var counts by state.counts
    var loading by state.loading
    var fileError by state.fileError
    var ocrConfirm by state.ocrConfirm
    var confirm by state.confirm
    var host by state.host
    var batches by state.batches
    var config by state.config
    var generationError by state.generationError
    val repository = viewModel.aiQuestionRepository
    fun accept(result: MaterialFileResult) {
        when (result) {
            is MaterialFileResult.Text -> { material = result.text; state.fileName.value = result.fileName; batches = emptyList(); config = null; fileError = null; ocrConfirm = null }
            is MaterialFileResult.NeedsOcr -> { material = ""; state.fileName.value = ""; batches = emptyList(); config = null; ocrConfirm = result; fileError = null }
            is MaterialFileResult.Failed -> { material = ""; state.fileName.value = ""; batches = emptyList(); config = null; fileError = result; ocrConfirm = null }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { loading = true; try { accept(viewModel.readMaterial(uri)) } finally { loading = false } }
    }
    fun runBatches(indices: List<Int>) {
        val requestConfig = config ?: return
        if (loading) return
        loading = true
        scope.launch {
            try {
                for (index in indices) {
                    batches = batches.toMutableList().also { it[index] = it[index].copy(state = "生成中", error = null) }
                    try {
                        onGenerateBatch(batches[index].batch, requestConfig)
                        batches = batches.toMutableList().also { it[index] = it[index].copy(state = "已完成") }
                    } catch (e: CancellationException) { throw e }
                    catch (e: AiExplanationException) {
                        batches = batches.toMutableList().also { it[index] = it[index].copy(state = "失败", error = e.reason) }
                    } catch (_: Exception) {
                        batches = batches.toMutableList().also { it[index] = it[index].copy(state = "失败", error = AiExplanationError.SERVER) }
                    }
                }
            } finally { loading = false }
        }
    }
    MaterialQuestionFormContent(material, counts, loading, batches, fileError?.message, generationError,
        onMaterial = { material = it; state.fileName.value = ""; batches = emptyList(); config = null }, onCount = { type, value -> counts = counts + (type to value); batches = emptyList(); config = null },
        onPick = { launcher.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "application/csv")) },
        onOtherBasis = onOtherBasis, onSettings = onSettings,
        onRetry = { index -> runBatches(listOf(index)) },
        onGenerate = {
            try {
                val prepared = repository.materialConfig()
                host = repository.serviceHost(prepared)
                config = prepared
                batches = MaterialChunks.plan(material, counts).map { MaterialBatchStatus(it) }
                generationError = null
                confirm = true
            } catch (e: AiExplanationException) {
                generationError = e.reason
                if (e.reason == AiExplanationError.NOT_CONFIGURED) onSettings()
            }
        }, canReference = canReference)
    if (fileError?.needsSettings == true) LintiConfirmationSheet("尚未配置 OCR", fileError!!.message, "前往设置",
        onConfirm = onSettings, onDismiss = { fileError = null })
    if (ocrConfirm != null) LintiConfirmationSheet("在线识别扫描 PDF？",
        "整份 PDF 将上传至 DeepSeek 官方服务。识别按页计费，取消不一定停止计费；文字页保留本地提取结果。", "同意并识别",
        onDismiss = { ocrConfirm = null; viewModel.clearMaterialFile() }, onConfirm = {
            ocrConfirm = null
            scope.launch { loading = true; try { accept(viewModel.recognizeMaterialPdf()) } finally { loading = false } }
        })
    if (confirm) MaterialSendConfirmation(material.length, batches.size, host, onDismiss = { confirm = false }, onConfirm = {
        confirm = false; runBatches(batches.indices.toList())
    })
}

@Composable
internal fun MaterialSendConfirmation(chars: Int, batches: Int, host: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    LintiConfirmationSheet("确认发送材料", "约 $chars 字\n$batches 批\n服务地址：$host", "确认生成",
        onConfirm = onConfirm, onDismiss = onDismiss)
}

@Composable
internal fun MaterialQuestionFormContent(
    material: String, counts: Map<QuestionType, Int>, loading: Boolean, batches: List<MaterialBatchStatus>,
    fileError: String?, error: AiExplanationError?, onMaterial: (String) -> Unit, onCount: (QuestionType, Int) -> Unit,
    onPick: () -> Unit, onOtherBasis: () -> Unit, onSettings: () -> Unit, onRetry: (Int) -> Unit, onGenerate: () -> Unit,
    canReference: Boolean = true,
) {
    val total = counts.values.sum()
    LazyColumn(Modifier.fillMaxSize().testTag("material-form"), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOtherBasis, enabled = !loading) { Text(if (canReference) "主题 / 参照题" else "知识点 / 主题") }
            Text("从材料出题", modifier = Modifier.padding(top = 14.dp))
        } }
        item { OutlinedButton(onClick = onPick, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("选择文件") } }
        item { OutlinedTextField(material, onMaterial, Modifier.fillMaxWidth().testTag("material-text"), enabled = !loading, label = { Text("材料文字") }, minLines = 3, maxLines = 6) }
        if (fileError != null) item { Text(fileError, color = MaterialTheme.colorScheme.error) }
        item { Text("题型与数量（共 $total / 50）", style = MaterialTheme.typography.titleMedium) }
        items(QuestionType.entries) { type ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(type.label, Modifier.weight(1f).padding(top = 14.dp))
                val value = counts[type] ?: 0
                OutlinedButton(onClick = { onCount(type, value - 1) }, enabled = !loading && value > 0) { Text("−") }
                Text("$value", Modifier.padding(top = 14.dp))
                OutlinedButton(onClick = { onCount(type, value + 1) }, enabled = !loading && value < 20 && total < 50) { Text("+") }
            }
        }
        if (error != null) item { Column {
            Text(if (error == AiExplanationError.RESPONSE) "返回格式错误" else error.message, color = MaterialTheme.colorScheme.error)
            if (error.needsSettings) TextButton(onClick = onSettings) { Text("前往设置") }
        } }
        item { Button(onClick = onGenerate, enabled = !loading && material.isNotBlank() && total in 1..50, modifier = Modifier.fillMaxWidth().testTag("material-generate")) {
            if (loading) CircularProgressIndicator(strokeWidth = 2.dp)
            Text(if (loading) "处理中…" else "生成")
        } }
        items(batches.indices.toList()) { index ->
            val batch = batches[index]
            Column {
                Text("第 ${batch.batch.index} 批：${batch.state}")
                batch.error?.let { reason ->
                    Text(if (reason == AiExplanationError.RESPONSE) "返回格式错误" else reason.message, color = MaterialTheme.colorScheme.error)
                    if (reason.needsSettings) TextButton(onClick = onSettings) { Text("前往设置") }
                    TextButton(enabled = !loading, onClick = { onRetry(index) }) { Text("重试第 ${batch.batch.index} 批") }
                }
            }
        }
    }
}
