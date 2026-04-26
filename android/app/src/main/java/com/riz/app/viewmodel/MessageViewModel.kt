package com.riz.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riz.app.data.repository.SecurityRepository
import com.riz.app.crypto.Base64Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.AEADBadTagException

class MessageViewModel(
    private val securityRepository: SecurityRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MessageUiState())
    val uiState: StateFlow<MessageUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun clearInput() {
        _uiState.update { it.copy(inputText = "", showOutput = false, error = null) }
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
                        it.copy(outputText = result, isProcessing = false, showOutput = true)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MessageViewModel", "Operation failed", e)
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
                        it.copy(outputText = result, isProcessing = false, showOutput = true)
                    }
                } catch (e: AEADBadTagException) {
                    Log.e("MessageViewModel", "Decryption failed: bad tag", e)
                    _uiState.update { it.copy(error = ErrorType.WRONG_KEY_OR_CORRUPT, isProcessing = false) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MessageViewModel", "Operation failed", e)
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
