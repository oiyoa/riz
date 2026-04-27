package com.riz.app.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riz.app.crypto.BinaryFormat
import com.riz.app.crypto.FileEntry
import com.riz.app.crypto.FileNamingUtils
import com.riz.app.data.repository.FileRepository
import com.riz.app.data.repository.SecurityRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.crypto.AEADBadTagException

class FileViewModel(
    private val fileRepository: FileRepository,
    private val securityRepository: SecurityRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FileUiState())
    val uiState: StateFlow<FileUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileEvent>()
    val events: SharedFlow<FileEvent> = _events.asSharedFlow()

    private var currentJob: Job? = null

    sealed class FileEvent {
        data class DownloadSuccess(val fileNames: List<String>) : FileEvent()

        data class Error(
            val message: String,
        ) : FileEvent()
    }

    fun setFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val files =
                uris.mapNotNull { uri ->
                    fileRepository.getFileInfo(uri)?.let { (name, size) ->
                        SelectedFile(uri, name, size)
                    }
                }
            _uiState.update { it.copy(selectedFiles = files, results = emptyList(), error = null) }
        }
    }

    fun setSplitEnabled(enabled: Boolean) {
        _uiState.update { it.copy(splitEnabled = enabled) }
    }

    fun removeFile(uri: Uri) {
        val currentFiles = _uiState.value.selectedFiles.toMutableList()
        currentFiles.removeAll { it.uri == uri }
        _uiState.update { it.copy(selectedFiles = currentFiles) }
    }

    fun clearAllFiles() {
        viewModelScope.launch {
            fileRepository.clearCache()
            _uiState.update { it.copy(selectedFiles = emptyList(), results = emptyList(), error = null) }
        }
    }

    fun setSplitSize(mb: Int) {
        _uiState.update { it.copy(splitSizeMB = mb) }
    }

    fun clearResults() {
        viewModelScope.launch {
            fileRepository.clearCache()
            _uiState.update { it.copy(results = emptyList()) }
        }
    }

    fun prepareDownload(file: ResultFile) {
        _uiState.update { it.copy(pendingDownloadFile = file) }
    }

    fun saveResultFile(uri: Uri) {
        val fileToSave =
            _uiState.value.pendingDownloadFile ?: run {
                viewModelScope.launch { _events.emit(FileEvent.Error("Download failed: state lost")) }
                return
            }

        viewModelScope.launch {
            try {
                fileRepository.copyFileToUri(fileToSave.file, uri)
                _uiState.update { it.copy(pendingDownloadFile = null) }
                _events.emit(FileEvent.DownloadSuccess(listOf(fileToSave.name)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FileViewModel", "Save result failed", e)
                _events.emit(FileEvent.Error("Download failed: ${e.localizedMessage}"))
            }
        }
    }

    fun saveAllResults(treeUri: Uri) {
        val results = _uiState.value.results
        if (results.isEmpty()) return

        viewModelScope.launch {
            try {
                val fileNames = results.map { it.name }
                fileRepository.saveAllFilesToTreeUri(results.map { it.file }, treeUri)
                _events.emit(FileEvent.DownloadSuccess(fileNames))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FileViewModel", "Save all results failed", e)
                _events.emit(FileEvent.Error("Save failed: ${e.localizedMessage}"))
            }
        }
    }

    fun encryptFiles() {
        val files = _uiState.value.selectedFiles
        if (files.isEmpty()) {
            _uiState.update { it.copy(error = ErrorType.SELECT_FILE) }
            return
        }
        val pwd = securityRepository.getPassword()
        if (pwd.isNullOrEmpty()) {
            _uiState.update { it.copy(error = ErrorType.SET_KEY_FIRST) }
            return
        }

        _uiState.update {
            it.copy(
                isProcessing = true,
                loadingStatus = LoadingStatus.PREPARING,
                error = null,
                results = emptyList(),
            )
        }

        currentJob?.cancel()
        currentJob =
            viewModelScope.launch {
                try {
                    val fileEntries =
                        files.map { file ->
                            val bytes = fileRepository.readFile(file.uri)
                            FileEntry(file.name, bytes)
                        }

                    _uiState.update { it.copy(loadingStatus = LoadingStatus.COMPRESSING) }
                    val encryptedBytes =
                        if (fileEntries.size == 1) {
                            securityRepository.encryptSingleFile(fileEntries[0].name, fileEntries[0].data, pwd)
                        } else {
                            securityRepository.encryptMultiFiles(fileEntries, pwd)
                        }

                    val parts =
                        if (_uiState.value.splitEnabled) {
                            val splitBytes = _uiState.value.splitSizeMB * 1024 * 1024
                            if (encryptedBytes.size > splitBytes) {
                                BinaryFormat.splitBytes(encryptedBytes, splitBytes)
                            } else {
                                listOf(encryptedBytes)
                            }
                        } else {
                            listOf(encryptedBytes)
                        }

                    _uiState.update { it.copy(loadingStatus = LoadingStatus.SAVING) }
                    fileRepository.clearCache()
                    val resultFiles =
                        parts.mapIndexed { index, bytes ->
                            val name = FileNamingUtils.generateResultFilename(index + 1, parts.size)
                            val file = fileRepository.writeResultFile(name, bytes)
                            ResultFile(file, name, file.length())
                        }

                    _uiState.update { it.copy(results = resultFiles, isProcessing = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("FileViewModel", "Encryption failed", e)
                    _uiState.update { it.copy(error = ErrorType.FILE_PROCESSING, isProcessing = false) }
                }
            }
    }

    fun decryptFiles() {
        var files = _uiState.value.selectedFiles
        if (files.isEmpty()) {
            _uiState.update { it.copy(error = ErrorType.SELECT_FILE) }
            return
        }
        val pwd = securityRepository.getPassword()
        if (pwd.isNullOrEmpty()) {
            _uiState.update { it.copy(error = ErrorType.SET_KEY_FIRST) }
            return
        }

        _uiState.update {
            it.copy(
                isProcessing = true,
                loadingStatus = LoadingStatus.PREPARING,
                error = null,
                results = emptyList(),
            )
        }

        currentJob?.cancel()
        currentJob =
            viewModelScope.launch {
                try {
                    if (files.size > 1) {
                        val sorted = FileNamingUtils.sortFileParts(files.map { it.name to it })
                        if (sorted == null) {
                            _uiState.update { it.copy(error = ErrorType.INVALID_PARTS, isProcessing = false) }
                            return@launch
                        }
                        files = sorted
                    }

                    val bos = ByteArrayOutputStream()
                    for (file in files) {
                        val bytes = fileRepository.readFile(file.uri)
                        bos.write(bytes)
                    }
                    val combinedBytes = bos.toByteArray()

                    _uiState.update { it.copy(loadingStatus = LoadingStatus.EXTRACTING) }
                    val res = securityRepository.decryptBytes(combinedBytes, pwd)

                    fileRepository.clearCache()
                    val resultFiles =
                        res.files
                            .map { f ->
                                val file = fileRepository.writeResultFile(f.name, f.data)
                                ResultFile(file, f.name, file.length())
                            }.ifEmpty {
                                val name = res.filename.ifEmpty { "decrypted_file" }
                                val file = fileRepository.writeResultFile(name, res.data)
                                listOf(ResultFile(file, name, file.length()))
                            }

                    _uiState.update { it.copy(results = resultFiles, isProcessing = false) }
                } catch (e: AEADBadTagException) {
                    Log.e("FileViewModel", "Decryption failed: bad tag", e)
                    _uiState.update { it.copy(error = ErrorType.WRONG_KEY_OR_CORRUPT, isProcessing = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("FileViewModel", "Decryption failed", e)
                    _uiState.update { it.copy(error = ErrorType.GENERIC, isProcessing = false) }
                }
            }
    }

    fun cancelTask() {
        currentJob?.cancel()
        _uiState.update { it.copy(isProcessing = false, loadingStatus = null) }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            fileRepository.clearCache()
        }
    }
}
