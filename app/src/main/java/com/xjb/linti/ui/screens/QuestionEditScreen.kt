package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.xjb.linti.domain.Chapter
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.QuestionDraftEditor
import kotlinx.coroutines.launch

@Composable
fun QuestionEditScreen(
    viewModel: LintiViewModel,
    questionId: Long,
    onBack: () -> Unit,
    newBankId: Long? = null,
) {
    var question by remember { mutableStateOf<Question?>(null) }
    var draft by remember { mutableStateOf<DraftQuestion?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var chapterId by remember { mutableStateOf<Long?>(null) }
    var chapterMenu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(questionId) {
        val current = if (newBankId != null) Question(bankId = newBankId, sourceFileName = "手动新增", createdAt = System.currentTimeMillis()) else viewModel.questionForEdit(questionId)
        question = current
        draft = current?.toDraft()
        chapterId = current?.chapterId
        chapters = current?.let { viewModel.getChapters(it.bankId) } ?: emptyList()
        loading = false
    }

    fun save() {
        val currentDraft = draft ?: return
        val original = question ?: return
        if (currentDraft.stem.isBlank()) {
            viewModel.showMessage("题干不能为空")
            return
        }
        if (currentDraft.type == QuestionType.ESSAY && currentDraft.answer.isBlank()) {
            viewModel.showMessage("请填写参考答案")
            return
        }
        if (currentDraft.type == QuestionType.FILL && currentDraft.acceptedAnswers.isNotEmpty()) {
            val blanks = Regex("_{2,}|＿{2,}|（\\s*）|\\(\\s*\\)").findAll(currentDraft.stem).count()
            if (blanks != currentDraft.acceptedAnswers.size || currentDraft.acceptedAnswers.any { it.isEmpty() || it.any(String::isBlank) }) {
                viewModel.showMessage("空数与答案数不一致，或答案为空")
                return
            }
        }
        if ((currentDraft.type == QuestionType.SINGLE || currentDraft.type == QuestionType.MULTI) &&
            currentDraft.options.isEmpty()
        ) {
            viewModel.showMessage("请至少保留一个选项")
            return
        }
        scope.launch {
            saving = true
            val saved = viewModel.saveQuestionEdit(original.merge(currentDraft).copy(chapterId = chapterId))
            saving = false
            if (saved) {
                viewModel.showMessage("已保存")
                onBack()
            } else {
                viewModel.showMessage("保存失败，请重试")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LintiPageHeader(
            title = if (newBankId != null) "新增题目" else "编辑本题",
            onBack = onBack,
            actions = {
                TextButton(enabled = draft != null && !saving, onClick = { save() }) {
                    Text("保存", color = LocalLintiPalette.current.primary)
                }
            },
        )
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", style = MaterialTheme.typography.bodyMedium, color = LocalLintiPalette.current.textSecondary)
                }
            }
            draft == null -> {
                EmptyState(
                    title = "题目不存在",
                    message = "该题可能已被删除。",
                    modifier = Modifier.padding(20.dp),
                    action = {
                        OutlinedActionButton(text = "返回", onClick = onBack)
                    },
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Box {
                        OutlinedButton(onClick = { chapterMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("章节：" + (chapters.firstOrNull { it.id == chapterId }?.name ?: "未分类"))
                        }
                        DropdownMenu(expanded = chapterMenu, onDismissRequest = { chapterMenu = false }) {
                            (listOf(null to "未分类") + chapters.map { it.id to it.name }).forEach { (id, name) ->
                                DropdownMenuItem(text = { Text(name) }, onClick = { chapterId = id; chapterMenu = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    QuestionDraftEditor(
                        draft = draft!!,
                        index = 0,
                        onChange = { draft = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryActionButton(
                        text = "保存修改",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving,
                        onClick = { save() },
                    )
                }
            }
        }
    }
}

private fun Question.toDraft(): DraftQuestion = DraftQuestion(
    sourceNumber = sourceNumber,
    type = type,
    stem = stem,
    options = options,
    answer = answer,
    explanation = explanation,
    tags = tags,
    knowledgePoint = knowledgePoint,
    confidence = confidence,
    needsReview = needsReview,
    reviewReasons = reviewReasons,
    sourceFileName = sourceFileName,
    rawSnippet = rawSnippet,
    acceptedAnswers = acceptedAnswers,
)

private fun Question.merge(draft: DraftQuestion): Question = copy(
    sourceNumber = draft.sourceNumber,
    type = draft.type,
    stem = draft.stem,
    options = if (draft.type == QuestionType.ESSAY) emptyList() else draft.options,
    answer = draft.answer,
    explanation = draft.explanation,
    tags = draft.tags,
    knowledgePoint = draft.knowledgePoint,
    confidence = draft.confidence,
    needsReview = draft.needsReview,
    reviewReasons = draft.reviewReasons,
    sourceFileName = draft.sourceFileName,
    rawSnippet = draft.rawSnippet,
    acceptedAnswers = if (draft.type == QuestionType.FILL) draft.acceptedAnswers else emptyList(),
)
