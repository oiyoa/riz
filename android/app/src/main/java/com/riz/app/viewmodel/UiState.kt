package com.riz.app.viewmodel

import android.net.Uri
import java.io.File

enum class LoadingStatus {
    PREPARING,
    COMPRESSING,
    EXTRACTING,
    SAVING,
}

enum class ErrorType {
    ENTER_TEXT,
    ENTER_COMPRESSED_TEXT,
    SET_KEY_FIRST,
    INVALID_PARTS,
    WRONG_KEY_OR_CORRUPT,
    GENERIC,
    FILE_PROCESSING,
    SELECT_FILE,
}

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
)

data class ResultFile(
    val file: File,
    val name: String,
    val size: Long,
)

data class FileUiState(
    val selectedFiles: List<SelectedFile> = emptyList(),
    val splitEnabled: Boolean = false,
    val splitSizeMB: Int = 10,
    val isProcessing: Boolean = false,
    val loadingStatus: LoadingStatus? = null,
    val error: ErrorType? = null,
    val results: List<ResultFile> = emptyList(),
    val pendingDownloadFile: ResultFile? = null,
)

data class MessageUiState(
    val inputText: String = "",
    val outputText: String = "",
    val isProcessing: Boolean = false,
    val loadingStatus: LoadingStatus? = null,
    val error: ErrorType? = null,
    val showOutput: Boolean = false,
)
