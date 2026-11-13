package com.xjb.linti.ui.nav

import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.xjb.linti.ui.theme.Line
import com.xjb.linti.ui.theme.Primary
import com.xjb.linti.ui.theme.PrimarySoft
import com.xjb.linti.ui.theme.Surface
import com.xjb.linti.ui.theme.TextSecondary

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val destinations = listOf(
    BottomDestination(Routes.Home, "首页", Icons.Outlined.Home),
    BottomDestination(Routes.Banks, "题库", Icons.Outlined.LibraryBooks),
    BottomDestination(Routes.Practice, "练习", Icons.Outlined.TaskAlt),
    BottomDestination(Routes.Settings, "我的", Icons.Outlined.Person),
)

@Composable
fun LintiBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar(
        modifier = Modifier,
        containerColor = Surface,
        tonalElevation = 0.dp,
    ) {
        destinations.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Primary,
                    selectedTextColor = Primary,
                    indicatorColor = PrimarySoft,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                ),
            )
        }
    }
}
