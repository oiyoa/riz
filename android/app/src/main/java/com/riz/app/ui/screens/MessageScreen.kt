package com.riz.app.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riz.app.crypto.Base64Url
import com.riz.app.crypto.CryptoEngine
import com.riz.app.ui.asString
import com.riz.app.ui.components.ProcessingIndicator
import com.riz.app.viewmodel.MessageViewModel

@Composable
fun MessageScreen(viewModel: MessageViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
        ) {
            MessageInputArea(
                inputText = uiState.inputText,
                onValueChange = { viewModel.onInputChanged(it) },
                onPaste = { pasteText ->
                    viewModel.onInputChanged(pasteText)
                    if (isLikelyEncrypted(pasteText)) {
                        try {
                            val decoded = Base64Url.decode(pasteText)
                            if (CryptoEngine.isValidBuffer(decoded)) {
                                viewModel.decrypt()
                            }
                        } catch (e: Exception) {
                            Log.d("MessageScreen", "Auto-decrypt failed for pasted text", e)
                        }
                    }
                },
                onClear = { viewModel.clearInput() },
                showClear = uiState.inputText.isNotEmpty() || uiState.showOutput,
            )

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                uiState.error?.let { error ->
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = error.asString(), color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            MessageOutputArea(
                outputText = uiState.outputText,
                visible = uiState.showOutput,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        ProcessingIndicator(
            visible = uiState.isProcessing,
            statusText = uiState.loadingStatus?.asString(),
            onCancel = { viewModel.cancelTask() },
        )

        MessageActionButtons(
            isProcessing = uiState.isProcessing,
            enabled = uiState.inputText.isNotEmpty(),
            onCompress = { viewModel.compress() },
            onExtract = { viewModel.decrypt() },
        )
    }
}
