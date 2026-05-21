package com.riz.app.viewmodel

import android.net.Uri
import com.riz.app.crypto.RizDetector
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
    val createdAt: Long? = null,
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
    val detection: RizDetector.Result = RizDetector.Result.NotRiz,
    val isDetecting: Boolean = false,
    val resultsAreExtract: Boolean = false,
)

data class MessageUiState(
    val inputText: String = "",
    val outputText: String = "",
    val outputCreatedAt: Long? = null,
    val isProcessing: Boolean = false,
    val loadingStatus: LoadingStatus? = null,
    val error: ErrorType? = null,
    val showOutput: Boolean = false,
    val detection: RizDetector.Result = RizDetector.Result.NotRiz,
    val isDetecting: Boolean = false,
)
