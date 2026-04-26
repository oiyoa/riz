package com.riz.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.riz.app.R
import com.riz.app.ui.components.ResultFileRow
import com.riz.app.ui.components.SelectedFileRow
import com.riz.app.viewmodel.ResultFile
import com.riz.app.viewmodel.SelectedFile

@Composable
fun EmptyFileState(onSelectFiles: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { onSelectFiles() }
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.select_files_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun SelectedFilesList(
    files: List<SelectedFile>,
    onAddFile: () -> Unit,
    onRemoveFile: (Uri) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.selected_files), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onAddFile) {
                Text(stringResource(R.string.add_file))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(files) { file ->
                val sizeStr =
                    if (file.size < 1048576) {
                        stringResource(R.string.size_kb, file.size / 1024)
                    } else {
                        stringResource(R.string.size_mb, file.size / 1048576)
                    }
                SelectedFileRow(
                    name = file.name,
                    size = sizeStr,
                    onDelete = { onRemoveFile(file.uri) },
                )
            }
        }
    }
}

@Composable
fun SplitOptionsCard(
    splitEnabled: Boolean,
    onSplitEnabledChange: (Boolean) -> Unit,
    splitSizeMB: Int,
    onSplitSizeChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = splitEnabled,
                    onCheckedChange = onSplitEnabledChange,
                )
                Text(stringResource(R.string.split_output))
            }

            AnimatedVisibility(
                visible = splitEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Slider(
                        value = splitSizeMB.toFloat(),
                        onValueChange = { onSplitSizeChange(it.toInt()) },
                        valueRange = 1f..50f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.size_mb, splitSizeMB),
                        modifier = Modifier.width(60.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
fun FileActionButtons(
    isProcessing: Boolean,
    hasFiles: Boolean,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
) {
    com.riz.app.ui.components.DualActionButtons(
        primaryText = stringResource(R.string.compress),
        primaryIcon = Icons.Outlined.Description,
        onPrimaryClick = onCompress,
        secondaryText = stringResource(R.string.extract),
        secondaryIcon = Icons.Outlined.Share,
        onSecondaryClick = onExtract,
        enabled = hasFiles,
        isProcessing = isProcessing,
    )
}

@Composable
fun ResultsList(
    results: List<ResultFile>,
    savedFileNames: Set<String>,
    onClear: () -> Unit,
    onDownload: (ResultFile) -> Unit,
) {
    val context = LocalContext.current

    Column {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.result_count, results.size, results.size),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.clear))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results) { result ->
                val saveFileTitle = stringResource(R.string.save_file)
                val sizeStr =
                    if (result.size < 1048576) {
                        stringResource(R.string.size_kb, result.size / 1024)
                    } else {
                        stringResource(R.string.size_mb, result.size / 1048576)
                    }
                ResultFileRow(
                    name = result.name,
                    fileSize = sizeStr,
                    isSaved = result.name in savedFileNames,
                    onDownload = { onDownload(result) },
                    onShare = {
                        val uri: Uri =
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                result.file,
                            )
                        val shareIntent =
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, uri)
                                type = "application/octet-stream"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(Intent.createChooser(shareIntent, saveFileTitle))
                    },
                )
            }
        }
    }
}
