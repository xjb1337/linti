package com.xjb.linti.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xjb.linti.ui.theme.LocalLintiPalette

@Composable
fun LintiGroup(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, modifier = Modifier.padding(horizontal = 4.dp))
        LintiCard(shapeRadius = 20.dp, content = content)
    }
}

@Composable
fun LintiGroupRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(modifier.fillMaxWidth().then(clickable).heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = LocalLintiPalette.current.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = LocalLintiPalette.current.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalLintiPalette.current.textSecondary)
        }
        trailing()
    }
}

@Composable
fun LintiLoadingState(title: String = "正在载入", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp,
            color = LocalLintiPalette.current.primary)
        Text(title, style = MaterialTheme.typography.bodySmall, color = LocalLintiPalette.current.textSecondary)
    }
}

@Composable
fun LintiErrorState(title: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(title, modifier = modifier, action = { OutlinedActionButton("重试", onRetry) })
}
