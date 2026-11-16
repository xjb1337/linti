package com.xjb.linti.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.xjb.linti.ui.theme.LintiMotion
import com.xjb.linti.ui.theme.LocalLintiPalette

@Composable
fun LintiChoiceCard(label: String, text: String, selected: Boolean, multiSelect: Boolean, reveal: Boolean,
    correct: Boolean, textStyle: TextStyle = MaterialTheme.typography.bodyLarge, onClick: () -> Unit) {
    val p = LocalLintiPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) LintiMotion.PressScale else 1f,
        tween(LintiMotion.PressMillis),
        label = "choice press",
    )
    val highlighted = selected || (reveal && correct)
    val targetBg = when {
        reveal && correct -> p.successSoft
        reveal && selected -> p.errorSoft
        selected -> p.primarySoft
        else -> p.surface
    }
    val targetBorder = when {
        reveal && correct -> p.success.copy(alpha = 0.7f)
        reveal && selected -> p.error.copy(alpha = 0.7f)
        selected -> p.primary.copy(alpha = 0.75f)
        else -> p.line.copy(alpha = 0.7f)
    }
    val ink = when {
        reveal && correct -> p.success
        reveal && selected -> p.error
        selected -> p.primary
        else -> p.textSecondary
    }
    val bg by animateColorAsState(targetBg, tween(LintiMotion.TransitionMillis), label = "choice bg")
    val border by animateColorAsState(targetBorder, tween(LintiMotion.TransitionMillis), label = "choice border")
    val badgeBg = if (highlighted) ink else p.surfaceContainer
    val badgeText = if (highlighted) p.surfaceLowest else ink
    val description = buildString {
        append(label); append("，"); append(text)
        if (reveal && correct) append("，正确选项") else if (reveal && selected) append("，错误选项")
        if (selected) append("，已选")
    }
    // selectable 自带合并语义（role/selected/点击），描述用非合并 semantics 叠加到同一节点，
    // 避免再引入第二个合并边界产生重复无障碍焦点
    val choice = if (multiSelect) {
        Modifier.toggleable(
            value = selected,
            interactionSource = interaction,
            indication = ripple(),
            role = Role.Checkbox,
            onValueChange = { onClick() },
        )
    } else {
        Modifier.selectable(
            selected = selected,
            interactionSource = interaction,
            indication = ripple(),
            role = Role.RadioButton,
            onClick = onClick,
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .border(width = if (highlighted) 1.5.dp else 1.dp, color = border, shape = RoundedCornerShape(15.dp))
            .then(choice)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = badgeText)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, Modifier.weight(1f), style = textStyle, color = p.textPrimary)
        if (highlighted) {
            Spacer(Modifier.width(8.dp))
            Icon(
                if (reveal && selected && !correct) Icons.Outlined.Close else Icons.Outlined.Check,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
