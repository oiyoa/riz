package com.riz.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.riz.app.viewmodel.FileUiState
import com.riz.app.viewmodel.MessageUiState
import kotlinx.coroutines.delay

private const val POST_SUCCESS_SECRET_DELAY_MS = 1100L
private const val POST_SUCCESS_SECRET_HOLD_MS = 3200L

@Stable
data class LogoSignals(
    val mood: LogoMood,
    val celebrateTick: Int,
    val sadTick: Int,
    val secretMode: Boolean,
)

@Composable
fun rememberLogoSignals(
    messageState: MessageUiState,
    fileState: FileUiState,
    passwordSheetOpen: Boolean,
): LogoSignals {
    val mood =
        if (messageState.isProcessing || fileState.isProcessing) {
            LogoMood.WORKING
        } else {
            LogoMood.IDLE
        }

    val celebrateTick =
        rememberTickOnTransition(messageState.showOutput) +
            rememberTickOnTransition(fileState.results.isNotEmpty())

    val sadTick =
        rememberTickOnTransition(messageState.error != null) +
            rememberTickOnTransition(fileState.error != null)

    val postMessageSecret =
        rememberTransientFlag(
            trigger = messageState.showOutput,
            startAfterMs = POST_SUCCESS_SECRET_DELAY_MS,
            holdMs = POST_SUCCESS_SECRET_HOLD_MS,
        )
    val postFileSecret =
        rememberTransientFlag(
            trigger = fileState.results.isNotEmpty(),
            startAfterMs = POST_SUCCESS_SECRET_DELAY_MS,
            holdMs = POST_SUCCESS_SECRET_HOLD_MS,
        )

    return LogoSignals(
        mood = mood,
        celebrateTick = celebrateTick,
        sadTick = sadTick,
        secretMode = passwordSheetOpen || postMessageSecret || postFileSecret,
    )
}

@Composable
private fun rememberTickOnTransition(triggered: Boolean): Int {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(triggered) {
        if (triggered) tick += 1
    }
    return tick
}

@Composable
private fun rememberTransientFlag(
    trigger: Boolean,
    startAfterMs: Long,
    holdMs: Long,
): Boolean {
    var flag by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (!trigger) {
            flag = false
            return@LaunchedEffect
        }
        // Reset on cancellation too: if `trigger` flips false mid-hold the
        // effect cancels at the next delay, and without finally the flag
        // would stick at true forever.
        try {
            delay(startAfterMs)
            flag = true
            delay(holdMs)
        } finally {
            flag = false
        }
    }
    return flag
}
