package com.riz.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riz.app.R
import kotlinx.coroutines.delay

@Composable
fun ProcessingIndicator(
    visible: Boolean,
    statusText: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    stepIndex: Int? = null,
    totalSteps: Int = 3,
) {
    // Only show the indicator if the operation takes longer than 400ms
    // to prevent flickering on instant operations.
    var showIndicator by remember { mutableStateOf(false) }

    val debounceDelay = 400L
    LaunchedEffect(visible) {
        if (visible) {
            delay(debounceDelay)
            showIndicator = true
        } else {
            showIndicator = false
        }
    }

    AnimatedVisibility(
        visible = showIndicator && visible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.CenterVertically),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.CenterVertically),
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // File flow: show the stepper above the spinner. Message flow: skip
            // the stepper since the operation is sub-second and the stepper
            // would barely have time to update.
            if (stepIndex != null) {
                ProgressStepper(
                    currentStep = stepIndex,
                    totalSteps = totalSteps,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            )
            statusText?.let { status ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
