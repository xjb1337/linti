package com.xjb.linti.ui.screens

import com.xjb.linti.ui.components.LintiConfirmationSheet
import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.MultiSelectRule
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.SettingSwitchRow
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.formatScore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ExamSetupScreen(
    viewModel: LintiViewModel,
    bankId: Long,
    onBack: () -> Unit,
    onQuiz: () -> Unit,
) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val examDrafts by viewModel.examDrafts.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var config by remember(bankId) { mutableStateOf<ExamConfig?>(null) }
    var available by remember(bankId) { mutableStateOf<Map<QuestionType, Int>>(emptyMap()) }
    var countInputs by remember(bankId) { mutableStateOf<Map<QuestionType, String>>(emptyMap()) }
    var pointsInputs by remember(bankId) { mutableStateOf<Map<QuestionType, String>>(emptyMap()) }
    var passScoreText by remember(bankId) { mutableStateOf("60") }
    var starting by remember(bankId) { mutableStateOf(false) }
    var showDiscardConfirm by remember(bankId) { mutableStateOf(false) }
    val bankName = banks.firstOrNull { it.id == bankId }?.name ?: "题库"

    LaunchedEffect(bankId) {
        val setup = viewModel.examSetupData(bankId)
        available = setup.available
        countInputs = QuestionType.entries.associateWith { setup.config.countFor(it).toString() }
        pointsInputs = QuestionType.entries.associateWith { formatScore(setup.config.pointsFor(it)) }
        config = setup.config
        passScoreText = formatScore(setup.config.passScore)
    }

    val currentConfig = config
    if (currentConfig == null || examDrafts == null) {
        Column {
            LintiPageHeader(modifier = Modifier.padding(horizontal = 16.dp), title = "模拟考试", onBack = onBack)
            LintiCard(
                modifier = Modifier.padding(20.dp),
                containerColor = LocalLintiPalette.current.primaryPale,
            ) {
                EmptyState("正在准备组卷")
            }
        }
        return
    }

    val draft = examDrafts?.firstOrNull { it.bankId == bankId }
    if (draft != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(modifier = Modifier.padding(horizontal = 16.dp), title = "模拟考试", subtitle = "题库：$bankName", onBack = onBack)
            LintiCard(modifier = Modifier.padding(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("未完成的考试", style = MaterialTheme.typography.titleMedium)
                    PrimaryActionButton(
                        text = "继续考试",
                        enabled = !starting,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                starting = true
                                try {
                                    val result = viewModel.resumeExam(bankId)
                                    if (result.started) onQuiz()
                                    else viewModel.showMessage(result.message ?: "无法继续考试")
                                } finally {
                                    starting = false
                                }
                            }
                        },
                    )
                    TextButton(enabled = !starting, onClick = { showDiscardConfirm = true }) {
                        Text("放弃本场")
                    }
                }
            }
        }
        if (showDiscardConfirm) {
            LintiConfirmationSheet("放弃本场考试？", "未交卷的作答将被删除。", "放弃", danger = true, confirmEnabled = !starting,
                onDismiss = { showDiscardConfirm = false }, onConfirm = {
                    scope.launch {
                        starting = true
                        try {
                            if (viewModel.discardExamDraft(draft.draftId)) showDiscardConfirm = false
                        } finally { starting = false }
                    }
                })
        }
        return
    }

    val counts = QuestionType.entries.associateWith { type ->
        ExamInputValidation.count(countInputs[type].orEmpty(), available[type] ?: 0)
    }
    val points = QuestionType.entries.associateWith { type ->
        ExamInputValidation.points(pointsInputs[type].orEmpty())
    }
    val countsValid = counts.values.all { it != null }
    val formConfig = if (countsValid && points.values.all { it != null }) {
        QuestionType.entries.fold(currentConfig) { current, type ->
            current.withCount(type, counts.getValue(type)!!).withPoints(type, points.getValue(type)!!)
        }
    } else null
    val availableQuestionCount = if (countsValid) counts.values.sumOf { it!! } else null
    val availableTotalPoints = formConfig?.totalPoints
    val enteredPassScore = passScoreText.toDoubleOrNull()
    val invalidPassScore = enteredPassScore == null || !enteredPassScore.isFinite() ||
        enteredPassScore < 0.0 || (availableTotalPoints != null && enteredPassScore > availableTotalPoints)

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LintiPageHeader(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = "模拟考试",
            subtitle = "题库：$bankName",
            onBack = onBack,
            actions = {
                StatusPill("自定义组卷", color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (available.values.all { it == 0 }) {
                item {
                    LintiCard(containerColor = LocalLintiPalette.current.warningSoft) {
                        EmptyState(
                            title = "暂无可组卷题目",
                            action = {
                                OutlinedActionButton(text = "返回题库", onClick = onBack)
                            },
                        )
                    }
                }
            }
            item {
                SectionHeader(title = "题型与分值", trailing = "共 ${availableQuestionCount ?: "—"} 题")
            }
            item {
                LintiCard {
                    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "题量",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(78.dp),
                            )
                            Text(
                                text = "每题（分）",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(78.dp),
                            )
                        }
                        QuestionType.entries.forEach { type ->
                            ExamTypeRow(
                                type = type,
                                countText = countInputs[type].orEmpty(),
                                available = available[type] ?: 0,
                                pointsText = pointsInputs[type].orEmpty(),
                                countValid = counts[type] != null,
                                pointsValid = points[type] != null,
                                onCountChange = { input ->
                                    countInputs = countInputs + (type to input)
                                },
                                onPointsChange = { input ->
                                    pointsInputs = pointsInputs + (type to input)
                                },
                            )
                        }
                        ThinDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "试卷总分",
                                style = MaterialTheme.typography.titleMedium,
                                color = LocalLintiPalette.current.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${availableTotalPoints?.let(::formatScore) ?: "—"} 分",
                                style = MaterialTheme.typography.titleLarge,
                                color = LocalLintiPalette.current.primary,
                            )
                        }
                    }
                }
            }
            item {
                SectionHeader(title = "时间与及格线")
            }
            item {
                LintiCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "考试时长",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalLintiPalette.current.textPrimary,
                                )
                            }
                            Counter(
                                value = currentConfig.durationMinutes,
                                suffix = " 分钟",
                                min = 5,
                                max = 240,
                                step = 5,
                                onChange = {
                                    config = currentConfig.copy(durationMinutes = it)
                                },
                            )
                        }
                        ThinDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "及格分",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalLintiPalette.current.textPrimary,
                                )
                                Text(
                                    text = "满分 ${availableTotalPoints?.let(::formatScore) ?: "—"} 分",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalLintiPalette.current.textSecondary,
                                )
                            }
                            OutlinedTextField(
                                value = passScoreText,
                                onValueChange = { input ->
                                    passScoreText = input.filter { it.isDigit() || it == '.' }
                                    passScoreText.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { score ->
                                        config = currentConfig.copy(passScore = score)
                                    }
                                },
                                modifier = Modifier.width(96.dp),
                                singleLine = true,
                                isError = invalidPassScore,
                                suffix = { Text("分") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                        if (invalidPassScore) {
                            Text(
                                text = if (enteredPassScore == null || !enteredPassScore.isFinite()) {
                                    "请输入有效及格分"
                                } else {
                                    "及格分不能超过满分"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.error,
                            )
                        }
                    }
                }
            }
            item {
                SectionHeader(title = "组卷与判分规则")
            }
            item {
                LintiCard {
                    Column {
                        SettingSwitchRow(
                            title = "选项乱序",
                            checked = currentConfig.shuffleOptions,
                            onCheckedChange = {
                                config = currentConfig.copy(shuffleOptions = it)
                            },
                        )
                        ThinDivider()
                        SettingSwitchRow(
                            title = "错题优先",
                            checked = currentConfig.wrongFirst,
                            onCheckedChange = {
                                config = currentConfig.copy(wrongFirst = it)
                            },
                        )
                        ThinDivider()
                        SettingSwitchRow(
                            title = "未做优先",
                            checked = currentConfig.unansweredFirst,
                            onCheckedChange = {
                                config = currentConfig.copy(unansweredFirst = it)
                            },
                        )
                        ThinDivider()
                        SettingSwitchRow(
                            title = "答后立即显示答案",
                            checked = currentConfig.showAnswerAfterAnswer,
                            onCheckedChange = {
                                config = currentConfig.copy(showAnswerAfterAnswer = it)
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "多选计分",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MultiSelectRule.entries.forEach { rule ->
                                FilterChip(
                                    selected = currentConfig.multiSelectRule == rule,
                                    onClick = {
                                        config = currentConfig.copy(multiSelectRule = rule)
                                    },
                                    label = { Text(rule.label) },
                                )
                            }
                        }
                    }
                }
            }
        }
        Surface(color = LocalLintiPalette.current.surface, shadowElevation = 8.dp) {
            Column {
                ThinDivider()
                PrimaryActionButton(
                    text = if (starting) "正在组卷…" else "开始考试",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    enabled = !starting && formConfig != null && (availableQuestionCount ?: 0) > 0 && !invalidPassScore,
                    icon = Icons.Outlined.TaskAlt,
                    onClick = {
                        val configToStart = formConfig
                        val scoreToStart = enteredPassScore
                        if (configToStart != null && scoreToStart != null && !invalidPassScore) scope.launch {
                            starting = true
                            try {
                                val result = viewModel.startExam(bankId, configToStart.copy(passScore = scoreToStart))
                                if (result.started) {
                                    if (result.warnings.isNotEmpty()) {
                                        viewModel.showMessage(result.warnings.joinToString("；"))
                                    }
                                    onQuiz()
                                } else {
                                    try {
                                        available = viewModel.examSetupData(bankId).available
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        // Retain the original start error if refreshing fails.
                                    }
                                    viewModel.showMessage(result.message ?: "无法开始考试")
                                }
                            } finally {
                                starting = false
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ExamTypeRow(
    type: QuestionType,
    countText: String,
    available: Int,
    pointsText: String,
    countValid: Boolean,
    pointsValid: Boolean,
    onCountChange: (String) -> Unit,
    onPointsChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalLintiPalette.current.textPrimary,
                )
                Text(
                    text = "可用 $available 题",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
            OutlinedTextField(
                value = countText,
                onValueChange = onCountChange,
                modifier = Modifier.width(78.dp).semantics { contentDescription = "${type.label}题量" },
                singleLine = true,
                isError = !countValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                shape = RoundedCornerShape(10.dp),
            )
            OutlinedTextField(
                value = pointsText,
                onValueChange = onPointsChange,
                modifier = Modifier.width(78.dp).semantics { contentDescription = "${type.label}每题分值" },
                singleLine = true,
                isError = !pointsValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                shape = RoundedCornerShape(10.dp),
            )
        }
        if (!countValid || !pointsValid) {
            val errors = buildList {
                if (!countValid) add("题量需为 0～$available 的整数")
                if (!pointsValid) add("每题 0.5～10 分，间隔 0.5 分")
            }
            Text(
                text = errors.joinToString("；"),
                style = MaterialTheme.typography.labelSmall,
                color = LocalLintiPalette.current.error,
            )
        }
    }
}

@Composable
private fun Counter(
    value: Int,
    suffix: String,
    min: Int,
    max: Int,
    step: Int,
    modifier: Modifier = Modifier,
    displayValue: String = value.toString(),
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LocalLintiPalette.current.background)
            .border(1.dp, LocalLintiPalette.current.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onChange((value - step).coerceIn(min, max)) },
            enabled = value > min,
        ) {
            Icon(
                Icons.Outlined.Remove,
                contentDescription = "减少",
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = displayValue + suffix,
            style = MaterialTheme.typography.labelMedium,
            color = LocalLintiPalette.current.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(50.dp),
        )
        IconButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "增加",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
