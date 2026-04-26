package com.riz.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riz.app.R
import com.riz.app.ui.components.DualActionButtons

private const val MIN_BASE64_URL_LENGTH = 20
private val BASE64_URL_REGEX = Regex("^[a-zA-Z0-9-_]+$")

@Composable
fun MessageInputArea(
    inputText: String,
    onValueChange: (String) -> Unit,
    onPaste: (String) -> Unit,
    onClear: () -> Unit,
    showClear: Boolean,
) {
    val context = LocalContext.current

    Column {
        OutlinedTextField(
            value = inputText,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.message_placeholder)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp, max = 250.dp),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (clipboard.hasPrimaryClip()) {
                        val item = clipboard.primaryClip?.getItemAt(0)
                        val pasteText = item?.text?.toString()
                        if (!pasteText.isNullOrEmpty()) {
                            onPaste(pasteText)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(imageVector = Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.paste))
            }

            TextButton(
                onClick = onClear,
                enabled = showClear,
            ) {
                Icon(imageVector = Icons.Outlined.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.clear))
            }
        }
    }
}

@Composable
fun MessageOutputArea(
    outputText: String,
    visible: Boolean,
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val resultLabel = stringResource(R.string.result)
        val copiedMessage = stringResource(R.string.copied_to_clipboard)

        val copyToClipboard = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(resultLabel, outputText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }

        Column {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.result), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = copyToClipboard) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = copyToClipboard),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text = outputText,
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Left),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MessageActionButtons(
    isProcessing: Boolean,
    enabled: Boolean,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
) {
    DualActionButtons(
        primaryText = stringResource(R.string.compress),
        primaryIcon = Icons.AutoMirrored.Outlined.Message,
        onPrimaryClick = onCompress,
        secondaryText = stringResource(R.string.extract),
        secondaryIcon = Icons.Outlined.Share,
        onSecondaryClick = onExtract,
        enabled = enabled,
        isProcessing = isProcessing,
    )
}

fun isLikelyEncrypted(text: String): Boolean {
    return text.length > MIN_BASE64_URL_LENGTH && !text.contains(" ") && text.matches(BASE64_URL_REGEX)
}
