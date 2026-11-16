package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiFilterChip
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.theme.LocalLintiPalette

@Composable
fun StudySettingsScreen(viewModel: LintiViewModel, onBack: () -> Unit) {
    val masteryThreshold by viewModel.masteryThreshold.collectAsStateWithLifecycle()
    val dailyNewCards by viewModel.dailyNewCards.collectAsStateWithLifecycle()

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("练习设置", pageListState, onBack = onBack) {
        LazyColumn(
            state = pageListState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(title = "练习设置", onBack = onBack)
            }
            item {
                LintiCard(shapeRadius = 20.dp) { MasterySettingsContent(masteryThreshold, viewModel::setMasteryThreshold) }
            }
            item {
                LintiCard(shapeRadius = 20.dp) { DailyNewCardsSettingsContent(dailyNewCards, viewModel::setDailyNewCards) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MasterySettingsContent(threshold: Int?, onThresholdChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("掌握所需连对", style = MaterialTheme.typography.bodyMedium, color = LocalLintiPalette.current.textPrimary)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            (listOf<Int?>(null) + (1..5).toList()).forEach { value ->
                LintiFilterChip(
                    selected = threshold == value,
                    onClick = { onThresholdChange(value) },
                    label = value?.let { "${it} 次" } ?: "关闭",
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DailyNewCardsSettingsContent(limit: Int?, onLimitChange: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("每日新卡", style = MaterialTheme.typography.bodyMedium, color = LocalLintiPalette.current.textPrimary)
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf<Int?>(10, 20, 50, null).forEach { value ->
                LintiFilterChip(selected = limit == value, onClick = { onLimitChange(value) }, label = value?.toString() ?: "不限")
            }
        }
    }
}