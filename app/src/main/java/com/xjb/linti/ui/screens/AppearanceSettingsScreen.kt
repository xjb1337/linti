package com.xjb.linti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.ColorTheme
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiChoiceSheet
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiGroupRow
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.theme.LocalLintiPalette
import com.xjb.linti.ui.theme.palette

@Composable
fun AppearanceSettingsScreen(viewModel: LintiViewModel, onBack: () -> Unit) {
    val appearanceMode by viewModel.appearanceMode.collectAsStateWithLifecycle()
    val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
    var showAppearance by remember { mutableStateOf(false) }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("外观", pageListState, onBack = onBack) {
        LazyColumn(
            state = pageListState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(title = "外观", onBack = onBack)
            }
            item {
                LintiCard(shapeRadius = 20.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LintiGroupRow(title = "显示模式", icon = Icons.Outlined.Palette,
                            onClick = { showAppearance = true }, trailing = {
                                Text(appearanceMode.label, style = MaterialTheme.typography.bodySmall, color = LocalLintiPalette.current.textSecondary)
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = LocalLintiPalette.current.textSecondary)
                            })
                        ThinDivider()
                        Text(
                            text = "配色",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                        ThemeSwatches(
                            selected = colorTheme,
                            onSelect = viewModel::setColorTheme,
                        )
                    }
                }
            }
        }
    }

    if (showAppearance) {
        LintiChoiceSheet("外观", AppearanceMode.entries, appearanceMode, { it.label },
            onSelect = viewModel::setAppearanceMode, onDismiss = { showAppearance = false })
    }
}

@Composable
private fun ThemeSwatches(
    selected: ColorTheme,
    onSelect: (ColorTheme) -> Unit,
) {
    val dark = LocalLintiPalette.current.background.luminance() < 0.5f
    // Older installs may have stored dynamic color; the static default is their visual fallback.
    val themes = ColorTheme.entries.filter { it != ColorTheme.DYNAMIC }
    val shownSelection = if (selected == ColorTheme.DYNAMIC) ColorTheme.DEFAULT else selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        themes.forEach { theme ->
            val palette = theme.palette(dark)
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(theme) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(palette.background)
                        .border(
                            width = if (shownSelection == theme) 2.dp else 1.dp,
                            color = if (shownSelection == theme) palette.primary else palette.line,
                            shape = RoundedCornerShape(13.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(palette.primary),
                    )
                    if (shownSelection == theme) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = palette.onPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = theme.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (shownSelection == theme) {
                        LocalLintiPalette.current.primary
                    } else {
                        LocalLintiPalette.current.textSecondary
                    },
                    maxLines = 1,
                )
            }
        }
    }
}