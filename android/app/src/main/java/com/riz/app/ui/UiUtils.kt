package com.riz.app.ui

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.riz.app.R
import com.riz.app.viewmodel.ErrorType
import com.riz.app.viewmodel.LoadingStatus

@Composable
fun ErrorType.asString(): String =
    when (this) {
        ErrorType.ENTER_TEXT -> stringResource(R.string.error_enter_text)
        ErrorType.ENTER_COMPRESSED_TEXT -> stringResource(R.string.error_enter_compressed_text)
        ErrorType.SET_KEY_FIRST -> stringResource(R.string.error_set_password_first)
        ErrorType.INVALID_PARTS -> stringResource(R.string.error_invalid_parts)
        ErrorType.WRONG_KEY_OR_CORRUPT -> stringResource(R.string.error_wrong_password_or_corrupt)
        ErrorType.GENERIC -> stringResource(R.string.error_generic)
        ErrorType.FILE_PROCESSING -> stringResource(R.string.error_file_processing)
        ErrorType.SELECT_FILE -> stringResource(R.string.error_select_file)
    }

// Step name shown under the progress stepper. We collapse the four internal
// states into three user-facing phases — COMPRESSING/EXTRACTING are both
// "Processing" since the user already knows which way they're going.
@Composable
fun LoadingStatus.asStepName(): String =
    when (this) {
        LoadingStatus.PREPARING -> stringResource(R.string.step_preparing)
        LoadingStatus.COMPRESSING, LoadingStatus.EXTRACTING -> stringResource(R.string.step_processing)
        LoadingStatus.SAVING -> stringResource(R.string.step_saving)
    }

// Index used to drive the stepper UI. We collapse COMPRESSING and EXTRACTING
// into the same "processing" step — the user doesn't need to see that
// distinction reflected on the stepper.
fun LoadingStatus.stepIndex(): Int =
    when (this) {
        LoadingStatus.PREPARING -> STEP_PREPARING
        LoadingStatus.COMPRESSING, LoadingStatus.EXTRACTING -> STEP_PROCESSING
        LoadingStatus.SAVING -> STEP_SAVING
    }

private const val STEP_PREPARING = 1
private const val STEP_PROCESSING = 2
private const val STEP_SAVING = 3

// Locale-aware "Created 2 hours ago (Jan 5, 2026, 3:45 PM)" line shown on
// decrypt results. DateUtils respects the user's system 12/24-hour setting
// and locale. For ages under one minute we substitute "just now", since
// getRelativeTimeSpanString otherwise returns the awkward "0 minutes ago".
@Composable
fun formatCreatedAt(epochMs: Long): String {
    val context = LocalContext.current
    val absoluteFlags =
        DateUtils.FORMAT_SHOW_DATE or
            DateUtils.FORMAT_SHOW_YEAR or
            DateUtils.FORMAT_SHOW_TIME or
            DateUtils.FORMAT_ABBREV_MONTH
    val absolute = DateUtils.formatDateTime(context, epochMs, absoluteFlags)
    val now = System.currentTimeMillis()
    val relative =
        if (now - epochMs < DateUtils.MINUTE_IN_MILLIS) {
            stringResource(R.string.just_now)
        } else {
            DateUtils
                .getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS)
                .toString()
        }
    return stringResource(R.string.created_at, relative, absolute)
}
