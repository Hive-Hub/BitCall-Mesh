package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()
    val identity by viewModel.userIdentity.collectAsState()

    // Handle full-screen overlays first
    if (identity == null || !identity!!.isConfigured) {
        OnboardingScreen(viewModel)
        return
    }

    if (currentScreen == AppScreen.APP_LOCK && !isAppUnlocked) {
        AppLockScreen(viewModel)
        return
    }

    if (currentScreen == AppScreen.ACTIVE_CALL) {
        ActiveCallScreen(viewModel)
        return
    }

    if (currentScreen == AppScreen.CHAT_DETAIL) {
        ChatDetailScreen(viewModel)
        return
    }

    val navItems = listOf(
        NavItem(AppScreen.CHATS, "Chats", Icons.Default.Chat, "nav_chats"),
        NavItem(AppScreen.CONTACTS, "Contacts", Icons.Default.People, "nav_contacts"),
        NavItem(AppScreen.NEARBY_RADAR, "Mesh", Icons.Default.CellTower, "nav_mesh"),
        NavItem(AppScreen.AI_TOOLS, "AI Tools", Icons.Default.AutoAwesome, "nav_ai"),
        NavItem(AppScreen.SETTINGS, "Settings", Icons.Default.Settings, "nav_settings")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp

        if (isExpanded) {
            // Tablet / Foldable Expanded layout with NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    navItems.forEach { item ->
                        NavigationRailItem(
                            selected = currentScreen == item.screen,
                            onClick = { viewModel.setScreen(item.screen) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.tag)
                        )
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "BitCall • " + navItems.firstOrNull { it.screen == currentScreen }?.label.orEmpty(),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        ScreenContent(currentScreen, viewModel)
                    }
                }
            }
        } else {
            // Standard Mobile Compact layout with Bottom NavigationBar
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (currentScreen) {
                                    AppScreen.CHATS -> "BitCall Mesh"
                                    AppScreen.CONTACTS -> "Verified Contacts"
                                    AppScreen.NEARBY_RADAR -> "Bluetooth Mesh Radar"
                                    AppScreen.AI_TOOLS -> "AI Security & Speech"
                                    AppScreen.SETTINGS -> "Settings & Telemetry"
                                    else -> "BitCall"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentScreen == item.screen,
                                onClick = { viewModel.setScreen(item.screen) },
                                icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                                label = { Text(item.label, fontSize = 11.sp) },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ScreenContent(currentScreen, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(screen: AppScreen, viewModel: MainViewModel) {
    AnimatedContent(targetState = screen, label = "screen_transition") { targetScreen ->
        when (targetScreen) {
            AppScreen.CHATS -> ChatsScreen(viewModel)
            AppScreen.CONTACTS -> ContactsScreen(viewModel)
            AppScreen.NEARBY_RADAR -> NearbyRadarScreen(viewModel)
            AppScreen.AI_TOOLS -> AiAssistantScreen(viewModel)
            AppScreen.SETTINGS -> SettingsScreen(viewModel)
            else -> ChatsScreen(viewModel)
        }
    }
}

private data class NavItem(
    val screen: AppScreen,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tag: String
)
