package com.xjb.linti.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
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
import com.xjb.linti.data.aigeneration.AiDraftFields
import com.xjb.linti.data.aigeneration.AiDraftValidator
import com.xjb.linti.data.aigeneration.AiQuestionDraft
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.theme.LocalLintiPalette
import kotlinx.coroutines.launch

@Composable
fun AiQuestionScreen(viewModel: LintiViewModel, bankId: Long, onBack: () -> Unit, onSettings: () -> Unit) {
    val repository = viewModel.aiQuestionRepository
    val questions by remember(bankId) { viewModel.questionsForBank(bankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val drafts by remember(bankId) { repository.observe(bankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var review by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AiQuestionDraft?>(null) }
    var topicMode by rememberSaveable { mutableStateOf(true) }
    var topic by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var type by rememberSaveable { mutableStateOf(QuestionType.SINGLE) }
    var count by rememberSaveable { mutableStateOf(5) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AiExplanationError?>(null) }
    val scope = rememberCoroutineScope()
    val goBack = { if (editing != null) editing = null else if (review) review = false else onBack() }
    BackHandler(enabled = editing != null || review) { goBack() }
    Column(Modifier.fillMaxSize().imePadding()) {
        LintiPageHeader(title = if (editing != null) "编辑草稿" else if (review) "待校对" else "AI 出题", onBack = goBack,
            actions = { if (editing == null) TextButton(onClick = { review = !review }) {
                Text(if (review) "出题" else "待校对 (${drafts.size})")
            } })
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
            else -> AiQuestionFormContent(topicMode, topic, selected, type, count, questions, loading, error,
                onTopicMode = { topicMode = it }, onTopic = { topic = it }, onSelected = { selected = it },
                onType = { type = it }, onCount = { count = it }, onSettings = onSettings, onGenerate = {
                    if (!loading) {
                        loading = true; error = null
                        scope.launch {
                            try {
                                repository.generate(bankId, if (topicMode) topic else null,
                                    if (topicMode) emptyList() else selected.toList(), type, count)
                                review = true
                            } catch (e: AiExplanationException) {
                                error = e.reason
                                if (e.reason == AiExplanationError.NOT_CONFIGURED) {
                                    viewModel.showMessage(e.reason.message)
                                    onSettings()
                                }
                            }
                            catch (_: Exception) { viewModel.showMessage("生成失败，请重试") }
                            finally { loading = false }
                        }
                    }
                })
        }
    }
}

@Composable
internal fun AiQuestionFormContent(
    topicMode: Boolean, topic: String, selected: Set<Long>, type: QuestionType, count: Int,
    questions: List<Question>, loading: Boolean, error: AiExplanationError?,
    onTopicMode: (Boolean) -> Unit, onTopic: (String) -> Unit, onSelected: (Set<Long>) -> Unit,
    onType: (QuestionType) -> Unit, onCount: (Int) -> Unit, onSettings: () -> Unit, onGenerate: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().testTag("ai-form"), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("出题依据", style = MaterialTheme.typography.titleMedium) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = topicMode, onClick = { onTopicMode(true) }, label = { Text("知识点 / 主题") })
            FilterChip(selected = !topicMode, onClick = { onTopicMode(false) }, label = { Text("参照题目") })
        } }
        if (topicMode) item {
            OutlinedTextField(value = topic, onValueChange = onTopic, modifier = Modifier.fillMaxWidth(), label = { Text("知识点 / 主题") }, minLines = 2)
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
        if (error != null) item {
            Column {
                Text(if (error == AiExplanationError.RESPONSE) "返回格式错误" else error.message, color = MaterialTheme.colorScheme.error)
                if (error.needsSettings) TextButton(onClick = onSettings) { Text("前往设置") }
            }
        }
        item { Button(onClick = onGenerate, enabled = !loading && if (topicMode) topic.isNotBlank() else selected.size in 1..5,
            modifier = Modifier.fillMaxWidth().testTag("ai-generate")) {
            if (loading) { CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
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
    val problems = AiDraftValidator.validate(fields)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (problems.isNotEmpty()) Text(problems.joinToString("；"), color = LocalLintiPalette.current.warning)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE).forEach { type ->
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
        } else {
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
        OutlinedTextField(fields.explanation, { fields = fields.copy(explanation = it) }, Modifier.fillMaxWidth(), label = { Text("解析") }, minLines = 2)
        Button(onClick = { onSave(fields) }, modifier = Modifier.fillMaxWidth()) { Text("保存修改") }
    }
}
