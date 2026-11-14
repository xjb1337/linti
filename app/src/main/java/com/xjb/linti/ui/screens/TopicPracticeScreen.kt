package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.Chapter
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.TopicPractice
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.TopicSetupData
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.theme.LocalLintiPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TopicPracticeSetupScreen(
    viewModel: LintiViewModel,
    initialBankId: Long?,
    onBack: () -> Unit,
    onQuiz: () -> Unit,
) {
    var data by remember { mutableStateOf<TopicSetupData?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialBankId, loadAttempt) {
        loadFailed = false
        data = try {
            viewModel.topicSetupData()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            loadFailed = true
            null
        }
    }

    val setup = data
    if (setup == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(title = "专项练习", onBack = onBack)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (loadFailed) {
                    EmptyState(
                        title = "无法读取题库",
                        modifier = Modifier.testTag("topic-load-failed"),
                        action = {
                            OutlinedActionButton(text = "重试", onClick = { loadAttempt++ })
                        },
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
        }
        return
    }

    TopicPracticeSetupContent(
        banks = setup.banks,
        questions = setup.questions,
        chapters = setup.chapters,
        initialBankId = initialBankId,
        onBack = onBack,
        onStart = { _, _, _ -> },
        onStartFiltered = { bankIds, types, count, chapterIds ->
            scope.launch {
                val result = viewModel.startTopicQuiz(bankIds, types, count, chapterIds = chapterIds)
                if (result.started) onQuiz()
                else viewModel.showMessage(result.message ?: "无法开始专项练习")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicPracticeSetupContent(
    banks: List<QuestionBank>,
    questions: List<Question>,
    initialBankId: Long?,
    onBack: () -> Unit,
    onStart: (bankIds: Set<Long>, types: Set<QuestionType>, count: Int) -> Unit,
    modifier: Modifier = Modifier,
    chapters: List<Chapter> = emptyList(),
    onStartFiltered: ((Set<Long>, Set<QuestionType>, Int, Set<Long?>?) -> Unit)? = null,
) {
    val defaultBankIds = remember(banks, questions, initialBankId) {
        if (initialBankId != null) {
            setOf(initialBankId).filter { id -> banks.any { it.id == id } }.toSet()
        } else {
            banks
                .filter { bank -> questions.any { it.bankId == bank.id } }
                .map { it.id }
                .toSet()
        }
    }
    var selectedBankIds by remember(banks, questions, initialBankId) {
        mutableStateOf(defaultBankIds)
    }
    var selectedTypes by remember { mutableStateOf(QuestionType.entries.toSet()) }
    var selectedCount by remember { mutableStateOf<Int?>(null) }

    val singleBankId = selectedBankIds.singleOrNull()
    val bankChapters = remember(chapters, singleBankId) {
        chapters.filter { it.bankId == singleBankId }.sortedWith(compareBy(Chapter::sortOrder, Chapter::id))
    }
    var selectedChapterIds by remember(selectedBankIds, chapters) {
        mutableStateOf<Set<Long?>>(bankChapters.map { it.id }.toSet() + setOf(null))
    }
    val chapterFilter = if (singleBankId != null) selectedChapterIds else null
    val countsByType = remember(questions, selectedBankIds, chapterFilter) {
        TopicPractice.countsByType(questions, selectedBankIds, chapterFilter)
    }
    val availableCount = selectedTypes.sumOf { countsByType[it] ?: 0 }
    val countOptions = remember(availableCount) {
        TopicPractice.countOptions(availableCount)
    }
    LaunchedEffect(countOptions) {
        if (selectedCount !in countOptions.filter { it.enabled }.map { it.count }) {
            selectedCount = TopicPractice.defaultCount(availableCount)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LintiPageHeader(
                title = "专项练习",
                onBack = onBack,
            )
        }
        item {
            LintiCard(
                containerColor = LocalLintiPalette.current.primaryPale,
                borderColor = LocalLintiPalette.current.primarySoft,
            ) {
                Text(
                    text = "可练 $availableCount 题",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalLintiPalette.current.primary,
                    modifier = Modifier.testTag("topic-total"),
                )
            }
        }
        item {
            SectionHeader(title = "题库")
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                banks.forEach { bank ->
                    FilterChip(
                        selected = bank.id in selectedBankIds,
                        onClick = {
                            selectedBankIds = if (bank.id in selectedBankIds) {
                                selectedBankIds - bank.id
                            } else {
                                selectedBankIds + bank.id
                            }
                        },
                        enabled = questions.any { it.bankId == bank.id },
                        label = {
                            Text(
                                text = bank.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.testTag("topic-bank-${bank.id}"),
                    )
                }
            }
        }
        if (singleBankId != null) {
            item { SectionHeader(title = "章节") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    (bankChapters.map { it.id to it.name } + listOf(null to "未分类")).forEach { (chapterId, name) ->
                        val count = TopicPractice.eligibleQuestions(questions, selectedBankIds, selectedTypes, setOf(chapterId)).size
                        FilterChip(
                            selected = chapterId in selectedChapterIds,
                            onClick = { selectedChapterIds = if (chapterId in selectedChapterIds) selectedChapterIds - chapterId else selectedChapterIds + chapterId },
                            enabled = count > 0,
                            label = { Text("$name $count") },
                            modifier = Modifier.testTag("topic-chapter-${chapterId ?: "uncategorized"}"),
                        )
                    }
                }
            }
        }
        item {
            SectionHeader(title = "题型")
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                QuestionType.entries.forEach { type ->
                    val count = countsByType[type] ?: 0
                    FilterChip(
                        selected = type in selectedTypes && count > 0,
                        onClick = {
                            selectedTypes = if (type in selectedTypes) {
                                selectedTypes - type
                            } else {
                                selectedTypes + type
                            }
                        },
                        enabled = count > 0,
                        label = { Text("${type.label} $count") },
                        modifier = Modifier.testTag("topic-type-${type.name}"),
                    )
                }
            }
        }
        item {
            SectionHeader(title = "题量")
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                countOptions.forEach { option ->
                    FilterChip(
                        selected = selectedCount == option.count,
                        onClick = { selectedCount = option.count },
                        enabled = option.enabled,
                        label = {
                            Text(
                                if (option.isAll) {
                                    "全部 ${option.count} 题"
                                } else {
                                    "${option.count} 题"
                                },
                            )
                        },
                        modifier = Modifier.testTag("topic-count-${option.count}"),
                    )
                }
            }
        }
        item {
            PrimaryActionButton(
                text = "开始练习",
                onClick = {
                    val count = selectedCount ?: return@PrimaryActionButton
                    if (availableCount <= 0) return@PrimaryActionButton
                    val types = selectedTypes
                        .filterTo(linkedSetOf()) { (countsByType[it] ?: 0) > 0 }
                    if (onStartFiltered != null) onStartFiltered(selectedBankIds, types, count, chapterFilter)
                    else onStart(selectedBankIds, types, count)
                },
                enabled = availableCount > 0 && selectedCount != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("topic-start"),
            )
        }
    }
}
