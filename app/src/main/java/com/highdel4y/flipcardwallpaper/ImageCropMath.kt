package com.highdel4y.flipcardwallpaper

import kotlin.math.min
import kotlin.math.roundToInt

internal data class BitmapCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}

internal fun calculateBitmapCropRect(
    bitmapWidth: Int,
    bitmapHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    transform: ImageTransform,
): BitmapCropRect {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
        return BitmapCropRect(0, 0, 0, 0)
    }

    val safeTransform = transform.sanitized()
    val cropWidth = visibleCropWidth(bitmapWidth, bitmapHeight, targetWidth, targetHeight, safeTransform.scale)
        .roundToInt()
        .coerceIn(1, bitmapWidth)
    val cropHeight = visibleCropHeight(bitmapWidth, bitmapHeight, targetWidth, targetHeight, safeTransform.scale)
        .roundToInt()
        .coerceIn(1, bitmapHeight)
    val maxShiftX = (bitmapWidth - cropWidth) * 0.5f
    val maxShiftY = (bitmapHeight - cropHeight) * 0.5f
    val centerX = bitmapWidth * 0.5f - safeTransform.offsetX * maxShiftX
    val centerY = bitmapHeight * 0.5f - safeTransform.offsetY * maxShiftY
    val left = (centerX - cropWidth * 0.5f).roundToInt().coerceIn(0, bitmapWidth - cropWidth)
    val top = (centerY - cropHeight * 0.5f).roundToInt().coerceIn(0, bitmapHeight - cropHeight)
    return BitmapCropRect(left, top, left + cropWidth, top + cropHeight)
}

internal fun bitmapCropPanRangeX(
    bitmapWidth: Int,
    bitmapHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    scale: Float,
): Float {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 0f
    val cropWidth = visibleCropWidth(bitmapWidth, bitmapHeight, targetWidth, targetHeight, scale)
    return ((bitmapWidth - cropWidth) * (targetWidth / cropWidth) * 0.5f).coerceAtLeast(0f)
}

internal fun bitmapCropPanRangeY(
    bitmapWidth: Int,
    bitmapHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    scale: Float,
): Float {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 0f
    val cropHeight = visibleCropHeight(bitmapWidth, bitmapHeight, targetWidth, targetHeight, scale)
    return ((bitmapHeight - cropHeight) * (targetHeight / cropHeight) * 0.5f).coerceAtLeast(0f)
}

private fun visibleCropWidth(
    bitmapWidth: Int,
    bitmapHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    scale: Float,
): Float {
    val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
    val bitmapRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()
    val baseCropWidth = if (bitmapRatio > targetRatio) {
        min(bitmapWidth.toFloat(), bitmapHeight * targetRatio)
    } else {
        bitmapWidth.toFloat()
    }
    return (baseCropWidth / scale.coerceIn(ImageTransform.MIN_SCALE, ImageTransform.MAX_SCALE))
        .coerceIn(1f, bitmapWidth.toFloat())
}

private fun visibleCropHeight(
    bitmapWidth: Int,
    bitmapHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
    scale: Float,
): Float {
    val targetRatio = targetWidth.toFloat() / targetHeight.toFloat()
    val bitmapRatio = bitmapWidth.toFloat() / bitmapHeight.toFloat()
    val baseCropHeight = if (bitmapRatio > targetRatio) {
        bitmapHeight.toFloat()
    } else {
        min(bitmapHeight.toFloat(), bitmapWidth / targetRatio)
    }
    return (baseCropHeight / scale.coerceIn(ImageTransform.MIN_SCALE, ImageTransform.MAX_SCALE))
        .coerceIn(1f, bitmapHeight.toFloat())
}
