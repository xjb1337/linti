package com.xjb.linti.ui.components

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import com.xjb.linti.ui.theme.LintiMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LintiPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    titleColor: Color = Color.Unspecified,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        if (onBack != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).clip(CircleShape).background(LocalLintiPalette.current.surface)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = LocalLintiPalette.current.textPrimary)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (titleColor == Color.Unspecified) LocalLintiPalette.current.textPrimary else titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = LocalLintiPalette.current.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onBack == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = LocalLintiPalette.current.primary,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = LocalLintiPalette.current.textSecondary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = trailingColor,
                modifier = if (onTrailingClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onTrailingClick)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                } else {
                    Modifier
                },
            )
        }
    }
}

@Composable
fun LintiCard(
    modifier: Modifier = Modifier,
    containerColor: Color = LocalLintiPalette.current.surface,
    borderColor: Color = Color.Transparent,
    shapeRadius: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(shapeRadius))
            .then(clickModifier),
        color = containerColor,
        shape = RoundedCornerShape(shapeRadius),
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.padding(15.dp)) {
            content()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalLintiPalette.current.primary,
    background: Color = LocalLintiPalette.current.primaryPale,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

data class MetricItem(
    val value: String,
    val label: String,
    val valueColor: Color = Color.Unspecified,
)

@Composable
fun MetricStrip(
    items: List<MetricItem>,
    modifier: Modifier = Modifier,
) {
    LintiCard(modifier = modifier, shapeRadius = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = item.value,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (item.valueColor == Color.Unspecified) {
                            LocalLintiPalette.current.textPrimary
                        } else {
                            item.valueColor
                        },
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                }
                if (index != items.lastIndex) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(LocalLintiPalette.current.line),
                    )
                }
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) LintiMotion.PressScale else 1f, tween(LintiMotion.PressMillis), label = "button press")
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.scale(scale).heightIn(min = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        contentPadding = contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = LocalLintiPalette.current.primary,
            contentColor = LocalLintiPalette.current.onPrimary,
            disabledContainerColor = LocalLintiPalette.current.line,
            disabledContentColor = LocalLintiPalette.current.textPrimary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun OutlinedActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) LintiMotion.PressScale else 1f, tween(LintiMotion.PressMillis), label = "button press")
    OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.scale(scale).heightIn(min = 48.dp),
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(0.dp, Color.Transparent),
        contentPadding = contentPadding,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LocalLintiPalette.current.surface,
            contentColor = LocalLintiPalette.current.textPrimary,
            disabledContentColor = LocalLintiPalette.current.textTertiary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FeatureTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    iconColor: Color = LocalLintiPalette.current.textSecondary,
    iconBackground: Color = LocalLintiPalette.current.background,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalLintiPalette.current.textPrimary,
            maxLines = 1,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = LocalLintiPalette.current.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun LintiSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = LocalLintiPalette.current.textSecondary)
        },
        singleLine = true,
        shape = RoundedCornerShape(13.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = LocalLintiPalette.current.primary,
            unfocusedIndicatorColor = LocalLintiPalette.current.line,
            focusedContainerColor = LocalLintiPalette.current.surface,
            unfocusedContainerColor = LocalLintiPalette.current.background,
            cursorColor = LocalLintiPalette.current.primary,
        ),
    )
}

@Composable
fun SimpleProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = LocalLintiPalette.current.line,
    progressColor: Color = LocalLintiPalette.current.primary,
    height: Dp = 6.dp,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(progressColor),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = LocalLintiPalette.current.textPrimary)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = LocalLintiPalette.current.textSecondary,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun WarningNote(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.labelSmall,
        color = LocalLintiPalette.current.warning,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(LocalLintiPalette.current.warningSoft)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    )
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalLintiPalette.current.line),
    )
}
