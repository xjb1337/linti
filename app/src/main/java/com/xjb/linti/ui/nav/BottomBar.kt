package com.xjb.linti.ui.nav

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class BottomDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val destinations = listOf(
    BottomDestination(Routes.Home, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    BottomDestination(Routes.Banks, "题库", Icons.Filled.LibraryBooks, Icons.Outlined.LibraryBooks),
    BottomDestination(Routes.Practice, "练习", Icons.Filled.TaskAlt, Icons.Outlined.TaskAlt),
    BottomDestination(Routes.Settings, "我的", Icons.Filled.Person, Icons.Outlined.Person),
)

@Composable
fun LintiBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val palette = LocalLintiPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.line.copy(alpha = 0.65f)),
        )
        NavigationBar(
            modifier = Modifier,
            containerColor = palette.surface,
            tonalElevation = 0.dp,
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(destination.route) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label,
                        )
                    },
                    label = {
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.primary,
                        selectedTextColor = palette.primary,
                        indicatorColor = palette.primarySoft,
                        unselectedIconColor = palette.textSecondary,
                        unselectedTextColor = palette.textSecondary,
                    ),
                )
            }
        }
    }
}
