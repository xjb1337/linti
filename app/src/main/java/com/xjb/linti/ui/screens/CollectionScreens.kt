package com.xjb.linti.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.formatDateTime
import com.xjb.linti.ui.theme.AiAccent
import com.xjb.linti.ui.theme.AiSoft
import com.xjb.linti.ui.theme.Background
import com.xjb.linti.ui.theme.Line
import com.xjb.linti.ui.theme.Primary
import com.xjb.linti.ui.theme.PrimaryPale
import com.xjb.linti.ui.theme.PrimarySoft
import com.xjb.linti.ui.theme.Surface
import com.xjb.linti.ui.theme.TextPrimary
import com.xjb.linti.ui.theme.TextSecondary
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
                    StatusPill("${filtered.size} 题", color = Primary, background = PrimaryPale)
                },
            )
        }
        item {
            LintiCard(containerColor = PrimaryPale, borderColor = PrimarySoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.BookmarkBorder,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "收藏练习",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = "只练已收藏题目，随时取消收藏不会删除题库。",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
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
                LintiCard(containerColor = Background) {
                    EmptyState(
                        title = "还没有收藏题",
                        message = "答题时点击右上角书签即可收藏。",
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
                            StatusPill(item.type.label, color = Primary, background = PrimaryPale)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = item.bankName,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "收藏于 ${formatDateTime(item.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = item.stem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "答案：" + AnswerNormalizer.displayAnswer(item.type, item.answer),
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "练这道题",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                            )
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
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
                    StatusPill("${filtered.size} 条", color = AiAccent, background = AiSoft)
                },
            )
        }
        if (filtered.isEmpty()) {
            item {
                LintiCard(containerColor = Background) {
                    EmptyState(
                        title = "还没有复习笔记",
                        message = "答题页右上角的笔记按钮可记录易错点与记忆线索。",
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
                                tint = AiAccent,
                                modifier = Modifier.size(19.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.bankName,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            StatusPill(item.type.label, color = Primary, background = PrimaryPale)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = item.stem,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AiSoft)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "更新于 ${formatDateTime(item.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
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
                    StatusPill("${results.size} 条结果", color = Primary, background = PrimaryPale)
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
                SimpleProgressBar(
                    progress = 0.55f,
                    modifier = Modifier.padding(vertical = 5.dp),
                )
            }
        }
        if (results.isEmpty() && collection?.loading != true) {
            item {
                LintiCard(containerColor = Background) {
                    EmptyState(
                        title = "没有找到匹配题目",
                        message = if (query.isBlank()) {
                            "这个题库还没有题目。"
                        } else {
                            "换一个关键词，或返回题库查看全部题目。"
                        },
                    )
                }
            }
        } else {
            item {
                SectionHeader(
                    title = if (query.isBlank()) "全部题目" else "搜索结果",
                    trailing = "点击开始单题练习",
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
                    color = TextSecondary,
                )
                Spacer(Modifier.width(7.dp))
                StatusPill(question.type.label, color = Primary, background = PrimaryPale)
                Spacer(Modifier.weight(1f))
                if (question.needsReview) {
                    StatusPill("待核对", color = com.xjb.linti.ui.theme.Warning, background = com.xjb.linti.ui.theme.WarningSoft)
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = question.stem,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (question.options.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = question.options.joinToString(" / ") { "${it.label}.${it.text}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "答案：" + AnswerNormalizer.displayAnswer(question.type, question.answer),
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = question.knowledgePoint ?: "无知识点标签",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
