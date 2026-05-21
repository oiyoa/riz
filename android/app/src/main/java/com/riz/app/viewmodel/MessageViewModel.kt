package com.riz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riz.app.crypto.Base64Url
import com.riz.app.crypto.RizDetector
import com.riz.app.data.repository.SecurityRepository
import com.riz.app.util.AppLog
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
import javax.crypto.AEADBadTagException

class MessageViewModel(
    private val securityRepository: SecurityRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessageUiState())
    val uiState: StateFlow<MessageUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MessageEvent>()
    val events: SharedFlow<MessageEvent> = _events.asSharedFlow()

    // replay=1 so the HomeScreen collector receives the event even if the
    // intake happens during MainActivity.onCreate, before composition mounts.
    private val _shareIntake = MutableSharedFlow<Unit>(replay = 1)
    val shareIntake: SharedFlow<Unit> = _shareIntake.asSharedFlow()

    private var currentJob: Job? = null
    private var detectionJob: Job? = null

    sealed class MessageEvent {
        data class Completed(val isExtract: Boolean) : MessageEvent()
    }

    fun ingestSharedText(text: String) {
        onInputPasted(text)
        viewModelScope.launch { _shareIntake.emit(Unit) }
    }

    // Typing implies compose-intent: skip detection entirely so labels don't flicker per
    // keystroke. The detection flow only kicks in on an explicit paste.
    fun onInputChanged(text: String) {
        detectionJob?.cancel()
        _uiState.update {
            it.copy(
                inputText = text,
                detection = RizDetector.Result.NotRiz,
                isDetecting = false,
            )
        }
    }

    fun onInputPasted(text: String) {
        _uiState.update { it.copy(inputText = text) }
        runDetection(text)
    }

    fun clearInput() {
        detectionJob?.cancel()
        _uiState.update {
            it.copy(
                inputText = "",
                showOutput = false,
                error = null,
                detection = RizDetector.Result.NotRiz,
                isDetecting = false,
            )
        }
    }

    // Drop back from DONE to READY with the typed text intact so users can fix
    // a typo without retyping. Ciphertext is discarded — they're about to
    // produce a fresh one.
    fun editInput() {
        _uiState.update {
            it.copy(
                showOutput = false,
                outputText = "",
                outputCreatedAt = null,
                error = null,
            )
        }
    }

    private fun runDetection(text: String) {
        detectionJob?.cancel()
        val screen = RizDetector.screenMessageText(text)
        if (screen == RizDetector.Result.NotRiz) {
            _uiState.update { it.copy(detection = RizDetector.Result.NotRiz, isDetecting = false) }
            return
        }
        val pwd = securityRepository.getPassword()
        if (pwd.isNullOrEmpty()) {
            _uiState.update { it.copy(detection = RizDetector.Result.MaybeRiz, isDetecting = false) }
            return
        }
        _uiState.update { it.copy(detection = RizDetector.Result.MaybeRiz, isDetecting = true) }
        detectionJob =
            viewModelScope.launch {
                try {
                    val result = RizDetector.probeMessage(text, pwd)
                    _uiState.update { it.copy(detection = result, isDetecting = false) }
                    // Skip auto-decrypt if the field changed while we were probing.
                    if (result == RizDetector.Result.Riz && _uiState.value.inputText == text) {
                        decrypt()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("MessageViewModel", "Detection failed", e)
                    _uiState.update { it.copy(isDetecting = false) }
                }
            }
    }

    fun compress() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(error = ErrorType.ENTER_TEXT) }
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
                loadingStatus = LoadingStatus.COMPRESSING,
                error = null,
                showOutput = false,
            )
        }

        currentJob?.cancel()
        currentJob =
            viewModelScope.launch {
                try {
                    val encrypted = securityRepository.encryptMessage(text, pwd)
                    val result = Base64Url.encode(encrypted)

                    _uiState.update {
                        it.copy(
                            outputText = result,
                            outputCreatedAt = null,
                            isProcessing = false,
                            showOutput = true,
                        )
                    }
                    _events.emit(MessageEvent.Completed(isExtract = false))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("MessageViewModel", "Operation failed", e)
                    _uiState.update {
                        it.copy(error = ErrorType.GENERIC, isProcessing = false)
                    }
                }
            }
    }

    fun decrypt() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(error = ErrorType.ENTER_COMPRESSED_TEXT) }
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
                loadingStatus = LoadingStatus.EXTRACTING,
                error = null,
                showOutput = false,
            )
        }

        currentJob?.cancel()
        currentJob =
            viewModelScope.launch {
                try {
                    val packed = Base64Url.decode(text)
                    val res = securityRepository.decryptBytes(packed, pwd)
                    val result = String(res.data, Charsets.UTF_8)

                    _uiState.update {
                        it.copy(
                            outputText = result,
                            outputCreatedAt = res.createdAt,
                            isProcessing = false,
                            showOutput = true,
                        )
                    }
                    _events.emit(MessageEvent.Completed(isExtract = true))
                } catch (e: AEADBadTagException) {
                    AppLog.e("MessageViewModel", "Decryption failed: bad tag", e)
                    _uiState.update { it.copy(error = ErrorType.WRONG_KEY_OR_CORRUPT, isProcessing = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("MessageViewModel", "Operation failed", e)
                    _uiState.update {
                        it.copy(error = ErrorType.GENERIC, isProcessing = false)
                    }
                }
            }
    }

    fun cancelTask() {
        currentJob?.cancel()
        _uiState.update { it.copy(isProcessing = false) }
    }
}
