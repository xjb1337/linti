package com.xjb.linti.ui.components

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.xjb.linti.ui.theme.LocalLintiPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LintiSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = LocalLintiPalette.current.surface,
        contentColor = LocalLintiPalette.current.textPrimary,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭") }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun <T> LintiChoiceSheet(title: String, values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    LintiSheet(title, onDismiss) {
        LintiCard(shapeRadius = 20.dp, containerColor = LocalLintiPalette.current.background) {
            Column {
                values.forEach { value ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value); onDismiss() }).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label(value), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (value == selected) Icon(Icons.Outlined.Check, contentDescription = "已选择", tint = LocalLintiPalette.current.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LintiConfirmationSheet(title: String, message: String?, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit,
    danger: Boolean = false, confirmEnabled: Boolean = true, dismissLabel: String = "取消") {
    LintiSheet(title, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!message.isNullOrBlank()) Text(message, style = MaterialTheme.typography.bodyMedium, color = LocalLintiPalette.current.textSecondary)
            if (danger) {
                Button(onClick = onConfirm, enabled = confirmEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalLintiPalette.current.error, contentColor = LocalLintiPalette.current.onPrimary)) { Text(confirmLabel) }
            } else {
                PrimaryActionButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), enabled = confirmEnabled)
            }
            OutlinedActionButton(dismissLabel, onDismiss, Modifier.fillMaxWidth())
        }
    }
}
