package com.xjb.linti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.xjb.linti.ui.theme.LocalLintiPalette

@Composable
fun LintiChoiceCard(label: String, text: String, selected: Boolean, multiSelect: Boolean, reveal: Boolean,
    correct: Boolean, textStyle: TextStyle = MaterialTheme.typography.bodyLarge, onClick: () -> Unit) {
    val p = LocalLintiPalette.current
    val bg = when {
        reveal && correct -> p.successSoft
        reveal && selected -> p.errorSoft
        selected -> p.primarySoft
        else -> p.surface
    }
    val ink = when {
        reveal && correct -> p.success
        reveal && selected -> p.error
        selected -> p.primary
        else -> p.textSecondary
    }
    val description = buildString {
        append(label); append("，"); append(text)
        if (reveal && correct) append("，正确选项") else if (reveal && selected) append("，错误选项")
        if (selected) append("，已选")
    }
    val choice = if (multiSelect) Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { onClick() })
        else Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(bg)
        .then(choice).clearAndSetSemantics { contentDescription = description }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(p.background), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), style = textStyle, color = p.textPrimary)
        if (selected || (reveal && correct)) {
            Icon(if (reveal && selected && !correct) Icons.Outlined.Close else Icons.Outlined.Check,
                contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        }
    }
}
