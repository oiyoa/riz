package com.riz.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.riz.app.R
import com.riz.app.data.repository.SecurityRepository
import com.riz.app.ui.components.PasswordBottomSheet
import com.riz.app.ui.components.WelcomeScreen
import com.riz.app.ui.screens.HomeScreen
import com.riz.app.util.BiometricAuth
import com.riz.app.viewmodel.FileViewModel
import com.riz.app.viewmodel.MessageViewModel

private enum class PasswordDialogMode { CREATE, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    messageViewModel: MessageViewModel,
    fileViewModel: FileViewModel,
    securityRepository: SecurityRepository,
) {
    var passwordDialog by remember { mutableStateOf<PasswordDialogMode?>(null) }
    var hasPassword by remember { mutableStateOf(securityRepository.hasPassword()) }

    val context = LocalContext.current
    val biometricTitle = stringResource(R.string.biometric_prompt_title)
    val biometricSubtitle = stringResource(R.string.biometric_prompt_subtitle)
    val biometricAvailable = remember { BiometricAuth.isAvailable(context) }

    if (passwordDialog != null) {
        PasswordBottomSheet(
            isSettings = passwordDialog == PasswordDialogMode.SETTINGS,
            isBiometricAvailable = biometricAvailable,
            onDismiss = { passwordDialog = null },
            onSave = { pwd ->
                securityRepository.setPassword(pwd)
                hasPassword = true
                passwordDialog = null
            },
            onClear = {
                securityRepository.clearPassword()
                hasPassword = false
                passwordDialog = null
            },
            onRequestReveal = { deliver ->
                val activity = context as? FragmentActivity ?: return@PasswordBottomSheet
                BiometricAuth.authenticate(
                    activity = activity,
                    title = biometricTitle,
                    subtitle = biometricSubtitle,
                    onSuccess = {
                        val current = securityRepository.getPassword().orEmpty()
                        if (current.isNotEmpty()) deliver(current)
                    },
                    onFailure = { /* user cancelled or device-credential refused */ },
                )
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Image(
                        painter = painterResource(R.drawable.ic_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.height(32.dp),
                    )
                },
                actions = {
                    if (hasPassword) {
                        IconButton(onClick = {
                            passwordDialog = PasswordDialogMode.SETTINGS
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.settings),
                            )
                        }
                    }
                },
            )
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
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "hasPasswordContent",
            ) { targetHasPassword ->
                if (!targetHasPassword) {
                    WelcomeScreen(onSetKey = {
                        passwordDialog = PasswordDialogMode.CREATE
                    })
                } else {
                    HomeScreen(
                        messageViewModel = messageViewModel,
                        fileViewModel = fileViewModel,
                    )
                }
            }
        }
    }
}
