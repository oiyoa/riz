package com.riz.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oiyoa.android.updater.UpdateState
import com.oiyoa.android.updater.Updater
import com.riz.app.BuildConfig
import com.riz.app.R
import com.riz.app.crypto.PasswordPolicy
import com.riz.app.updater.RizUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PASSWORD_MAX_LINES = 3
private const val METER_SEGMENTS = 4
private const val DISABLED_ALPHA = 0.5f
private val AMBER = Color(0xFFF59E0B)

// How long the "Up to date" affirmation lingers on the check-updates
// button after a successful no-update-found check. Long enough for the
// user to register the outcome, short enough that the button reverts to
// the default affordance before they tap it again.
private const val UP_TO_DATE_HOLD_MS = 2500L

// Width cap for tablets/foldables. Standard M3 default is 640dp, which makes
// the input field uncomfortably wide for a single-column password form. 480dp
// keeps the field readable while still filling the screen on phones (where it
// gets clamped to the screen width anyway).
private val SHEET_MAX_WIDTH = 480.dp

/**
 * Single-input password sheet.
 *
 * The visibility toggle in the trailing icon does double duty in settings
 * mode: when the field is empty (no value to mask yet), tapping it asks for
 * biometric/device-credential auth and then populates the field with the
 * stored passphrase, revealed. After that, the same icon toggles visibility
 * normally. No confirm field — the user verifies what they typed by tapping
 * the eye, and generated passphrases are revealed automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
// Composables genuinely have many small render branches; the threshold detekt
// enforces on regular methods isn't a useful signal here.
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun PasswordBottomSheet(
    isSettings: Boolean,
    isBiometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onRequestReveal: (onPassword: (String) -> Unit) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var revealedOriginal by remember { mutableStateOf<String?>(null) }
    var showWeakError by remember { mutableStateOf(false) }
    var biometricUnavailable by remember { mutableStateOf(false) }

    // Skip the half-expanded state: the sheet's content is short and there's
    // nothing to scroll to, so the default 50%-then-drag-up behavior would
    // just hide our primary button behind a gesture.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val assessment =
        remember(password) {
            if (password.isEmpty()) null else PasswordPolicy.assessStrength(password)
        }
    val isUnchangedExisting =
        isSettings && revealedOriginal != null && password == revealedOriginal

    fun attemptSave() {
        when {
            password.isEmpty() -> Unit
            isUnchangedExisting -> onSave(password)
            (assessment?.score ?: 0) < PasswordPolicy.MIN_SCORE -> showWeakError = true
            else -> onSave(password)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        sheetMaxWidth = SHEET_MAX_WIDTH,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text =
                    if (isSettings) {
                        stringResource(R.string.settings_title)
                    } else {
                        stringResource(R.string.create_password)
                    },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            if (!isSettings) {
                // Welcome / first-run only — explains why we're asking for a
                // password. In Settings the password field is self-evident and
                // a tagline just adds vertical noise.
                Text(
                    text = stringResource(R.string.create_password_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 24.dp),
                )
            } else {
                Spacer(Modifier.height(16.dp))
            }

            val hasStoredUnrevealed = isSettings && password.isEmpty() && revealedOriginal == null
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    showWeakError = false
                    biometricUnavailable = false
                },
                label = { Text(stringResource(R.string.password_label)) },
                singleLine = false,
                maxLines = PASSWORD_MAX_LINES,
                visualTransformation =
                    if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Left),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { attemptSave() }),
                leadingIcon =
                    if (hasStoredUnrevealed) {
                        {
                            // Status indicator only — communicates "a key is
                            // saved, the field is locked." Not part of the
                            // input value and not interactive.
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = stringResource(R.string.password_saved_status),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                trailingIcon = {
                    EyeToggle(
                        visible = passwordVisible,
                        canRevealStored = hasStoredUnrevealed,
                        onToggleVisibility = { passwordVisible = !passwordVisible },
                        onRevealStored = {
                            if (isBiometricAvailable) {
                                onRequestReveal { current ->
                                    revealedOriginal = current
                                    password = current
                                    passwordVisible = true
                                }
                            } else {
                                biometricUnavailable = true
                            }
                        },
                    )
                },
                supportingText = {
                    SupportingContent(
                        assessment = assessment,
                        showWeakError = showWeakError,
                        biometricUnavailable = biometricUnavailable,
                        showRevealHint = hasStoredUnrevealed,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(Modifier.height(8.dp))

            // AssistChip — "suggested action," not "suggested input value"
            // (the latter is SuggestionChip's role). Filled with the theme's
            // secondaryContainer so the chip reads as a real affordance
            // against the surface, not just an outlined hint.
            AssistChip(
                onClick = {
                    val phrase = PasswordPolicy.generatePassphrase()
                    password = phrase
                    passwordVisible = true
                    showWeakError = false
                    biometricUnavailable = false
                },
                label = { Text(stringResource(R.string.generate_strong_password)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                border = null,
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { attemptSave() },
                modifier = Modifier.fillMaxWidth(),
                enabled = password.isNotEmpty(),
            ) {
                Text(
                    text =
                        if (isSettings) {
                            stringResource(R.string.save_changes)
                        } else {
                            stringResource(R.string.confirm_and_continue)
                        },
                )
            }

            if (isSettings) {
                // Delete sits with the password actions — a user managing
                // their key thinks of view/change/delete as one cluster. The
                // 8dp gap + error tint keeps it visually distinct from Save
                // so it doesn't read as "the next primary action".
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onClear,
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.delete_password),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Bottom housekeeping row: version label + small text-button
                // for an update check. Sits at the very bottom of the sheet,
                // labelSmall typography, so it reads as metadata rather than
                // a primary action.
                Spacer(Modifier.height(24.dp))
                AppFooterRow()
            }
        }
    }
}

@Composable
private fun AppFooterRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
            modifier = Modifier.weight(1f),
        )
        if (RizUpdater.isEnabled()) {
            CheckUpdatesButton()
        }
    }
}

@Composable
private fun CheckUpdatesButton() {
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()

    // `awaitingResult` is set when the *user* taps. We use it to gate the
    // transient "Up to date" affirmation so we don't show it on background
    // ticks that happen while the sheet is open.
    var awaitingResult by remember { mutableStateOf(false) }

    LaunchedEffect(awaitingResult, state) {
        if (awaitingResult && state !is UpdateState.Checking) {
            if (state is UpdateState.UpToDate) {
                delay(UP_TO_DATE_HOLD_MS)
            }
            awaitingResult = false
        }
    }

    val isChecking = state is UpdateState.Checking
    val showUpToDate = awaitingResult && state is UpdateState.UpToDate

    val labelRes =
        when {
            isChecking -> R.string.checking_for_updates
            showUpToDate -> R.string.up_to_date
            else -> R.string.check_for_updates
        }

    TextButton(
        onClick = {
            awaitingResult = true
            scope.launch { Updater.checkNow(announce = true) }
        },
        enabled = !isChecking,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        when {
            isChecking ->
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            showUpToDate ->
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            else ->
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun EyeToggle(
    visible: Boolean,
    canRevealStored: Boolean,
    onToggleVisibility: () -> Unit,
    onRevealStored: () -> Unit,
) {
    IconButton(
        onClick = { if (canRevealStored) onRevealStored() else onToggleVisibility() },
    ) {
        Icon(
            imageVector = if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            contentDescription =
                if (visible) {
                    stringResource(R.string.hide)
                } else {
                    stringResource(R.string.show)
                },
        )
    }
}

@Composable
private fun SupportingContent(
    assessment: PasswordPolicy.Assessment?,
    showWeakError: Boolean,
    biometricUnavailable: Boolean,
    showRevealHint: Boolean,
) {
    when {
        biometricUnavailable ->
            Text(
                stringResource(R.string.biometric_unavailable),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        showWeakError ->
            Text(
                stringResource(R.string.strength_too_weak_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        assessment != null -> CompactStrengthRow(assessment.score)
        showRevealHint ->
            Text(
                stringResource(R.string.reveal_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
    }
}

@Composable
private fun CompactStrengthRow(score: Int) {
    val activeColor =
        when (score) {
            0, 1 -> MaterialTheme.colorScheme.error
            2 -> AMBER
            else -> MaterialTheme.colorScheme.tertiary
        }
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val label =
        when (score) {
            0 -> stringResource(R.string.strength_very_weak)
            1 -> stringResource(R.string.strength_weak)
            2 -> stringResource(R.string.strength_fair)
            3 -> stringResource(R.string.strength_strong)
            else -> stringResource(R.string.strength_very_strong)
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 1..METER_SEGMENTS) {
                Box(
                    modifier =
                        Modifier
                            .width(14.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= score) activeColor else inactive),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = activeColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
