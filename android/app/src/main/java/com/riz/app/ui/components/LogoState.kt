package com.riz.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D

internal typealias FloatAnim = Animatable<Float, AnimationVector1D>

internal class LogoState {
    // Continuous idle motion.
    val breathe = Animatable(1f)
    val antennaPhase = Animatable(0f)

    // Eye direction and lid state.
    val blink = Animatable(1f)
    val lookX = Animatable(0f)
    val lookY = Animatable(0f)

    // Tap / spring bounce.
    val bounceX = Animatable(1f)
    val bounceY = Animatable(1f)

    // Working-mood overlays.
    val workEyeOpen = Animatable(1f)
    val workAntennaBoost = Animatable(0f)
    val workSway = Animatable(0f)
    val workSwayPhase = Animatable(0f)
    val workAntennaPhase = Animatable(0f)

    // Sad reaction overlays.
    val sadEyeOpen = Animatable(1f)
    val sadAntennaDroop = Animatable(0f)
    val bodyShake = Animatable(0f)

    // Celebrate reaction overlays.
    val celebrateEyeWide = Animatable(1f)
    val celebrateAntennaSpread = Animatable(0f)

    // Ambient mouth, yawn squint, head tilt.
    val mouthScaleY = Animatable(1f)
    val yawnEyeSquint = Animatable(1f)
    val idleTilt = Animatable(0f)

    // Secret mode: per-eye closure + shared look-down for the peeking eye.
    val secretLeftOpen = Animatable(1f)
    val secretRightOpen = Animatable(1f)
    val secretLookY = Animatable(0f)
}
