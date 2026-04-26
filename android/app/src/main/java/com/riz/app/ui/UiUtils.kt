package com.riz.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.riz.app.R
import com.riz.app.viewmodel.ErrorType
import com.riz.app.viewmodel.LoadingStatus

@Composable
fun ErrorType.asString(): String =
    when (this) {
        ErrorType.ENTER_TEXT -> stringResource(R.string.error_enter_text)
        ErrorType.ENTER_COMPRESSED_TEXT -> stringResource(R.string.error_enter_compressed_text)
        ErrorType.SET_KEY_FIRST -> stringResource(R.string.error_set_key_first)
        ErrorType.INVALID_PARTS -> stringResource(R.string.error_invalid_parts)
        ErrorType.WRONG_KEY_OR_CORRUPT -> stringResource(R.string.error_wrong_key_or_corrupt)
        ErrorType.GENERIC -> stringResource(R.string.error_generic)
        ErrorType.FILE_PROCESSING -> stringResource(R.string.error_file_processing)
        ErrorType.SELECT_FILE -> stringResource(R.string.error_select_file)
    }

@Composable
fun LoadingStatus.asString(): String =
    when (this) {
        LoadingStatus.PREPARING -> stringResource(R.string.status_preparing)
        LoadingStatus.COMPRESSING -> stringResource(R.string.status_compressing)
        LoadingStatus.EXTRACTING -> stringResource(R.string.status_extracting)
        LoadingStatus.SAVING -> stringResource(R.string.status_saving)
    }
