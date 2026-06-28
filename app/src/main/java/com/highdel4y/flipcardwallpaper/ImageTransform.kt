package com.highdel4y.flipcardwallpaper

import androidx.compose.runtime.Immutable

@Immutable
data class ImageTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun sanitized(): ImageTransform = ImageTransform(
        scale = scale.coerceIn(MIN_SCALE, MAX_SCALE),
        offsetX = offsetX.coerceIn(-1f, 1f),
        offsetY = offsetY.coerceIn(-1f, 1f),
    )

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 3f
        val Centered = ImageTransform()
    }
}
