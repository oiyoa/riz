package com.riz.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riz.app.R
import com.riz.app.ui.asString
import com.riz.app.ui.components.ProcessingIndicator
import com.riz.app.viewmodel.FileViewModel

@Composable
fun FileScreen(viewModel: FileViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.file_saved)
    var savedFileNames by remember { mutableStateOf(setOf<String>()) }

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.setFiles(uris)
            }
        }

    val downloadLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("*/*"),
        ) { uri ->
            uri?.let {
                viewModel.saveResultFile(it)
            }
        }

    val directoryPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                viewModel.saveAllResults(it)
            }
        }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is FileViewModel.FileEvent.DownloadSuccess -> {
                    savedFileNames = savedFileNames + event.fileNames
                    snackbarHostState.showSnackbar(savedMessage)
                }
                is FileViewModel.FileEvent.Error -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
        ) {
            AnimatedContent(
                targetState = uiState.selectedFiles.isEmpty(),
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "fileListTransition",
            ) { isEmpty ->
                if (isEmpty) {
                    EmptyFileState(onSelectFiles = { filePickerLauncher.launch(arrayOf("*/*")) })
                } else {
                    SelectedFilesList(
                        files = uiState.selectedFiles,
                        onAddFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                        onRemoveFile = { viewModel.removeFile(it) },
                        onClearAll = { viewModel.clearAllFiles() },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SplitOptionsCard(
                splitEnabled = uiState.splitEnabled,
                onSplitEnabledChange = { viewModel.setSplitEnabled(it) },
                splitSizeMB = uiState.splitSizeMB,
                onSplitSizeChange = { viewModel.setSplitSize(it) },
            )

            Spacer(modifier = Modifier.weight(1f))

            ProcessingIndicator(
                visible = uiState.isProcessing,
                statusText = uiState.loadingStatus?.asString(),
                onCancel = { viewModel.cancelTask() },
            )

            FileActionButtons(
                isProcessing = uiState.isProcessing,
                hasFiles = uiState.selectedFiles.isNotEmpty(),
                onCompress = { viewModel.encryptFiles() },
                onExtract = { viewModel.decryptFiles() },
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

            AnimatedVisibility(
                visible = uiState.results.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ResultsList(
                    results = uiState.results,
                    savedFileNames = savedFileNames,
                    onClear = {
                        viewModel.clearResults()
                        savedFileNames = emptySet()
                    },
                    onDownload = {
                        viewModel.prepareDownload(it)
                        downloadLauncher.launch(it.name)
                    },
                    onSaveAll = {
                        directoryPickerLauncher.launch(null)
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}
