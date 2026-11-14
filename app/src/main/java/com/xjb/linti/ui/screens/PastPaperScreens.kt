package com.xjb.linti.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.PastPaper
import com.xjb.linti.domain.PastPaperItem
import com.xjb.linti.domain.PastPaperSource
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.groupPastPapersByYear
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.theme.LocalLintiPalette
import kotlinx.coroutines.launch

@Composable
fun PastPapersScreen(viewModel: LintiViewModel, bankId: Long, onBack: () -> Unit, onQuiz: () -> Unit) {
    val papers by remember(bankId) { viewModel.pastPapersForBank(bankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val questions by remember(bankId) { viewModel.questionsForBank(bankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var paperId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    val items by remember(paperId) { viewModel.pastPaperItems(paperId ?: -1L) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val paper = papers.firstOrNull { it.id == paperId }
    val scope = rememberCoroutineScope()
    fun perform(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { viewModel.showMessage(it.message ?: "操作失败") } }
    }
    BackHandler(enabled = editing || selecting || paperId != null) {
        when {
            editing -> editing = false
            selecting -> selecting = false
            else -> paperId = null
        }
    }
    when {
        editing -> PastPaperFormContent(paper, onBack = { editing = false }) { year, name, source, note ->
            perform {
                if (paper == null) viewModel.createPastPaper(bankId, year, name, source, note)
                else viewModel.updatePastPaper(paper.id, year, name, source, note)
                editing = false
            }
        }
        selecting && paper != null -> PastPaperQuestionSelectionContent(
            questions = questions.filter { q -> items.none { it.questionId == q.id } },
            onBack = { selecting = false },
            onAdd = { ids -> perform { viewModel.addQuestionsToPastPaper(paper.id, ids); selecting = false } },
        )
        paper != null -> PastPaperDetailContent(
            paper = paper, items = items, questions = questions,
            onBack = { paperId = null }, onEdit = { editing = true },
            onDelete = { perform { viewModel.deletePastPaper(paper.id); paperId = null } },
            onAdd = { selecting = true },
            onMove = { id, direction -> perform { viewModel.movePastPaperItem(paper.id, id, direction) } },
            onRemove = { id -> perform { viewModel.removePastPaperItem(paper.id, id) } },
            onNumber = { id, number -> perform { viewModel.updatePastPaperNumber(paper.id, id, number) } },
            onPractice = {
                scope.launch {
                    val result = viewModel.startPastPaperQuiz(paper.id)
                    if (result.started) onQuiz() else viewModel.showMessage(result.message ?: "无法开始练习")
                }
            },
        )
        else -> PastPaperListContent(papers, onBack, onCreate = { editing = true }, onOpen = { paperId = it })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PastPaperListContent(papers: List<PastPaper>, onBack: () -> Unit, onCreate: () -> Unit, onOpen: (Long) -> Unit) {
    val grouped = remember(papers) { groupPastPapersByYear(papers) }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stickyHeader {
            LintiPageHeader(title = "真题", onBack = onBack, modifier = Modifier.background(LocalLintiPalette.current.background),
                actions = { TextButton(onClick = onCreate) { Text("新建") } })
        }
        grouped.forEach { (year, group) ->
            item(key = "year-$year") {
                Text("${year}年", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp).testTag("paper-year-$year"))
            }
            items(group, key = { "paper-${it.id}" }) { paper ->
                LintiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("paper-${paper.id}"), onClick = { onOpen(paper.id) }) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(paper.name, style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusPill(paper.source.label)
                                Text("${paper.questionCount} 题", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PastPaperFormContent(
    paper: PastPaper?, onBack: () -> Unit,
    onSave: (Int, String, PastPaperSource, String?) -> Unit,
) {
    var year by rememberSaveable(paper?.id) { mutableStateOf(paper?.year?.toString() ?: "") }
    var name by rememberSaveable(paper?.id) { mutableStateOf(paper?.name ?: "") }
    var source by rememberSaveable(paper?.id) { mutableStateOf(paper?.source ?: PastPaperSource.UNVERIFIED) }
    var note by rememberSaveable(paper?.id) { mutableStateOf(paper?.sourceNote ?: "") }
    val validYear = year.toIntOrNull()?.takeIf { it > 0 }
    Column(modifier = Modifier.fillMaxSize()) {
        LintiPageHeader(title = if (paper == null) "新建真题卷" else "编辑真题卷", onBack = onBack,
            actions = { TextButton(enabled = validYear != null && name.isNotBlank(), onClick = {
                onSave(requireNotNull(validYear), name.trim(), source, note.trim().ifEmpty { null })
            }) { Text("保存") } })
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = year, onValueChange = { year = it.filter(Char::isDigit) }, label = { Text("年份") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("paper-form-year"))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("卷名") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("paper-form-name"))
            Text("来源", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PastPaperSource.entries.forEach { value ->
                    FilterChip(selected = source == value, onClick = { source = value }, label = { Text(value.label) },
                        modifier = Modifier.testTag("paper-source-${value.name}"))
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("来源备注") },
                modifier = Modifier.fillMaxWidth().testTag("paper-form-note"))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PastPaperDetailContent(
    paper: PastPaper, items: List<PastPaperItem>, questions: List<Question>, onBack: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit, onAdd: () -> Unit,
    onMove: (Long, Int) -> Unit, onRemove: (Long) -> Unit, onNumber: (Long, String) -> Unit,
    onPractice: () -> Unit,
) {
    var deleting by remember { mutableStateOf(false) }
    var editingNumber by remember { mutableStateOf<PastPaperItem?>(null) }
    var number by remember { mutableStateOf("") }
    val ordered = remember(items) { items.sortedWith(compareBy(PastPaperItem::position, PastPaperItem::questionId)) }
    val byId = remember(questions) { questions.associateBy { it.id } }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stickyHeader {
            LintiPageHeader(title = paper.name, onBack = onBack, modifier = Modifier.background(LocalLintiPalette.current.background), actions = {
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "编辑卷") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.DeleteOutline, "删除卷") }
            })
        }
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${paper.year}年")
                    StatusPill(paper.source.label)
                    Text("${items.size} 题")
                }
                paper.sourceNote?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = items.isNotEmpty(), onClick = onPractice) { Text("按卷练习") }
                    OutlinedButton(onClick = onAdd) { Text("加题") }
                }
            }
        }
        items(ordered, key = { it.questionId }) { item ->
            val question = byId[item.questionId]
            if (question != null) LintiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("paper-item-${item.questionId}")) {
                Column {
                    Text("${item.originalNumber}. ${question.stem}", style = MaterialTheme.typography.bodyLarge)
                    if (editingNumber?.questionId == item.questionId) {
                        val duplicate = ordered.any { it.questionId != item.questionId && it.originalNumber == number.trim() }
                        OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("原卷题号") },
                            singleLine = true, isError = duplicate, supportingText = if (duplicate) ({ Text("原卷题号已存在") }) else null,
                            modifier = Modifier.fillMaxWidth().testTag("paper-original-number"))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editingNumber = null }) { Text("取消") }
                            TextButton(enabled = number.isNotBlank() && !duplicate, onClick = {
                                onNumber(item.questionId, number.trim()); editingNumber = null
                            }) { Text("保存") }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(enabled = item != ordered.first(), onClick = { onMove(item.questionId, -1) }) { Icon(Icons.Outlined.ArrowUpward, "上移${item.originalNumber}") }
                            IconButton(enabled = item != ordered.last(), onClick = { onMove(item.questionId, 1) }) { Icon(Icons.Outlined.ArrowDownward, "下移${item.originalNumber}") }
                            IconButton(onClick = { editingNumber = item; number = item.originalNumber }) { Icon(Icons.Outlined.Edit, "编辑题号${item.originalNumber}") }
                            IconButton(onClick = { onRemove(item.questionId) }) { Icon(Icons.Outlined.RemoveCircleOutline, "移出${item.originalNumber}") }
                        }
                    }
                }
            }
        }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("删除 ${paper.name}？") },
        confirmButton = { TextButton(onClick = { onDelete(); deleting = false }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } })

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PastPaperQuestionSelectionContent(questions: List<Question>, onBack: () -> Unit, onAdd: (List<Long>) -> Unit) {
    var selected by remember { mutableStateOf<List<Long>>(emptyList()) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        stickyHeader {
            LintiPageHeader(title = "加题", onBack = onBack, modifier = Modifier.background(LocalLintiPalette.current.background), actions = {
                TextButton(enabled = selected.isNotEmpty(), onClick = { onAdd(selected) }) { Text("加入 (${selected.size})") }
            })
        }
        items(questions, key = { it.id }) { question ->
            val toggle = { selected = if (question.id in selected) selected - question.id else selected + question.id }
            Row(modifier = Modifier.fillMaxWidth().clickable { toggle() }.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = question.id in selected, onCheckedChange = { toggle() }, modifier = Modifier.testTag("paper-select-${question.id}"))
                Text(question.stem, modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
        }
    }
}
