package com.riz.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import com.riz.app.R

private const val VIEWPORT_WIDTH = 117f
private const val VIEWPORT_HEIGHT = 132f

// Feature anchor points expressed as fractions of the 117x132 viewport.
private val LeftEye = TransformOrigin(29f / VIEWPORT_WIDTH, 65f / VIEWPORT_HEIGHT)
private val RightEye = TransformOrigin(89f / VIEWPORT_WIDTH, 65f / VIEWPORT_HEIGHT)
private val LeftAntennaBase = TransformOrigin(37f / VIEWPORT_WIDTH, 58.5f / VIEWPORT_HEIGHT)
private val RightAntennaBase = TransformOrigin(80.5f / VIEWPORT_WIDTH, 58.5f / VIEWPORT_HEIGHT)
private val MouthAnchor = TransformOrigin(59f / VIEWPORT_WIDTH, 84f / VIEWPORT_HEIGHT)
private val BodyBottom = TransformOrigin(0.5f, 0.9f)
private val BodyCenter = TransformOrigin(0.5f, 0.55f)

@Composable
fun AnimatedLogo(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    mood: LogoMood = LogoMood.IDLE,
    celebrateTick: Int = 0,
    sadTick: Int = 0,
    secretMode: Boolean = false,
) {
    val state = remember { LogoState() }
    val tapPulse = remember { mutableIntStateOf(0) }
    var tapTick by tapPulse
    val eyesActive = mood == LogoMood.IDLE && !secretMode
    val bodyIdle = mood == LogoMood.IDLE

    LaunchedEffect(Unit) { runBreathe(state.breathe) }
    LaunchedEffect(Unit) { runAntennaSway(state.antennaPhase) }
    LaunchedEffect(eyesActive) { if (eyesActive) runBlinkLoop(state.blink) }
    LaunchedEffect(eyesActive) { if (eyesActive) runLookLoop(state.lookX) }
    LaunchedEffect(eyesActive) { if (eyesActive) runYawnLoop(state.mouthScaleY, state.yawnEyeSquint) }
    LaunchedEffect(eyesActive) { if (eyesActive) runLookUpLoop(state.lookY) }
    LaunchedEffect(bodyIdle) { if (bodyIdle) runChewLoop(state.mouthScaleY) }
    LaunchedEffect(bodyIdle) { if (bodyIdle) runHeadTiltLoop(state.idleTilt) }
    LaunchedEffect(mood) { runMoodTransition(state, mood) }
    LaunchedEffect(secretMode) { runSecret(state, secretMode) }
    LaunchedEffect(tapTick) { if (tapTick > 0) runTapReaction(state) }
    LaunchedEffect(celebrateTick) { if (celebrateTick > 0) runCelebrate(state) }
    LaunchedEffect(sadTick) { if (sadTick > 0) runSad(state) }

    LogoCanvas(
        state = state,
        contentDescription = contentDescription,
        modifier = modifier,
        onTap = { tapTick += 1 },
    )
}

@Composable
private fun LogoCanvas(
    state: LogoState,
    contentDescription: String?,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(VIEWPORT_WIDTH / VIEWPORT_HEIGHT)
                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
                .graphicsLayer {
                    scaleX = state.breathe.value * state.bounceX.value
                    scaleY = state.breathe.value * state.bounceY.value
                    rotationZ = state.bodyShake.value +
                        (state.workSwayPhase.value - PHASE_CENTER) * 2f *
                        WORK_SWAY_DEGREES * state.workSway.value
                    transformOrigin = BodyBottom
                }
                .graphicsLayer {
                    rotationZ = state.idleTilt.value
                    transformOrigin = BodyCenter
                },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_base),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
        AntennaLayer(state, isLeft = true)
        AntennaLayer(state, isLeft = false)
        MouthLayer(state)
        EyeLayer(state, R.drawable.ic_logo_sclera_left, LeftEye, isLeft = true, translates = false)
        EyeLayer(state, R.drawable.ic_logo_sclera_right, RightEye, isLeft = false, translates = false)
        EyeLayer(state, R.drawable.ic_logo_iris_left, LeftEye, isLeft = true, translates = true)
        EyeLayer(state, R.drawable.ic_logo_iris_right, RightEye, isLeft = false, translates = true)
        EyeLayer(state, R.drawable.ic_logo_highlights_left, LeftEye, isLeft = true, translates = false)
        EyeLayer(state, R.drawable.ic_logo_highlights_right, RightEye, isLeft = false, translates = false)
        EyelidLayer(state, R.drawable.ic_logo_eyelid_left, LeftEye, isLeft = true)
        EyelidLayer(state, R.drawable.ic_logo_eyelid_right, RightEye, isLeft = false)
    }
}

@Composable
private fun EyelidLayer(
    state: LogoState,
    drawable: Int,
    origin: TransformOrigin,
    isLeft: Boolean,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = origin
                    val perEye = if (isLeft) state.secretLeftOpen.value else state.secretRightOpen.value
                    alpha = (1f - perEye).coerceIn(0f, 1f)
                },
    )
}

@Composable
private fun MouthLayer(state: LogoState) {
    Image(
        painter = painterResource(R.drawable.ic_logo_mouth),
        contentDescription = null,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = MouthAnchor
                    scaleY = state.mouthScaleY.value
                },
    )
}

@Composable
private fun AntennaLayer(
    state: LogoState,
    isLeft: Boolean,
) {
    val drawable = if (isLeft) R.drawable.ic_logo_antenna_left else R.drawable.ic_logo_antenna_right
    val origin = if (isLeft) LeftAntennaBase else RightAntennaBase
    val sign = if (isLeft) 1f else -1f
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = origin
                    val phase = state.antennaPhase.value - PHASE_CENTER
                    val workPhase = state.workAntennaPhase.value - PHASE_CENTER
                    rotationZ = sign * phase * ANTENNA_SWAY_DEGREES +
                        sign * workPhase * WORK_ANTENNA_EXTRA_DEGREES * state.workAntennaBoost.value +
                        -sign * CELEBRATE_ANTENNA_SPREAD * state.celebrateAntennaSpread.value +
                        sign * SAD_ANTENNA_DROOP_DEG * state.sadAntennaDroop.value
                },
    )
}

@Composable
private fun EyeLayer(
    state: LogoState,
    drawable: Int,
    origin: TransformOrigin,
    isLeft: Boolean,
    translates: Boolean,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = origin
                    val perEye = if (isLeft) state.secretLeftOpen.value else state.secretRightOpen.value
                    val eyeY =
                        state.blink.value * state.workEyeOpen.value *
                            state.sadEyeOpen.value * state.yawnEyeSquint.value * perEye
                    scaleY = eyeY * state.celebrateEyeWide.value
                    scaleX = state.celebrateEyeWide.value * perEye
                    if (translates) {
                        translationX = state.lookX.value / VIEWPORT_WIDTH * size.width
                        val effectiveLookY = state.lookY.value + state.secretLookY.value
                        translationY = effectiveLookY / VIEWPORT_HEIGHT * size.height
                    }
                },
    )
}
