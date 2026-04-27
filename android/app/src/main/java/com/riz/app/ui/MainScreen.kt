package com.riz.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riz.app.R
import com.riz.app.data.repository.SecurityRepository
import com.riz.app.ui.components.PasswordBottomSheet
import com.riz.app.ui.components.WelcomeScreen
import com.riz.app.ui.screens.FileScreen
import com.riz.app.ui.screens.MessageScreen
import com.riz.app.viewmodel.FileViewModel
import com.riz.app.viewmodel.MessageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    messageViewModel: MessageViewModel,
    fileViewModel: FileViewModel,
    securityRepository: SecurityRepository,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isSettingsDialog by remember { mutableStateOf(false) }

    var hasPassword by remember { mutableStateOf(!securityRepository.getPassword().isNullOrEmpty()) }

    if (showPasswordDialog) {
        PasswordBottomSheet(
            isSettings = isSettingsDialog,
            initialPassword = if (isSettingsDialog) securityRepository.getPassword() ?: "" else "",
            onDismiss = { showPasswordDialog = false },
            onSave = { pwd ->
                securityRepository.setPassword(pwd)
                hasPassword = true
                showPasswordDialog = false
            },
            onClear = {
                securityRepository.clearPassword()
                hasPassword = false
                showPasswordDialog = false
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                },
                actions = {
                    if (hasPassword) {
                        IconButton(
                            onClick = {
                                isSettingsDialog = true
                                showPasswordDialog = true
                            },
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = hasPassword,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(imageVector = Icons.AutoMirrored.Outlined.Message, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_text)) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(imageVector = Icons.Outlined.Description, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_file)) },
                        colors =
                            NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            AnimatedContent(
                targetState = hasPassword,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "hasPasswordContent",
            ) { targetHasPassword ->
                if (!targetHasPassword) {
                    WelcomeScreen(onSetKey = {
                        isSettingsDialog = false
                        showPasswordDialog = true
                    })
                } else {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> -width } + fadeOut(),
                                )
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                    slideOutHorizontally { width -> width } + fadeOut(),
                                )
                            }.using(
                                SizeTransform(clip = false),
                            )
                        },
                        label = "tabContent",
                    ) { targetTab ->
                        when (targetTab) {
                            0 -> MessageScreen(messageViewModel)
                            1 -> FileScreen(fileViewModel)
                        }
                    }
                }
            }
        }
    }
}
