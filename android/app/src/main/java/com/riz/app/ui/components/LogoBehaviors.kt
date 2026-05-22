package com.riz.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ============================================================
// Ambient — continuous loops that make the mascot feel alive.
// ============================================================

private const val BREATHE_SCALE = 1.015f
private const val BREATHE_MS = 3000

private const val ANTENNA_SWAY_MS = 3200
internal const val ANTENNA_SWAY_DEGREES = 6f
internal const val PHASE_CENTER = 0.5f

private const val BLINK_INITIAL_DELAY_MS = 1500L
private const val BLINK_CLOSED_SCALE = 0.05f
private const val BLINK_CLOSE_MS = 80
private const val BLINK_HOLD_MS = 40L
private const val BLINK_OPEN_MS = 120
private const val DOUBLE_BLINK_CHANCE = 0.3f
private const val BLINK_BETWEEN_DOUBLES_MS = 120L
private const val BLINK_MIN_INTERVAL_MS = 3000L
private const val BLINK_MAX_INTERVAL_MS = 6000L

private const val LOOK_INITIAL_DELAY_MS = 4500L
private const val LOOK_SIDE_OFFSET = 2f
private const val LOOK_SEEK_MS = 400
private const val LOOK_CROSS_MS = 600
private const val LOOK_HOLD_MS = 700L
private const val LOOK_MIN_INTERVAL_MS = 8000L
private const val LOOK_MAX_INTERVAL_MS = 14000L

private const val CHEW_RESET_MS = 120
private const val CHEW_CLOSE_SCALE = 0.32f
private const val CHEW_OPEN_SCALE = 1.05f
private const val CHEW_CLOSE_MS = 130
private const val CHEW_OPEN_MS = 110
private const val CHEW_MIN_REPS = 3
private const val CHEW_MAX_REPS = 5
private const val CHEW_MIN_INTERVAL_MS = 12000L
private const val CHEW_MAX_INTERVAL_MS = 22000L

private const val YAWN_RESET_MS = 120
private const val YAWN_MOUTH_OPEN = 1.55f
private const val YAWN_EYE_SQUINT = 0.45f
private const val YAWN_OPEN_MS = 280
private const val YAWN_HOLD_MS = 360L
private const val YAWN_CLOSE_MS = 360
private const val YAWN_MIN_INTERVAL_MS = 60000L
private const val YAWN_MAX_INTERVAL_MS = 120000L

private const val TILT_RESET_MS = 200
private const val TILT_DEGREES = 4f
private const val TILT_OUT_MS = 700
private const val TILT_RETURN_MS = 800
private const val TILT_MIN_INTERVAL_MS = 18000L
private const val TILT_MAX_INTERVAL_MS = 30000L

private const val LOOK_UP_RESET_MS = 200
private const val LOOK_UP_OFFSET = -2f
private const val LOOK_UP_MS = 420
private const val LOOK_UP_HOLD_MS = 700L
private const val LOOK_UP_RETURN_MS = 420
private const val LOOK_UP_MIN_INTERVAL_MS = 22000L
private const val LOOK_UP_MAX_INTERVAL_MS = 38000L

internal suspend fun runBreathe(breathe: FloatAnim) {
    breathe.animateTo(
        BREATHE_SCALE,
        infiniteRepeatable(tween(BREATHE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )
}

internal suspend fun runAntennaSway(phase: FloatAnim) {
    phase.animateTo(
        1f,
        infiniteRepeatable(tween(ANTENNA_SWAY_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    )
}

internal suspend fun runBlinkLoop(blink: FloatAnim) {
    delay(BLINK_INITIAL_DELAY_MS)
    while (true) {
        singleBlink(blink)
        if (Random.nextFloat() < DOUBLE_BLINK_CHANCE) {
            delay(BLINK_BETWEEN_DOUBLES_MS)
            singleBlink(blink)
        }
        delay(Random.nextLong(BLINK_MIN_INTERVAL_MS, BLINK_MAX_INTERVAL_MS))
    }
}

private suspend fun singleBlink(blink: FloatAnim) {
    blink.animateTo(BLINK_CLOSED_SCALE, tween(BLINK_CLOSE_MS, easing = LinearEasing))
    delay(BLINK_HOLD_MS)
    blink.animateTo(1f, tween(BLINK_OPEN_MS, easing = LinearEasing))
}

internal suspend fun runLookLoop(lookX: FloatAnim) {
    delay(LOOK_INITIAL_DELAY_MS)
    while (true) {
        lookX.animateTo(-LOOK_SIDE_OFFSET, tween(LOOK_SEEK_MS, easing = FastOutSlowInEasing))
        delay(LOOK_HOLD_MS)
        lookX.animateTo(LOOK_SIDE_OFFSET, tween(LOOK_CROSS_MS, easing = FastOutSlowInEasing))
        delay(LOOK_HOLD_MS)
        lookX.animateTo(0f, tween(LOOK_SEEK_MS, easing = FastOutSlowInEasing))
        delay(Random.nextLong(LOOK_MIN_INTERVAL_MS, LOOK_MAX_INTERVAL_MS))
    }
}

internal suspend fun runChewLoop(mouth: FloatAnim) {
    mouth.animateTo(1f, tween(CHEW_RESET_MS, easing = FastOutSlowInEasing))
    while (true) {
        delay(Random.nextLong(CHEW_MIN_INTERVAL_MS, CHEW_MAX_INTERVAL_MS))
        val reps = Random.nextInt(CHEW_MIN_REPS, CHEW_MAX_REPS + 1)
        repeat(reps) {
            mouth.animateTo(CHEW_CLOSE_SCALE, tween(CHEW_CLOSE_MS, easing = FastOutSlowInEasing))
            mouth.animateTo(CHEW_OPEN_SCALE, tween(CHEW_OPEN_MS, easing = FastOutSlowInEasing))
        }
        mouth.animateTo(1f, tween(CHEW_OPEN_MS, easing = FastOutSlowInEasing))
    }
}

internal suspend fun runYawnLoop(
    mouth: FloatAnim,
    eyeSquint: FloatAnim,
) {
    mouth.animateTo(1f, tween(YAWN_RESET_MS, easing = FastOutSlowInEasing))
    eyeSquint.animateTo(1f, tween(YAWN_RESET_MS, easing = FastOutSlowInEasing))
    while (true) {
        delay(Random.nextLong(YAWN_MIN_INTERVAL_MS, YAWN_MAX_INTERVAL_MS))
        coroutineScope {
            launch { mouth.animateTo(YAWN_MOUTH_OPEN, tween(YAWN_OPEN_MS, easing = FastOutSlowInEasing)) }
            launch { eyeSquint.animateTo(YAWN_EYE_SQUINT, tween(YAWN_OPEN_MS, easing = FastOutSlowInEasing)) }
        }
        delay(YAWN_HOLD_MS)
        coroutineScope {
            launch { mouth.animateTo(1f, tween(YAWN_CLOSE_MS, easing = FastOutSlowInEasing)) }
            launch { eyeSquint.animateTo(1f, tween(YAWN_CLOSE_MS, easing = FastOutSlowInEasing)) }
        }
    }
}

internal suspend fun runHeadTiltLoop(tilt: FloatAnim) {
    tilt.animateTo(0f, tween(TILT_RESET_MS, easing = FastOutSlowInEasing))
    while (true) {
        delay(Random.nextLong(TILT_MIN_INTERVAL_MS, TILT_MAX_INTERVAL_MS))
        val direction = if (Random.nextBoolean()) 1f else -1f
        tilt.animateTo(direction * TILT_DEGREES, tween(TILT_OUT_MS, easing = FastOutSlowInEasing))
        tilt.animateTo(0f, tween(TILT_RETURN_MS, easing = FastOutSlowInEasing))
    }
}

internal suspend fun runLookUpLoop(lookY: FloatAnim) {
    lookY.animateTo(0f, tween(LOOK_UP_RESET_MS, easing = FastOutSlowInEasing))
    while (true) {
        delay(Random.nextLong(LOOK_UP_MIN_INTERVAL_MS, LOOK_UP_MAX_INTERVAL_MS))
        lookY.animateTo(LOOK_UP_OFFSET, tween(LOOK_UP_MS, easing = FastOutSlowInEasing))
        delay(LOOK_UP_HOLD_MS)
        lookY.animateTo(0f, tween(LOOK_UP_RETURN_MS, easing = FastOutSlowInEasing))
    }
}

// ============================================================
// Mood transition — working concentration overlays.
// ============================================================

private const val WORK_EYE_SQUINT = 0.4f
private const val WORK_TRANSITION_MS = 350
private const val WORK_ANTENNA_BOOST_MS = 900
internal const val WORK_ANTENNA_EXTRA_DEGREES = 10f
private const val WORK_SWAY_MS = 700
internal const val WORK_SWAY_DEGREES = 2.5f

internal suspend fun runMoodTransition(
    state: LogoState,
    mood: LogoMood,
) {
    val working = mood == LogoMood.WORKING
    val transition = tween<Float>(WORK_TRANSITION_MS, easing = FastOutSlowInEasing)
    coroutineScope {
        launch { state.workEyeOpen.animateTo(if (working) WORK_EYE_SQUINT else 1f, transition) }
        launch { state.workAntennaBoost.animateTo(if (working) 1f else 0f, transition) }
        launch { state.workSway.animateTo(if (working) 1f else 0f, transition) }
        if (working) {
            launch {
                state.workAntennaPhase.animateTo(
                    1f,
                    infiniteRepeatable(
                        tween(WORK_ANTENNA_BOOST_MS, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                )
            }
            launch {
                state.workSwayPhase.animateTo(
                    1f,
                    infiniteRepeatable(
                        tween(WORK_SWAY_MS, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                )
            }
        }
    }
}

// ============================================================
// Reactions — one-shot sequences fired by ticks or flags.
// ============================================================

private const val TAP_SQUISH_X = 1.12f
private const val TAP_SQUISH_Y = 0.88f
private const val TAP_SQUISH_MS = 100
private const val TAP_STRETCH_X = 0.94f
private const val TAP_STRETCH_Y = 1.08f
private const val TAP_STRETCH_MS = 180
private const val TAP_EXCITED_BLINK_SCALE = 0.12f
private const val TAP_REOPEN_MS = 150

private const val CELEBRATE_SQUINT_SCALE = 0.15f
private const val CELEBRATE_SQUINT_MS = 100
private const val CELEBRATE_STRETCH_X = 0.86f
private const val CELEBRATE_STRETCH_Y = 1.18f
private const val CELEBRATE_STRETCH_MS = 180
internal const val CELEBRATE_ANTENNA_SPREAD = 14f
private const val CELEBRATE_ANTENNA_MS = 220
private const val CELEBRATE_WIDE_EYE = 1.15f
private const val CELEBRATE_WIDE_MS = 220
private const val CELEBRATE_HOLD_MS = 250L
private const val CELEBRATE_RESET_MS = 260

private const val SAD_EYE_DROOP = 0.32f
private const val SAD_DROOP_MS = 220
internal const val SAD_ANTENNA_DROOP_DEG = 22f
private const val SAD_SHAKE_LEFT_DEG = -7f
private const val SAD_SHAKE_RIGHT_DEG = 7f
private const val SAD_SHAKE_SECOND_LEFT_DEG = -4f
private const val SAD_SHAKE_SECOND_RIGHT_DEG = 4f
private const val SAD_SHAKE_STEP_MS = 130
private const val SAD_HOLD_MS = 280L
private const val SAD_RECOVER_MS = 320

private const val SECRET_CLOSE_SCALE = 0.04f
private const val SECRET_CLOSE_MS = 420
private const val SECRET_OPEN_MS = 380
private const val SECRET_PEEK_OPEN_MS = 240
private const val SECRET_PEEK_CLOSE_MS = 260
private const val SECRET_PEEK_HOLD_MS = 520L
private const val SECRET_PEEK_LOOK_DOWN = 2.5f
private const val SECRET_PEEK_MIN_INTERVAL_MS = 1500L
private const val SECRET_PEEK_MAX_INTERVAL_MS = 3500L

internal suspend fun runTapReaction(state: LogoState) {
    val squish = tween<Float>(TAP_SQUISH_MS, easing = FastOutSlowInEasing)
    val stretch = tween<Float>(TAP_STRETCH_MS, easing = FastOutSlowInEasing)
    val bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
    coroutineScope {
        launch {
            state.bounceX.snapTo(1f)
            state.bounceX.animateTo(TAP_SQUISH_X, squish)
        }
        launch {
            state.bounceY.snapTo(1f)
            state.bounceY.animateTo(TAP_SQUISH_Y, squish)
        }
        launch { state.blink.snapTo(TAP_EXCITED_BLINK_SCALE) }
    }
    coroutineScope {
        launch { state.bounceX.animateTo(TAP_STRETCH_X, stretch) }
        launch { state.bounceY.animateTo(TAP_STRETCH_Y, stretch) }
    }
    coroutineScope {
        launch { state.bounceX.animateTo(1f, bouncy) }
        launch {
            state.bounceY.animateTo(1f, bouncy)
            state.blink.animateTo(1f, tween(TAP_REOPEN_MS))
        }
    }
}

internal suspend fun runCelebrate(state: LogoState) {
    val squint = tween<Float>(CELEBRATE_SQUINT_MS, easing = FastOutSlowInEasing)
    val stretch = tween<Float>(CELEBRATE_STRETCH_MS, easing = FastOutSlowInEasing)
    val widen = tween<Float>(CELEBRATE_WIDE_MS, easing = FastOutSlowInEasing)
    val reset = tween<Float>(CELEBRATE_RESET_MS, easing = FastOutSlowInEasing)
    val bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
    coroutineScope {
        launch { state.blink.animateTo(CELEBRATE_SQUINT_SCALE, squint) }
        launch {
            state.celebrateAntennaSpread.animateTo(
                1f,
                tween(CELEBRATE_ANTENNA_MS, easing = FastOutSlowInEasing),
            )
        }
        launch { state.bounceX.animateTo(CELEBRATE_STRETCH_X, stretch) }
        launch { state.bounceY.animateTo(CELEBRATE_STRETCH_Y, stretch) }
    }
    coroutineScope {
        launch { state.blink.animateTo(1f, widen) }
        launch { state.celebrateEyeWide.animateTo(CELEBRATE_WIDE_EYE, widen) }
        launch { state.bounceX.animateTo(1f, bouncy) }
        launch { state.bounceY.animateTo(1f, bouncy) }
    }
    delay(CELEBRATE_HOLD_MS)
    coroutineScope {
        launch { state.celebrateEyeWide.animateTo(1f, reset) }
        launch { state.celebrateAntennaSpread.animateTo(0f, reset) }
    }
}

internal suspend fun runSad(state: LogoState) {
    val droop = tween<Float>(SAD_DROOP_MS, easing = FastOutSlowInEasing)
    val step = tween<Float>(SAD_SHAKE_STEP_MS, easing = FastOutSlowInEasing)
    val recover = tween<Float>(SAD_RECOVER_MS, easing = FastOutSlowInEasing)
    coroutineScope {
        launch { state.sadEyeOpen.animateTo(SAD_EYE_DROOP, droop) }
        launch { state.sadAntennaDroop.animateTo(1f, droop) }
    }
    state.bodyShake.animateTo(SAD_SHAKE_LEFT_DEG, step)
    state.bodyShake.animateTo(SAD_SHAKE_RIGHT_DEG, step)
    state.bodyShake.animateTo(SAD_SHAKE_SECOND_LEFT_DEG, step)
    state.bodyShake.animateTo(SAD_SHAKE_SECOND_RIGHT_DEG, step)
    state.bodyShake.animateTo(0f, step)
    delay(SAD_HOLD_MS)
    coroutineScope {
        launch { state.sadEyeOpen.animateTo(1f, recover) }
        launch { state.sadAntennaDroop.animateTo(0f, recover) }
    }
}

internal suspend fun runSecret(
    state: LogoState,
    secretMode: Boolean,
) {
    val open = tween<Float>(SECRET_OPEN_MS, easing = FastOutSlowInEasing)
    if (!secretMode) {
        coroutineScope {
            launch { state.secretLeftOpen.animateTo(1f, open) }
            launch { state.secretRightOpen.animateTo(1f, open) }
            launch { state.secretLookY.animateTo(0f, open) }
        }
        return
    }
    closeBothEyes(state)
    peekLoop(state)
}

private suspend fun closeBothEyes(state: LogoState) {
    val close = tween<Float>(SECRET_CLOSE_MS, easing = FastOutSlowInEasing)
    coroutineScope {
        launch { state.blink.snapTo(1f) }
        launch { state.lookX.animateTo(0f, close) }
        launch { state.lookY.animateTo(0f, close) }
        launch { state.yawnEyeSquint.snapTo(1f) }
        launch { state.secretLeftOpen.animateTo(SECRET_CLOSE_SCALE, close) }
        launch { state.secretRightOpen.animateTo(SECRET_CLOSE_SCALE, close) }
    }
}

private suspend fun peekLoop(state: LogoState) {
    val peekOpen = tween<Float>(SECRET_PEEK_OPEN_MS, easing = FastOutSlowInEasing)
    val peekClose = tween<Float>(SECRET_PEEK_CLOSE_MS, easing = FastOutSlowInEasing)
    while (true) {
        delay(Random.nextLong(SECRET_PEEK_MIN_INTERVAL_MS, SECRET_PEEK_MAX_INTERVAL_MS))
        val whichEye = if (Random.nextBoolean()) state.secretLeftOpen else state.secretRightOpen
        coroutineScope {
            launch { whichEye.animateTo(1f, peekOpen) }
            launch { state.secretLookY.animateTo(SECRET_PEEK_LOOK_DOWN, peekOpen) }
        }
        delay(SECRET_PEEK_HOLD_MS)
        coroutineScope {
            launch { whichEye.animateTo(SECRET_CLOSE_SCALE, peekClose) }
            launch { state.secretLookY.animateTo(0f, peekClose) }
        }
    }
}
