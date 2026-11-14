package com.xjb.linti.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.Chapter
import com.xjb.linti.domain.Question
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.theme.LocalLintiPalette
import kotlinx.coroutines.launch

@Composable
fun ChapterScreen(viewModel: LintiViewModel, bankId: Long, onBack: () -> Unit) {
    val chapters by remember(bankId) { viewModel.chaptersForBank(bankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val questions by remember(bankId) { viewModel.questionsForBank(bankId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    fun perform(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { viewModel.showMessage(it.message ?: "操作失败") } }
    }
    ChapterContent(
        chapters = chapters, questions = questions, onBack = onBack,
        onCreate = { name -> perform { viewModel.createChapter(bankId, name) } },
        onRename = { id, name -> perform { viewModel.renameChapter(id, name) } },
        onDelete = { id -> perform { viewModel.deleteChapter(id) } },
        onReorder = { id, direction -> perform { viewModel.moveChapter(id, direction) } },
        onMove = { ids, target -> perform { viewModel.moveQuestions(bankId, ids, target) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterContent(
    chapters: List<Chapter>,
    questions: List<Question>,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (Long, Int) -> Unit,
    onMove: (Set<Long>, Long?) -> Unit,
) {
    var inQuestions by remember { mutableStateOf(false) }
    var currentChapter by remember { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var editing by remember { mutableStateOf(false) }
    var editingChapter by remember { mutableStateOf<Chapter?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Chapter?>(null) }
    var moveMenu by remember { mutableStateOf(false) }
    val exitQuestions = { inQuestions = false; selected = emptySet() }
    BackHandler(enabled = inQuestions && !editing) { exitQuestions() }
    BackHandler(enabled = editing) { editing = false }
    val ordered = chapters.sortedWith(compareBy(Chapter::sortOrder, Chapter::id))
    val shown = questions.filter { it.chapterId == currentChapter }
    val title = if (currentChapter == null) "未分类" else ordered.firstOrNull { it.id == currentChapter }?.name ?: "章节"

    if (editing) {
        val duplicate = ordered.any { it.id != editingChapter?.id && it.name == name.trim() }
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(
                title = if (editingChapter == null) "新建章节" else "重命名章节",
                onBack = { editing = false },
                actions = {
                    TextButton(enabled = name.isNotBlank() && !duplicate, onClick = {
                        val chapter = editingChapter
                        if (chapter == null) onCreate(name.trim()) else onRename(chapter.id, name.trim())
                        editing = false
                    }) { Text("保存") }
                },
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("章节名称") }, singleLine = true,
                isError = duplicate, supportingText = if (duplicate) ({ Text("章节名称已存在") }) else null,
                modifier = Modifier.fillMaxWidth().padding(20.dp).testTag("chapter-name"),
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stickyHeader {
            LintiPageHeader(modifier = Modifier.background(LocalLintiPalette.current.background), title = if (inQuestions) title else "章节", onBack = if (inQuestions) exitQuestions else onBack,
                actions = {
                    if (inQuestions) {
                        androidx.compose.foundation.layout.Box {
                            TextButton(enabled = selected.isNotEmpty(), onClick = { moveMenu = true }) { Text("移到…") }
                            DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                                (ordered.map { it.id to it.name } + listOf(null to "未分类")).forEach { (id, label) ->
                                    DropdownMenuItem(text = { Text(label) }, enabled = id != currentChapter, onClick = {
                                        onMove(selected, id); selected = emptySet(); moveMenu = false
                                    })
                                }
                            }
                        }
                    } else TextButton(onClick = { editingChapter = null; name = ""; editing = true }) { Text("新建") }
                })
        }
        if (inQuestions) {
            item { Text("已选 ${selected.size} 题", modifier = Modifier.padding(horizontal = 20.dp)) }
            items(shown, key = { it.id }) { question ->
                Row(modifier = Modifier.fillMaxWidth().clickable {
                    selected = if (question.id in selected) selected - question.id else selected + question.id
                }.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = question.id in selected, onCheckedChange = {
                        selected = if (it) selected + question.id else selected - question.id
                    }, modifier = Modifier.testTag("chapter-question-${question.id}"))
                    Text(question.stem, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            items(ordered, key = { it.id }) { chapter ->
                val count = questions.count { it.chapterId == chapter.id }
                LintiCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { currentChapter = chapter.id; inQuestions = true }, verticalAlignment = Alignment.CenterVertically) {
                            Text(chapter.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count 题")
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                        }
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            IconButton(enabled = chapter != ordered.first(), onClick = { onReorder(chapter.id, -1) }) { Icon(Icons.Outlined.ArrowUpward, "上移${chapter.name}") }
                            IconButton(enabled = chapter != ordered.last(), onClick = { onReorder(chapter.id, 1) }) { Icon(Icons.Outlined.ArrowDownward, "下移${chapter.name}") }
                            IconButton(onClick = { editingChapter = chapter; name = chapter.name; editing = true }) { Icon(Icons.Outlined.Edit, "重命名${chapter.name}") }
                            IconButton(onClick = { deleting = chapter }) { Icon(Icons.Outlined.DeleteOutline, "删除${chapter.name}") }
                        }
                    }
                }
            }
            item {
                LintiCard(modifier = Modifier.padding(horizontal = 16.dp), onClick = { currentChapter = null; inQuestions = true }) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("未分类", modifier = Modifier.weight(1f))
                        Text("${questions.count { it.chapterId == null }} 题")
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
    deleting?.let { chapter ->
        val count = questions.count { it.chapterId == chapter.id }
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除 ${chapter.name}？") },
            text = { Text("$count 题将回到未分类") },
            confirmButton = { TextButton(onClick = { onDelete(chapter.id); deleting = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}
