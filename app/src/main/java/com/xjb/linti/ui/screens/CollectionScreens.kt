package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.Question
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.LintiSearchField
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.formatDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    viewModel: LintiViewModel,
    bankId: Long?,
    onBack: () -> Unit,
    onQuiz: () -> Unit,
) {
    val favorites by viewModel.favoriteQuestions.collectAsStateWithLifecycle()
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var detailQuestionId by remember { mutableStateOf<Long?>(null) }
    val filtered = favorites.filter { bankId == null || it.bankId == bankId }
    val bankName = banks.firstOrNull { it.id == bankId }?.name

    fun startAll() {
        scope.launch {
            val result = viewModel.startFavoriteQuiz(bankId)
            if (result.started) onQuiz()
            else viewModel.showMessage(result.message ?: "无法开始收藏练习")
        }
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            LintiPageHeader(
                title = "收藏",
                subtitle = bankName ?: "全部题库",
                onBack = onBack,
                actions = {
                    StatusPill("${filtered.size} 题", color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
                },
            )
        }
        item {
            LintiCard(containerColor = LocalLintiPalette.current.primaryPale, borderColor = LocalLintiPalette.current.primarySoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = LocalLintiPalette.current.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "收藏练习",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalLintiPalette.current.textPrimary,
                        )
                    }
                    OutlinedActionButton(
                        text = "开始练习",
                        enabled = filtered.isNotEmpty(),
                        onClick = ::startAll,
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                LintiCard(containerColor = LocalLintiPalette.current.background) {
                    EmptyState(
                        title = "还没有收藏题",
                        action = {
                            OutlinedActionButton(text = "返回练习", onClick = onBack)
                        },
                    )
                }
            }
        } else {
            items(filtered, key = { "fav-${it.questionId}" }) { item ->
                LintiCard(
                    shapeRadius = 15.dp,
                    onClick = {
                        scope.launch {
                            val result = viewModel.startSingleQuestionQuiz(
                                item.questionId,
                                PracticeMode.FAVORITE,
                            )
                            if (result.started) onQuiz()
                            else viewModel.showMessage(result.message ?: "题目已不存在")
                        }
                    },
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(item.type.label, color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = item.bankName,
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.textSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "收藏于 ${formatDateTime(item.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = item.stem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalLintiPalette.current.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "答案：" + AnswerNormalizer.displayAnswer(item.type, item.answer),
                                style = MaterialTheme.typography.labelMedium,
                                color = LocalLintiPalette.current.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { detailQuestionId = item.questionId }) { Text("详情") }
                            Text(
                                text = "练这道题",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.primary,
                            )
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = LocalLintiPalette.current.primary,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    detailQuestionId?.let { id -> QuestionExplanationDetail(id, viewModel) { detailQuestionId = null } }
}

@Composable
fun NotesScreen(
    viewModel: LintiViewModel,
    bankId: Long?,
    onBack: () -> Unit,
    onQuiz: () -> Unit,
) {
    val notes by viewModel.noteQuestions.collectAsStateWithLifecycle()
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val filtered = notes.filter { bankId == null || it.bankId == bankId }
    val bankName = banks.firstOrNull { it.id == bankId }?.name

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            LintiPageHeader(
                title = "笔记",
                subtitle = bankName ?: "全部题库",
                onBack = onBack,
                actions = {
                    StatusPill("${filtered.size} 条", color = LocalLintiPalette.current.aiAccent, background = LocalLintiPalette.current.aiSoft)
                },
            )
        }
        if (filtered.isEmpty()) {
            item {
                LintiCard(containerColor = LocalLintiPalette.current.background) {
                    EmptyState(
                        title = "还没有复习笔记",
                        action = {
                            OutlinedActionButton(text = "返回练习", onClick = onBack)
                        },
                    )
                }
            }
        } else {
            items(filtered, key = { "note-${it.questionId}" }) { item ->
                LintiCard(
                    shapeRadius = 15.dp,
                    onClick = {
                        scope.launch {
                            val result = viewModel.startSingleQuestionQuiz(
                                item.questionId,
                                PracticeMode.SEQUENCE,
                            )
                            if (result.started) onQuiz()
                            else viewModel.showMessage(result.message ?: "题目已不存在")
                        }
                    },
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.EditNote,
                                contentDescription = null,
                                tint = LocalLintiPalette.current.aiAccent,
                                modifier = Modifier.size(19.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.bankName,
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.textSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            StatusPill(item.type.label, color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = item.stem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalLintiPalette.current.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(LocalLintiPalette.current.aiSoft)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "更新于 ${formatDateTime(item.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    viewModel: LintiViewModel,
    bankId: Long?,
    onBack: () -> Unit,
    onQuiz: () -> Unit,
) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val collection by viewModel.questionCollection.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val bankName = banks.firstOrNull { it.id == bankId }?.name
    val results = collection?.questions.orEmpty()

    LaunchedEffect(bankId, query) {
        if (query.isNotBlank()) delay(220)
        viewModel.searchQuestionCollection(bankId, query)
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            LintiPageHeader(
                title = "搜索题目",
                subtitle = bankName ?: "全部题库",
                onBack = onBack,
                actions = {
                    StatusPill("${results.size} 条结果", color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
                },
            )
        }
        item {
            LintiSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "搜索题干、答案、解析或知识点",
            )
        }
        if (collection?.loading == true) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                )
            }
        }
        if (results.isEmpty() && collection?.loading != true) {
            item {
                LintiCard(containerColor = LocalLintiPalette.current.background) {
                    EmptyState(
                        title = "没有找到匹配题目",
                        action = {
                            if (query.isBlank()) {
                                OutlinedActionButton(text = "返回", onClick = onBack)
                            } else {
                                OutlinedActionButton(
                                    text = "清空搜索",
                                    onClick = { query = "" },
                                )
                            }
                        },
                    )
                }
            }
        } else {
            item {
                SectionHeader(
                    title = if (query.isBlank()) "全部题目" else "搜索结果",
                )
            }
            items(results, key = { "search-${it.id}" }) { question ->
                SearchResultRow(
                    question = question,
                    onClick = {
                        scope.launch {
                            val result = viewModel.startSingleQuestionQuiz(
                                question.id,
                                PracticeMode.SEQUENCE,
                            )
                            if (result.started) onQuiz()
                            else viewModel.showMessage(result.message ?: "题目已不存在")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    question: Question,
    onClick: () -> Unit,
) {
    LintiCard(shapeRadius = 15.dp, onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = question.sourceNumber.ifBlank { "—" },
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalLintiPalette.current.textSecondary,
                )
                Spacer(Modifier.width(7.dp))
                StatusPill(question.type.label, color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
                Spacer(Modifier.weight(1f))
                if (question.needsReview) {
                    StatusPill("待核对", color = LocalLintiPalette.current.warning, background = LocalLintiPalette.current.warningSoft)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = question.stem,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalLintiPalette.current.textPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (question.options.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = question.options.joinToString(" / ") { "${it.label}.${it.text}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "答案：" + AnswerNormalizer.displayAnswer(question.type, question.answer),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalLintiPalette.current.primary,
                    modifier = Modifier.weight(1f),
                )
                question.knowledgePoint?.takeIf { it.isNotBlank() }?.let { knowledgePoint ->
                    Text(
                        text = knowledgePoint,
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
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
