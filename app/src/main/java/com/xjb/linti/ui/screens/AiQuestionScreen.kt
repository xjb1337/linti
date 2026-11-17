package com.xjb.linti.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.aigeneration.MaterialBatch
import com.xjb.linti.data.aigeneration.MaterialChunks
import com.xjb.linti.data.aigeneration.AiDraftFields
import com.xjb.linti.data.aigeneration.AiDraftValidator
import com.xjb.linti.data.aigeneration.AiQuestionDraft
import com.xjb.linti.data.aigeneration.AiQuestionTarget
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.MaterialFileResult
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.LintiChoiceSheet
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.theme.LocalLintiPalette
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

@Composable
fun AiQuestionScreen(viewModel: LintiViewModel, bankId: Long?, onBack: () -> Unit, onSettings: () -> Unit) {
    val repository = viewModel.aiQuestionRepository
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    var selectedBankId by rememberSaveable(bankId) { mutableStateOf(bankId) }
    var newBankId by rememberSaveable(bankId) { mutableStateOf<Long?>(null) }
    var createdName by rememberSaveable(bankId) { mutableStateOf("") }
    var editedName by rememberSaveable(bankId) { mutableStateOf<String?>(null) }
    val fallbackName = rememberSaveable { "AI 出题 ${LocalDate.now()}" }
    val activeBankId = selectedBankId ?: newBankId
    val questions by remember(activeBankId) { activeBankId?.let(viewModel::questionsForBank) ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val drafts by remember(activeBankId) { activeBankId?.let(repository::observe) ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var review by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AiQuestionDraft?>(null) }
    var topicMode by rememberSaveable { mutableStateOf(true) }
    var materialMode by rememberSaveable { mutableStateOf(false) }
    var topic by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var type by rememberSaveable { mutableStateOf(QuestionType.SINGLE) }
    var count by rememberSaveable { mutableStateOf(5) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AiExplanationError?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val materialState = remember { MaterialQuestionState() }
    val defaultName = defaultAiBankName(topic, materialState.material.value, materialState.fileName.value, materialMode, fallbackName)
    val requestedName = editedName?.takeIf { it.isNotBlank() } ?: defaultName
    val canReference = selectedBankId != null
    fun destination(): AiQuestionTarget = (selectedBankId ?: newBankId)?.let { AiQuestionTarget.Existing(it) } ?: AiQuestionTarget.New(requestedName)
    fun rememberCreated(target: AiQuestionTarget, id: Long) {
        if (target is AiQuestionTarget.New) { newBankId = id; createdName = target.name.trim() }
    }
    DisposableEffect(Unit) { onDispose { viewModel.clearMaterialFile() } }
    val goBack = { if (editing != null) editing = null else if (review) review = false else if (materialMode) materialMode = false else { scope.cancel(); onBack() } }
    BackHandler { goBack() }
    Column(Modifier.fillMaxSize().imePadding()) {
        LintiPageHeader(modifier = Modifier.padding(horizontal = 16.dp), title = if (editing != null) "编辑草稿" else if (review) "待校对" else "AI 出题", onBack = goBack,
            actions = { if (editing == null) TextButton(onClick = { review = !review }) {
                Text(if (review) "出题" else "待校对 (${drafts.size})")
            } })
        if (editing == null && !review) {
            AiDestinationContent(
                bankName = selectedBankId?.let { id -> banks.firstOrNull { it.id == id }?.name ?: "题库" } ?: "新建题库",
                isNew = selectedBankId == null,
                newName = if (newBankId == null) editedName ?: defaultName else createdName,
                editable = bankId == null && newBankId == null,
                locked = bankId != null,
                enabled = !loading && !materialState.loading.value,
                onName = { editedName = it },
                banks = banks.map { it.id to it.name },
                selectedBankId = selectedBankId,
                onSelect = { id -> selectedBankId = id; selected = emptySet(); if (id == null) topicMode = true },
            )
        }
        when {
            editing != null -> AiDraftEditContent(editing!!, onSave = { fields ->
                scope.launch { runCatching { repository.update(editing!!.id, fields) }
                    .onSuccess { editing = null }.onFailure { viewModel.showMessage("保存失败") } }
            })
            review -> AiDraftListContent(drafts, onEdit = { editing = it }, onApprove = { draft ->
                scope.launch { runCatching { repository.approve(draft.id) }.onFailure { viewModel.showMessage("请先修正草稿问题") } }
            }, onDiscard = { draft -> scope.launch { repository.discard(draft.id) } }, onApproveAll = {
                scope.launch { runCatching { repository.approveAll(drafts.filter { it.problems.isEmpty() }.map { it.id }) }
                    .onFailure { viewModel.showMessage("通过失败，请重试") } }
            })
            materialMode -> MaterialQuestionScreen(viewModel, materialState, scope, onSettings, onOtherBasis = { materialMode = false },
                onGenerateBatch = { batch, config ->
                    val target = destination()
                    val id = repository.generateMaterialBatch(target, batch, config)
                    rememberCreated(target, id)
                }, canReference = canReference, onOpenReview = { review = true })
            else -> AiQuestionFormContent(topicMode, topic, selected, type, count, questions, loading || materialState.loading.value, error,
                onTopicMode = { topicMode = it }, onTopic = { topic = it }, onSelected = { selected = it },
                onType = { type = it }, onCount = { count = it }, onSettings = onSettings, onGenerate = {
                    if (!loading) {
                        val target = destination()
                        loading = true; error = null; errorDetail = null
                        scope.launch {
                            try {
                                val id = repository.generate(target, if (topicMode) topic else null,
                                    if (topicMode) emptyList() else selected.toList(), type, count)
                                rememberCreated(target, id)
                                review = true
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: AiExplanationException) {
                                error = e.reason
                                errorDetail = aiErrorDetail(e.reason)
                                viewModel.showMessage(if (e.reason == AiExplanationError.RESPONSE) "生成失败：返回格式错误，请重试" else e.reason.message)
                                if (e.reason == AiExplanationError.NOT_CONFIGURED) {
                                    onSettings()
                                }
                            } catch (e: Exception) {
                                val msg = "生成失败：${e.message ?: "未知错误，请重试"}"
                                errorDetail = msg
                                viewModel.showMessage(msg)
                            } finally { loading = false }
                        }
                    }
                }, onMaterial = { materialMode = true }, canReference = canReference,
                referenceLabel = if (bankId == null) "参考已有题目" else "参照题目", errorDetail = errorDetail)
        }
    }
}

internal fun defaultAiBankName(topic: String, material: String, fileName: String, materialMode: Boolean, fallback: String): String {
    if (!materialMode) return topic.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty().take(40).ifBlank { fallback }
    if (fileName.isNotBlank()) return fileName.trim()
    val lines = material.lines().map { it.trim() }.filter { it.isNotBlank() }
    val heading = lines.firstOrNull { it.startsWith("#") || it.matches(Regex("^第.{1,12}[章节篇部].*")) }
    return (heading ?: lines.firstOrNull()).orEmpty().removePrefix("#").trimStart('#', ' ').take(40).ifBlank { fallback }
}

@Composable
internal fun AiDestinationContent(bankName: String, isNew: Boolean, newName: String, editable: Boolean, locked: Boolean,
    enabled: Boolean, onName: (String) -> Unit, banks: List<Pair<Long, String>>, selectedBankId: Long?, onSelect: (Long?) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("存到", style = MaterialTheme.typography.titleMedium)
            if (locked) Text(bankName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            else OutlinedActionButton(bankName, onClick = { showSheet = true }, modifier = Modifier.weight(1f).testTag("ai-destination"), enabled = enabled, icon = Icons.Outlined.ExpandMore)
        }
        if (isNew) OutlinedTextField(newName, onName, Modifier.fillMaxWidth().testTag("ai-bank-name"), enabled = editable && enabled,
            label = { Text("题库名称") }, singleLine = true)
    }
    if (showSheet) LintiChoiceSheet("存到", listOf(null) + banks.map { it.first }, selectedBankId,
        label = { id -> if (id == null) "新建题库" else banks.firstOrNull { it.first == id }?.second ?: "题库" },
        onSelect = onSelect, onDismiss = { showSheet = false })
}

internal fun aiErrorDetail(error: AiExplanationError): String = when (error) {
    AiExplanationError.NOT_CONFIGURED -> "请先在「AI 服务」设置中填写并保存 API Key"
    AiExplanationError.INVALID_CONFIGURATION -> "请检查 AI 接口地址与 API Key 格式是否正确"
    AiExplanationError.HTTPS_REQUIRED -> "AI 接口地址必须使用 HTTPS"
    AiExplanationError.UNAUTHORIZED -> "API Key 无效、已过期或无访问权限，请前往设置核对"
    AiExplanationError.TIMEOUT -> "请求等待超时，可减少单次生成题数或调低思考强度后重试"
    AiExplanationError.NETWORK -> "网络连接失败，请检查网络环境后重试"
    AiExplanationError.RESPONSE -> "AI 未按预期 JSON 格式返回或题数不符，可点击生成重试"
    AiExplanationError.SERVER -> "AI 服务端异常或受限（如余额不足、请求频繁），请稍后重试"
    AiExplanationError.QUESTION_MISSING -> "所选参照题目不存在"
}

@Composable
internal fun AiQuestionFormContent(
    topicMode: Boolean, topic: String, selected: Set<Long>, type: QuestionType, count: Int,
    questions: List<Question>, loading: Boolean, error: AiExplanationError?,
    onTopicMode: (Boolean) -> Unit, onTopic: (String) -> Unit, onSelected: (Set<Long>) -> Unit,
    onType: (QuestionType) -> Unit, onCount: (Int) -> Unit, onSettings: () -> Unit, onGenerate: () -> Unit,
    onMaterial: (() -> Unit)? = null,
    canReference: Boolean = true,
    referenceLabel: String = "参照题目",
    errorDetail: String? = null,
) {
    var search by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(loading) {
        elapsedSeconds = 0
        while (loading) {
            delay(1_000L)
            elapsedSeconds++
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("ai-form"), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("出题依据", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = topicMode, onClick = { onTopicMode(true) }, label = { Text("知识点 / 主题") })
            if (canReference) FilterChip(selected = !topicMode, onClick = { onTopicMode(false) }, label = { Text(referenceLabel) })
        } }
        if (onMaterial != null) item { OutlinedButton(onClick = onMaterial) { Text("从材料出题") } }
        if (topicMode) item {
            OutlinedTextField(value = topic, onValueChange = onTopic, modifier = Modifier.fillMaxWidth().testTag("ai-topic"), label = { Text("知识点 / 主题") }, minLines = 2)
        } else {
            item { OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("搜索题目") }, singleLine = true) }
            item { Text("已选 ${selected.size} / 5", style = MaterialTheme.typography.bodyMedium) }
            items(questions.filter { search.isBlank() || it.stem.contains(search, ignoreCase = true) }, key = { it.id }) { question ->
                val checked = question.id in selected
                val enabled = checked || selected.size < 5
                val toggle = { onSelected(if (checked) selected - question.id else selected + question.id) }
                Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { toggle() }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, enabled = enabled, onCheckedChange = { toggle() })
                    Text(question.stem, modifier = Modifier.weight(1f).padding(8.dp), maxLines = 3)
                }
            }
        }
        item { Text("题型", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE).forEach { option ->
                FilterChip(selected = type == option, onClick = { onType(option) }, label = { Text(option.label) })
            }
        } }
        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("数量", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(enabled = count > 1, onClick = { onCount(count - 1) }) { Text("−") }
            Text("$count", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(enabled = count < 10, onClick = { onCount(count + 1) }) { Text("+") }
        } }
        if (loading) item {
            LintiCard(
                containerColor = LocalLintiPalette.current.primaryPale,
                borderColor = LocalLintiPalette.current.primary.copy(alpha = 0.25f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "正在调用 AI 生成 $count 道${type.label}（已用时 ${elapsedSeconds}s）…",
                            style = MaterialTheme.typography.titleSmall,
                            color = LocalLintiPalette.current.primary,
                        )
                        Text(
                            text = "思考强度较高时约需 10–30 秒，请勿关闭页面",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                }
            }
        }
        if (error != null || !errorDetail.isNullOrBlank()) item {
            LintiCard(
                containerColor = LocalLintiPalette.current.errorSoft,
                borderColor = LocalLintiPalette.current.error.copy(alpha = 0.25f),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (error != null) {
                        Text(
                            text = if (error == AiExplanationError.RESPONSE) "返回格式错误" else error.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    val detail = errorDetail ?: error?.let(::aiErrorDetail)
                    if (!detail.isNullOrBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                    if (error?.needsSettings == true || error == AiExplanationError.UNAUTHORIZED) {
                        TextButton(onClick = onSettings) { Text("前往设置") }
                    }
                }
            }
        }
        item { Button(onClick = onGenerate, enabled = !loading && if (topicMode) topic.isNotBlank() else selected.size in 1..5,
            modifier = Modifier.fillMaxWidth().testTag("ai-generate")) {
            if (loading) { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(if (loading) "生成中…" else "生成")
        } }
    }
}

@Composable
internal fun AiDraftListContent(drafts: List<AiQuestionDraft>, onEdit: (AiQuestionDraft) -> Unit,
    onApprove: (AiQuestionDraft) -> Unit, onDiscard: (AiQuestionDraft) -> Unit, onApproveAll: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("ai-review"), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text("待校对 ${drafts.size} 题", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(enabled = drafts.any { it.problems.isEmpty() }, onClick = onApproveAll) { Text("通过全部可入库题目") }
        } }
        if (drafts.isEmpty()) item { Text("暂无待校对题目", color = LocalLintiPalette.current.textSecondary) }
        items(drafts, key = { it.id }) { draft ->
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(QuestionType.entries.firstOrNull { it.name == draft.fields.type }?.label ?: "题型待修正", modifier = Modifier.weight(1f))
                        StatusPill(if (draft.problems.isEmpty()) "可通过" else "待修正", color = if (draft.problems.isEmpty()) LocalLintiPalette.current.success else LocalLintiPalette.current.warning,
                            background = if (draft.problems.isEmpty()) LocalLintiPalette.current.successSoft else LocalLintiPalette.current.warningSoft)
                    }
                    Text(draft.fields.stem.ifBlank { "（题干为空）" }, style = MaterialTheme.typography.bodyLarge)
                    draft.fields.options.forEach { option ->
                        Text("${option.label}. ${option.text}", style = MaterialTheme.typography.bodyMedium)
                    }
                    val displayAnswer = if (draft.fields.type == QuestionType.JUDGE.name) {
                        com.xjb.linti.domain.AnswerNormalizer.displayAnswer(QuestionType.JUDGE, draft.fields.answer)
                    } else draft.fields.answer
                    Text("答案：" + displayAnswer.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                    if (draft.fields.explanation.isNotBlank()) Text("解析：${draft.fields.explanation}", style = MaterialTheme.typography.bodySmall)
                    if (draft.fields.material) Text("出处：${draft.fields.sourceExcerpt.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                    if (draft.problems.isNotEmpty()) Text(draft.problems.joinToString("；"), color = LocalLintiPalette.current.warning, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(draft) }) { Text("编辑") }
                        TextButton(onClick = { onDiscard(draft) }) { Text("丢弃") }
                        Spacer(Modifier.weight(1f))
                        Button(enabled = draft.problems.isEmpty(), onClick = { onApprove(draft) }) { Text("通过") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiDraftEditContent(draft: AiQuestionDraft, onSave: (AiDraftFields) -> Unit) {
    var fields by remember(draft.id) { mutableStateOf(draft.fields) }
    var acceptedText by remember(draft.id) { mutableStateOf(fields.acceptedAnswers.joinToString("\n") { it.joinToString(" | ") }) }
    val problems = AiDraftValidator.validate(fields)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (problems.isNotEmpty()) Text(problems.joinToString("；"), color = LocalLintiPalette.current.warning)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (fields.material) QuestionType.entries else listOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE)).forEach { type ->
                FilterChip(selected = fields.type == type.name, onClick = { fields = fields.copy(type = type.name) }, label = { Text(type.label) })
            }
        }
        OutlinedTextField(fields.stem, { fields = fields.copy(stem = it) }, Modifier.fillMaxWidth(), label = { Text("题干") }, minLines = 2)
        if (fields.type == QuestionType.JUDGE.name) {
            if (fields.options.isNotEmpty() || fields.invalidOptions) TextButton(onClick = { fields = fields.copy(options = emptyList(), invalidOptions = false) }) { Text("清除判断题选项") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("true" to "正确", "false" to "错误").forEach { (value, label) ->
                    FilterChip(selected = com.xjb.linti.data.import.SplitFileMatcher.strictJudge(fields.answer) == value, onClick = { fields = fields.copy(answer = value) }, label = { Text(label) })
                }
            }
        } else if (fields.type == QuestionType.SINGLE.name || fields.type == QuestionType.MULTI.name) {
            fields.options.forEachIndexed { index, option ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(option.label, { value -> fields = fields.copy(options = fields.options.toMutableList().also { it[index] = option.copy(label = value) }, invalidOptions = false) }, Modifier.width(74.dp), label = { Text("标签") }, singleLine = true)
                    OutlinedTextField(option.text, { value -> fields = fields.copy(options = fields.options.toMutableList().also { it[index] = option.copy(text = value) }, invalidOptions = false) }, Modifier.weight(1f), label = { Text("选项") })
                    TextButton(onClick = { fields = fields.copy(options = fields.options.filterIndexed { i, _ -> i != index }, invalidOptions = false) }) { Text("删除") }
                }
            }
            TextButton(onClick = { fields = fields.copy(options = fields.options + QuestionOption("", ""), invalidOptions = false) }) { Text("添加选项") }
            OutlinedTextField(fields.answer, { fields = fields.copy(answer = it) }, Modifier.fillMaxWidth(), label = { Text("答案") })
        }
        if (fields.type in listOf(QuestionType.FILL.name, QuestionType.ESSAY.name) && (fields.options.isNotEmpty() || fields.invalidOptions)) {
            TextButton(onClick = { fields = fields.copy(options = emptyList(), invalidOptions = false) }) { Text("清除选项") }
        }
        if (fields.type == QuestionType.FILL.name) {
            OutlinedTextField(acceptedText, { value ->
                acceptedText = value
                val answers = value.lines().filter { it.isNotBlank() }.map { it.split("|").map(String::trim) }
                fields = fields.copy(acceptedAnswers = answers, answer = answers.joinToString("；") { it.firstOrNull().orEmpty() })
            }, Modifier.fillMaxWidth(), label = { Text("每空一行，同义写法用 | 分隔") }, minLines = 2)
        } else if (fields.type == QuestionType.ESSAY.name) {
            OutlinedTextField(fields.answer, { fields = fields.copy(answer = it) }, Modifier.fillMaxWidth(), label = { Text("参考答案") }, minLines = 2)
        }
        if (fields.material) {
            OutlinedTextField(fields.sourceExcerpt, { fields = fields.copy(sourceExcerpt = it) }, Modifier.fillMaxWidth(), label = { Text("出处") }, minLines = 2)
            Text("材料原文：${fields.materialText}", style = MaterialTheme.typography.bodySmall)
            if (fields.generationProblem.isNotBlank()) TextButton(onClick = { fields = fields.copy(generationProblem = "") }) { Text("已核对数量问题") }
        }
        OutlinedTextField(fields.explanation, { fields = fields.copy(explanation = it) }, Modifier.fillMaxWidth(), label = { Text("解析") }, minLines = 2)
        Button(onClick = { onSave(fields) }, modifier = Modifier.fillMaxWidth()) { Text("保存修改") }
    }
}
