package com.riz.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.riz.app.R
import com.riz.app.crypto.RizDetector
import com.riz.app.ui.asString
import com.riz.app.ui.components.FileListContainer
import com.riz.app.ui.components.PrimaryActionRow
import com.riz.app.ui.components.ProcessingIndicator
import com.riz.app.ui.components.ResultFileRow
import com.riz.app.ui.components.SelectedFileRow
import com.riz.app.ui.components.SuggestedActionRow
import com.riz.app.ui.formatCreatedAt
import com.riz.app.viewmodel.FileUiState
import com.riz.app.viewmodel.FileViewModel
import com.riz.app.viewmodel.MessageUiState
import com.riz.app.viewmodel.MessageViewModel
import com.riz.app.viewmodel.ResultFile
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

// Drives the textfield resize, the action-row shrink/expand, and the output
// card's size transform during compress/extract transitions. Sharing one
// duration keeps the textfield collapse, button fade-out, and result reveal
// in lockstep so nothing snaps ahead of the rest.
private const val MESSAGE_TRANSITION_MS = 280
private const val MESSAGE_FADE_MS = 220

// Fixed textfield height once compress/extract is running or a result is shown
// — keeps the input visible above the result card without dominating the view.
private val MESSAGE_TEXTFIELD_BUSY_HEIGHT = 180.dp

// Space reserved at the bottom of the text body for the action area. The
// empty state stacks two affordances (Paste chip + Choose-files button) so it
// needs more room than the typing state, which only carries a single
// SuggestedActionRow. Sized tightly to each so the action sits flush near the
// bottom in both — no orphan gap when the user starts typing.
private val MESSAGE_TEXTFIELD_RESERVE_EMPTY = 132.dp
private val MESSAGE_TEXTFIELD_RESERVE_INPUT = 80.dp

private val SCROLLBAR_THICKNESS = 3.dp
private val SCROLLBAR_TOP_CLEAR_OFFSET = 48.dp
private const val SCROLLBAR_ALPHA = 0.4f
private const val SCROLL_THUMB_MIN_FRACTION = 0.15f

// Caps the result-card text area so very long ciphertext / extracted text
// doesn't push the Copy/Share actions off the bottom of the screen.
private val MESSAGE_OUTPUT_TEXT_MAX_HEIGHT = 220.dp

// Crossfade durations for the two top-level state changes. INTAKE_MODE is a
// bigger swap (text composer ↔ file list) so it gets the longer fade; the
// in-mode sub-state shuffle (ready ↔ processing ↔ done) is snappier.
private const val INTAKE_MODE_FADE_MS = 220
private const val FILE_SUBSTATE_FADE_MS = 180

private enum class IntakeMode { TEXT, FILES }

@Composable
fun HomeScreen(
    messageViewModel: MessageViewModel,
    fileViewModel: FileViewModel,
) {
    val context = LocalContext.current
    val activity = remember(context) { findFragmentActivity(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    var isDragOver by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var savedFileNames by remember { mutableStateOf(setOf<String>()) }

    val msgUi by messageViewModel.uiState.collectAsState()
    val fileUi by fileViewModel.uiState.collectAsState()

    // Mode is derived from content, not picked by the user. The moment any
    // file content exists (selected / processing / result), the screen is in
    // FILES mode; otherwise it's TEXT mode (which also covers the empty
    // launch state with placeholder + chips).
    val mode =
        if (
            fileUi.selectedFiles.isNotEmpty() ||
            fileUi.results.isNotEmpty() ||
            fileUi.isProcessing
        ) {
            IntakeMode.FILES
        } else {
            IntakeMode.TEXT
        }

    val noAppMessage = stringResource(R.string.no_app_to_open)
    val savedMessage = stringResource(R.string.file_saved)
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    val clearedTextMsg = stringResource(R.string.cleared_message_for_files)
    val clearedFilesMsg = stringResource(R.string.cleared_files_for_message)

    LaunchedEffect(fileViewModel.events) {
        fileViewModel.events.collect { event ->
            when (event) {
                is FileViewModel.FileEvent.DownloadSuccess -> {
                    savedFileNames = savedFileNames + event.fileNames
                    snackbarHostState.showSnackbar(savedMessage)
                }
                is FileViewModel.FileEvent.Error -> snackbarHostState.showSnackbar(event.message)
                is FileViewModel.FileEvent.Completed -> Unit
            }
        }
    }
    LaunchedEffect(fileUi.results.isEmpty()) {
        if (fileUi.results.isEmpty()) savedFileNames = emptySet()
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) fileViewModel.setFiles(uris)
        }
    val addFilesLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) fileViewModel.addFiles(uris)
        }
    val downloadLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            uri?.let { fileViewModel.saveResultFile(it) }
        }
    val directoryPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { fileViewModel.saveAllResults(it) }
        }

    val onAttachClick: () -> Unit = {
        // Cross-mode clear: if the user is mid-compose and now wants to switch
        // to files, drop the text so the new mode starts clean. Snackbar makes
        // the swap explicit since the keyboard may have been covering the field.
        if (messageViewModel.uiState.value.inputText.isNotEmpty()) {
            messageViewModel.clearInput()
            scope.launch { snackbarHostState.showSnackbar(clearedTextMsg) }
        }
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    val onPasteFromClipboard: (String) -> Unit = { pasted ->
        // Symmetric clear: switching from a file selection back to text by
        // hitting Paste shouldn't leave stale files lingering off-screen.
        if (fileViewModel.uiState.value.selectedFiles.isNotEmpty()) {
            fileViewModel.clearAllFiles()
            scope.launch { snackbarHostState.showSnackbar(clearedFilesMsg) }
        }
        messageViewModel.onInputPasted(pasted)
    }

    val dndTarget =
        remember(activity, fileViewModel, messageViewModel) {
            HomeDropTarget(
                activity = activity,
                messageViewModel = messageViewModel,
                fileViewModel = fileViewModel,
                onDragStateChange = { isDragOver = it },
            )
        }

    LaunchedEffect(Unit) {
        fileViewModel.shareIntake.collect { /* mode flips automatically once files land */ }
    }
    LaunchedEffect(Unit) {
        messageViewModel.shareIntake.collect { /* same — derived mode catches up */ }
    }

    BackHandler(
        enabled = msgUi.hasContent() || fileUi.hasContent(),
    ) {
        when {
            msgUi.isProcessing -> messageViewModel.cancelTask()
            fileUi.isProcessing -> fileViewModel.cancelTask()
            msgUi.showOutput -> messageViewModel.editInput()
            fileUi.results.isNotEmpty() -> fileViewModel.goBackToSelection()
            fileUi.selectedFiles.isNotEmpty() -> fileViewModel.clearAllFiles()
            msgUi.inputText.isNotEmpty() -> messageViewModel.clearInput()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event -> event.mimeTypes().isNotEmpty() },
                    target = dndTarget,
                ),
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(INTAKE_MODE_FADE_MS)) togetherWith fadeOut(tween(INTAKE_MODE_FADE_MS)))
                    .using(SizeTransform(clip = false))
            },
            label = "intakeMode",
            modifier = Modifier.fillMaxSize(),
        ) { currentMode ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp, bottom = 16.dp),
            ) {
                when (currentMode) {
                    IntakeMode.TEXT ->
                        TextMode(
                            ui = msgUi,
                            onInputChanged = messageViewModel::onInputChanged,
                            onClear = messageViewModel::clearInput,
                            onPaste = onPasteFromClipboard,
                            onAttach = onAttachClick,
                            onCompress = messageViewModel::compress,
                            onExtract = messageViewModel::decrypt,
                            onCancel = messageViewModel::cancelTask,
                            onDismissOutput = messageViewModel::editInput,
                            onCopy = { copyToClipboard(context, msgUi.outputText, copiedMessage) },
                            onShare = { shareText(context, msgUi.outputText) },
                        )
                    IntakeMode.FILES ->
                        FilesMode(
                            ui = fileUi,
                            savedFileNames = savedFileNames,
                            onAddFile = { addFilesLauncher.launch(arrayOf("*/*")) },
                            onRemoveFile = { fileViewModel.removeFile(it) },
                            onClearAll = { fileViewModel.clearAllFiles() },
                            onCompress = { fileViewModel.encryptFiles() },
                            onExtract = { fileViewModel.decryptFiles() },
                            onCancel = { fileViewModel.cancelTask() },
                            onSplitEnabledChange = { fileViewModel.setSplitEnabled(it) },
                            onSplitSizeChange = { fileViewModel.setSplitSize(it) },
                            onDownload = {
                                fileViewModel.prepareDownload(it)
                                downloadLauncher.launch(it.name)
                            },
                            onSaveAll = { directoryPickerLauncher.launch(null) },
                            onShareAll = { shareAllFiles(context, fileUi.results.map { it.file }) },
                            onOpenError = {
                                scope.launch { snackbarHostState.showSnackbar(noAppMessage) }
                            },
                            onBackToSelection = { fileViewModel.goBackToSelection() },
                        )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
        )

        AnimatedVisibility(
            visible = isDragOver,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            DragDropOverlay()
        }
    }
}

private fun MessageUiState.hasContent(): Boolean = inputText.isNotEmpty() || isProcessing || showOutput

private fun FileUiState.hasContent(): Boolean = selectedFiles.isNotEmpty() || isProcessing || results.isNotEmpty()

@Composable
private fun DragDropOverlay() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = MaterialTheme.shapes.large,
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.large,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.drop_to_add),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun findFragmentActivity(context: Context): FragmentActivity? {
    var ctx: Context = context
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// dndTarget routes drop content to whichever VM matches the payload, and
// applies the cross-mode clear so the screen never holds both a text input
// and a file selection at the same time.
private class HomeDropTarget(
    private val activity: FragmentActivity?,
    private val messageViewModel: MessageViewModel,
    private val fileViewModel: FileViewModel,
    private val onDragStateChange: (Boolean) -> Unit,
) : DragAndDropTarget {
    override fun onEntered(event: DragAndDropEvent) {
        onDragStateChange(true)
    }

    override fun onExited(event: DragAndDropEvent) {
        onDragStateChange(false)
    }

    override fun onEnded(event: DragAndDropEvent) {
        onDragStateChange(false)
    }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        onDragStateChange(false)
        val androidEvent = event.toAndroidDragEvent()
        val clip = androidEvent.clipData?.takeIf { it.itemCount > 0 } ?: return false

        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        if (uris.isNotEmpty()) {
            activity?.requestDragAndDropPermissions(androidEvent)
            if (messageViewModel.uiState.value.inputText.isNotEmpty()) {
                messageViewModel.clearInput()
            }
            fileViewModel.ingestSharedFiles(uris)
            return true
        }

        val text =
            (0 until clip.itemCount)
                .firstNotNullOfOrNull { clip.getItemAt(it).text?.toString() }
                ?.takeIf { it.isNotBlank() }
                ?: return false

        if (fileViewModel.uiState.value.selectedFiles.isNotEmpty()) {
            fileViewModel.clearAllFiles()
        }
        messageViewModel.ingestSharedText(text)
        return true
    }
}

// -------------------- Text mode --------------------

@Composable
@Suppress("LongParameterList")
private fun ColumnScope.TextMode(
    ui: MessageUiState,
    onInputChanged: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: (String) -> Unit,
    onAttach: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onCancel: () -> Unit,
    onDismissOutput: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val hasOutput = ui.showOutput
    val isProcessing = ui.isProcessing
    val isExtractMode = ui.detection != RizDetector.Result.NotRiz

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f),
    ) {
        val idleReserve =
            if (ui.inputText.isEmpty()) {
                MESSAGE_TEXTFIELD_RESERVE_EMPTY
            } else {
                MESSAGE_TEXTFIELD_RESERVE_INPUT
            }
        val idleHeight =
            (maxHeight - idleReserve)
                .coerceAtLeast(MESSAGE_TEXTFIELD_BUSY_HEIGHT)
        val targetHeight =
            if (hasOutput || isProcessing) MESSAGE_TEXTFIELD_BUSY_HEIGHT else idleHeight
        val animatedHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = tween(MESSAGE_TRANSITION_MS),
            label = "messageTextFieldHeight",
        )

        Column(modifier = Modifier.fillMaxSize()) {
            HomeTextField(
                value = ui.inputText,
                onValueChange = onInputChanged,
                placeholder = stringResource(R.string.message_placeholder),
                onClear = onClear,
                modifier = Modifier.fillMaxWidth().height(animatedHeight),
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = !isProcessing && !hasOutput,
                enter =
                    expandVertically(tween(MESSAGE_TRANSITION_MS)) +
                        fadeIn(tween(MESSAGE_FADE_MS)),
                exit =
                    shrinkVertically(tween(MESSAGE_TRANSITION_MS)) +
                        fadeOut(tween(MESSAGE_FADE_MS)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ErrorRow(ui.error?.asString())
                    Spacer(modifier = Modifier.height(8.dp))
                    if (ui.inputText.isEmpty()) {
                        EmptyActions(
                            onPaste = onPaste,
                            onChooseFiles = onAttach,
                        )
                    } else {
                        SuggestedActionRow(
                            isExtractSuggested = isExtractMode,
                            isDetecting = ui.isDetecting,
                            extractLabel = stringResource(R.string.extract_btn),
                            compressLabel = stringResource(R.string.compress_btn),
                            onExtract = onExtract,
                            onCompress = onCompress,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState =
                    when {
                        isProcessing -> MessageOutputState.PROCESSING
                        hasOutput -> MessageOutputState.DONE
                        else -> MessageOutputState.IDLE
                    },
                transitionSpec = {
                    (fadeIn(tween(MESSAGE_FADE_MS)) togetherWith fadeOut(tween(MESSAGE_FADE_MS)))
                        .using(SizeTransform(clip = false) { _, _ -> tween(MESSAGE_TRANSITION_MS) })
                },
                label = "messageOutput",
                modifier = Modifier.fillMaxWidth(),
            ) { outputState ->
                when (outputState) {
                    MessageOutputState.IDLE -> Spacer(Modifier.height(0.dp))
                    MessageOutputState.PROCESSING ->
                        InlineProcessingCard(
                            statusText = stringResource(R.string.working_on_message),
                            onCancel = onCancel,
                        )
                    MessageOutputState.DONE ->
                        MessageOutputCard(
                            text = ui.outputText,
                            isExtract = isExtractMode,
                            createdAt = ui.outputCreatedAt,
                            onCopy = onCopy,
                            onShare = onShare,
                            onDismiss = onDismissOutput,
                        )
                }
            }
        }
    }
}

private enum class MessageOutputState { IDLE, PROCESSING, DONE }

@Composable
private fun MessageOutputCard(
    text: String,
    isExtract: Boolean,
    createdAt: Long?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutputCardHeader(
                title =
                    stringResource(
                        if (isExtract) R.string.result_extracted else R.string.result_compressed,
                    ),
                subtitle =
                    when {
                        isExtract && createdAt != null -> formatCreatedAt(createdAt)
                        else -> null
                    },
                onDismiss = onDismiss,
            )

            Spacer(Modifier.height(12.dp))

            val resultScrollState = rememberScrollState()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
            ) {
                Box(modifier = Modifier.heightIn(max = MESSAGE_OUTPUT_TEXT_MAX_HEIGHT)) {
                    SelectionContainer {
                        Text(
                            text = text,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(resultScrollState)
                                    .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    VerticalScrollbarIndicator(
                        scrollState = resultScrollState,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                                .width(SCROLLBAR_THICKNESS)
                                .fillMaxHeight(),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Compress shows Copy + Share (commonly used for sending the packet).
            // Extract shows Copy only — share-sheet on plaintext is a foot-gun.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutputIconButton(
                    icon = Icons.Outlined.ContentCopy,
                    label = stringResource(R.string.copy),
                    onClick = onCopy,
                )
                if (!isExtract) {
                    Spacer(Modifier.width(4.dp))
                    OutputIconButton(
                        icon = Icons.Outlined.Share,
                        label = stringResource(R.string.share),
                        onClick = onShare,
                    )
                }
            }
        }
    }
}

// -------------------- Files mode --------------------

@Composable
@Suppress("LongParameterList")
private fun FilesMode(
    ui: FileUiState,
    savedFileNames: Set<String>,
    onAddFile: () -> Unit,
    onRemoveFile: (Uri) -> Unit,
    onClearAll: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onCancel: () -> Unit,
    onSplitEnabledChange: (Boolean) -> Unit,
    onSplitSizeChange: (Int) -> Unit,
    onDownload: (ResultFile) -> Unit,
    onSaveAll: () -> Unit,
    onShareAll: () -> Unit,
    onOpenError: () -> Unit,
    onBackToSelection: () -> Unit,
) {
    val hasResults = ui.results.isNotEmpty()
    val isProcessing = ui.isProcessing

    val bodyState =
        when {
            isProcessing -> FileBodyState.PROCESSING
            hasResults -> FileBodyState.DONE
            else -> FileBodyState.READY
        }

    AnimatedContent(
        targetState = bodyState,
        transitionSpec = {
            (fadeIn(tween(FILE_SUBSTATE_FADE_MS)) togetherWith fadeOut(tween(FILE_SUBSTATE_FADE_MS)))
                .using(SizeTransform(clip = false))
        },
        label = "fileBodyState",
        modifier = Modifier.fillMaxSize(),
    ) { state ->
        Column(modifier = Modifier.fillMaxSize()) {
            when (state) {
                FileBodyState.READY ->
                    FileReadyPanel(
                        ui = ui,
                        onAddFile = onAddFile,
                        onRemoveFile = onRemoveFile,
                        onClearAll = onClearAll,
                        onCompress = onCompress,
                        onExtract = onExtract,
                        onSplitEnabledChange = onSplitEnabledChange,
                        onSplitSizeChange = onSplitSizeChange,
                    )
                FileBodyState.PROCESSING ->
                    InlineProcessingCard(
                        statusText =
                            pluralStringResource(
                                R.plurals.working_on_n_files,
                                ui.selectedFiles.size,
                                ui.selectedFiles.size,
                            ),
                        onCancel = onCancel,
                    )
                FileBodyState.DONE ->
                    FileDonePanel(
                        results = ui.results,
                        isExtractMode = ui.resultsAreExtract,
                        savedFileNames = savedFileNames,
                        onDownload = onDownload,
                        onSaveAll = onSaveAll,
                        onShareAll = onShareAll,
                        onOpenError = onOpenError,
                        onBackToSelection = onBackToSelection,
                    )
            }
        }
    }
}

private enum class FileBodyState { READY, PROCESSING, DONE }

@Composable
@Suppress("LongParameterList")
private fun ColumnScope.FileReadyPanel(
    ui: FileUiState,
    onAddFile: () -> Unit,
    onRemoveFile: (Uri) -> Unit,
    onClearAll: () -> Unit,
    onCompress: () -> Unit,
    onExtract: () -> Unit,
    onSplitEnabledChange: (Boolean) -> Unit,
    onSplitSizeChange: (Int) -> Unit,
) {
    val isExtractMode =
        ui.selectedFiles.isNotEmpty() &&
            ui.detection != RizDetector.Result.NotRiz

    FileSelectionHeader(
        canClear = ui.selectedFiles.size > 1,
        onAddFile = onAddFile,
        onClearAll = onClearAll,
    )
    Spacer(modifier = Modifier.height(8.dp))
    FileListContainer(
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        items(ui.selectedFiles) { file ->
            SelectedFileRow(
                name = file.name,
                size = formatFileSize(file.size),
                onDelete = { onRemoveFile(file.uri) },
            )
        }
    }

    if (!isExtractMode) {
        Spacer(modifier = Modifier.height(8.dp))
        SplitToggleRow(
            enabled = ui.splitEnabled,
            onEnabledChange = onSplitEnabledChange,
            sizeMB = ui.splitSizeMB,
            onSizeChange = onSplitSizeChange,
        )
    }

    ErrorRow(ui.error?.asString())

    Spacer(modifier = Modifier.height(8.dp))

    SuggestedActionRow(
        isExtractSuggested = isExtractMode,
        isDetecting = ui.isDetecting,
        extractLabel = stringResource(R.string.extract_btn),
        compressLabel = stringResource(R.string.compress_btn),
        onExtract = onExtract,
        onCompress = onCompress,
    )
}

@Composable
private fun FileSelectionHeader(
    canClear: Boolean,
    onAddFile: () -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.selected_files),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canClear) {
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.clear))
                }
            }
            TextButton(onClick = onAddFile) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.add_file))
            }
        }
    }
}

@Composable
private fun SplitToggleRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    sizeMB: Int,
    onSizeChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.advanced_options),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onEnabledChange(!enabled) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.split_for_messaging),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = onEnabledChange,
                    )
                }
                AnimatedVisibility(
                    visible = enabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    ) {
                        Slider(
                            value = sizeMB.toFloat(),
                            onValueChange = { onSizeChange(it.toInt()) },
                            valueRange = 1f..50f,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.size_mb, sizeMB),
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ColumnScope.FileDonePanel(
    results: List<ResultFile>,
    isExtractMode: Boolean,
    savedFileNames: Set<String>,
    onDownload: (ResultFile) -> Unit,
    onSaveAll: () -> Unit,
    onShareAll: () -> Unit,
    onOpenError: () -> Unit,
    onBackToSelection: () -> Unit,
) {
    val context = LocalContext.current
    val totalSizeStr = formatFileSize(results.sumOf { it.size })

    // Header sits at the home-padding level — no wrapping tonal card. Multi-file
    // results read as a list, not as a sealed packet. Matches READY-state layout
    // (header + FileListContainer + action row) so DONE feels like the same screen
    // with a different list, not a different container.
    OutputCardHeader(
        title =
            stringResource(
                if (isExtractMode) R.string.result_extracted else R.string.result_compressed,
            ),
        subtitle =
            if (isExtractMode) {
                pluralStringResource(
                    R.plurals.result_files_extracted_desc,
                    results.size,
                    results.size,
                    totalSizeStr,
                )
            } else {
                pluralStringResource(
                    R.plurals.result_files_compressed_desc,
                    results.size,
                    results.size,
                    totalSizeStr,
                )
            },
        onDismiss = onBackToSelection,
    )

    Spacer(modifier = Modifier.height(12.dp))

    FileListContainer(
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) {
        items(results) { result ->
            ResultFileRow(
                name = result.name,
                fileSize = formatFileSize(result.size),
                isExtractMode = isExtractMode,
                isSaved = result.name in savedFileNames,
                createdAt = result.createdAt.takeIf { isExtractMode },
                onOpen = {
                    if (!openResultFile(context, result.file)) onOpenError()
                },
                onDownload = { onDownload(result) },
                onShare = { shareSingleFile(context, result.file) },
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    FileDoneActions(
        results = results,
        isExtractMode = isExtractMode,
        onDownload = onDownload,
        onSaveAll = onSaveAll,
        onShareAll = onShareAll,
        onOpenError = onOpenError,
    )
}

@Composable
@Suppress("LongParameterList")
private fun FileDoneActions(
    results: List<ResultFile>,
    isExtractMode: Boolean,
    onDownload: (ResultFile) -> Unit,
    onSaveAll: () -> Unit,
    onShareAll: () -> Unit,
    onOpenError: () -> Unit,
) {
    val context = LocalContext.current
    when {
        isExtractMode && results.size == 1 -> {
            val singleFile = results.first()
            PrimaryActionRow(
                primaryLabel = stringResource(R.string.open),
                primaryIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                onPrimary = { if (!openResultFile(context, singleFile.file)) onOpenError() },
                secondaryLabel = stringResource(R.string.save),
                secondaryIcon = Icons.Outlined.FileDownload,
                onSecondary = { onDownload(singleFile) },
            )
        }
        isExtractMode -> {
            Button(
                onClick = onSaveAll,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save_all_to_folder))
            }
        }
        else -> {
            val onSecondary: () -> Unit =
                if (results.size == 1) {
                    { onDownload(results.first()) }
                } else {
                    onSaveAll
                }
            PrimaryActionRow(
                primaryLabel = stringResource(R.string.share),
                primaryIcon = Icons.Outlined.Share,
                onPrimary = onShareAll,
                secondaryLabel =
                    if (results.size == 1) {
                        stringResource(R.string.save)
                    } else {
                        stringResource(R.string.save_all)
                    },
                secondaryIcon = Icons.Outlined.FileDownload,
                onSecondary = onSecondary,
            )
        }
    }
}

// -------------------- Shared building blocks --------------------

@Composable
private fun OutputCardHeader(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Outlined.Clear,
                contentDescription = stringResource(R.string.clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutputIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

// Empty-state affordances, stacked by hierarchy:
//   1. Paste — a sub-action of text mode. Small AssistChip, only useful when
//      the user is about to dump a Riz packet from clipboard. Goes away the
//      moment the field has any content.
//   2. Choose files — a whole separate functionality, not an "attachment" to a
//      message. Full-width FilledTonalButton with a folder icon so it reads as
//      a distinct mode, not another text-input shortcut.
@Composable
private fun EmptyActions(
    onPaste: (String) -> Unit,
    onChooseFiles: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        AssistChip(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (clipboard.hasPrimaryClip()) {
                    val item = clipboard.primaryClip?.getItemAt(0)
                    val pasteText = item?.text?.toString()
                    if (!pasteText.isNullOrEmpty()) onPaste(pasteText)
                }
            },
            label = { Text(stringResource(R.string.paste)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onChooseFiles,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.empty_files_button))
        }
    }
}

@Composable
private fun InlineProcessingCard(
    statusText: String,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProcessingIndicator(
                visible = true,
                statusText = statusText,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun HomeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scrollState = rememberScrollState()

    val container by animateColorAsState(
        targetValue =
            if (isFocused) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        animationSpec = tween(150),
        label = "homeFieldContainer",
    )

    val primary = MaterialTheme.colorScheme.primary
    val selectionColors =
        remember(primary) {
            TextSelectionColors(
                handleColor = primary,
                backgroundColor = primary.copy(alpha = 0.4f),
            )
        }

    Surface(
        modifier = modifier,
        color = container,
        shape = MaterialTheme.shapes.large,
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides selectionColors,
                LocalTextStyle provides
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(end = if (value.isNotEmpty()) 36.dp else 0.dp),
                    textStyle = LocalTextStyle.current,
                    cursorBrush = SolidColor(primary),
                    interactionSource = interactionSource,
                )
            }
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = stringResource(R.string.clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Thin scrollbar on the right edge — only visible when the text
            // overflows. Padded down past the Clear icon so the two don't
            // overlap when both are present.
            VerticalScrollbarIndicator(
                scrollState = scrollState,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            top = if (value.isNotEmpty()) SCROLLBAR_TOP_CLEAR_OFFSET else 4.dp,
                            bottom = 4.dp,
                        )
                        .width(SCROLLBAR_THICKNESS)
                        .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun VerticalScrollbarIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0) return

    val viewportPx = scrollState.viewportSize.toFloat()
    val totalContentPx = viewportPx + maxScroll
    val thumbFraction = (viewportPx / totalContentPx).coerceIn(SCROLL_THUMB_MIN_FRACTION, 1f)

    val color = MaterialTheme.colorScheme.outline.copy(alpha = SCROLLBAR_ALPHA)

    BoxWithConstraints(modifier = modifier) {
        val trackHeight = maxHeight
        val thumbHeight = trackHeight * thumbFraction

        // Read scrollState.value inside the offset lambda (layout phase) instead
        // of in composition — every scroll otherwise forces recomposition.
        Box(
            modifier =
                Modifier
                    .offset {
                        val travelPx = (trackHeight - thumbHeight).toPx()
                        val scrollFraction = scrollState.value.toFloat() / maxScroll
                        IntOffset(0, (travelPx * scrollFraction).roundToInt())
                    }
                    .fillMaxWidth()
                    .height(thumbHeight)
                    .background(color, CircleShape),
        )
    }
}

// -------------------- Clipboard / share / file helpers --------------------

private fun copyToClipboard(
    context: Context,
    text: String,
    toast: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

private fun shareText(
    context: Context,
    text: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    context.startActivity(Intent.createChooser(intent, null))
}

private fun contentUriFor(
    context: Context,
    file: File,
): Uri =
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

private fun resolveMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        ext.isEmpty() -> "*/*"
        ext == "apk" -> "application/vnd.android.package-archive"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }
}

private fun openResultFile(
    context: Context,
    file: File,
): Boolean {
    val uri = contentUriFor(context, file)
    val mime = resolveMimeType(file.name)
    val viewIntent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    return try {
        context.startActivity(viewIntent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun shareSingleFile(
    context: Context,
    file: File,
) {
    val uri = contentUriFor(context, file)
    val mime = resolveMimeType(file.name)
    val shareIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(shareIntent, file.name))
}

private fun shareAllFiles(
    context: Context,
    files: List<File>,
) {
    if (files.isEmpty()) return
    if (files.size == 1) {
        shareSingleFile(context, files.first())
        return
    }
    val uris = ArrayList(files.map { contentUriFor(context, it) })
    val intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, null))
}

private const val BYTES_PER_MB = 1024L * 1024L

@Composable
private fun formatFileSize(bytes: Long): String =
    if (bytes < BYTES_PER_MB) {
        stringResource(R.string.size_kb, bytes / 1024)
    } else {
        stringResource(R.string.size_mb, bytes / BYTES_PER_MB)
    }

@Composable
private fun ErrorRow(error: String?) {
    AnimatedVisibility(
        visible = error != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        error?.let { msg ->
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
