package com.highdel4y.flipcardwallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Size
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object BitmapLoader {
    fun decodeForTarget(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        hardware: Boolean,
    ): Bitmap? {
        if (targetWidth <= 0 || targetHeight <= 0) return null

        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = if (hardware) {
                    ImageDecoder.ALLOCATOR_HARDWARE
                } else {
                    ImageDecoder.ALLOCATOR_SOFTWARE
                }
                decoder.isMutableRequired = false
                decoder.setTargetSizeForCover(info.size, targetWidth, targetHeight)
            }
        }.getOrNull()
    }

    private fun ImageDecoder.setTargetSizeForCover(sourceSize: Size, targetWidth: Int, targetHeight: Int) {
        val sourceWidth = sourceSize.width
        val sourceHeight = sourceSize.height
        if (sourceWidth <= 0 || sourceHeight <= 0) return

        val scale = max(
            targetWidth.toFloat() / sourceWidth.toFloat(),
            targetHeight.toFloat() / sourceHeight.toFloat(),
        )
        val clampedScale = min(1f, scale)
        val decodeWidth = max(1, ceil(sourceWidth * clampedScale).toInt())
        val decodeHeight = max(1, ceil(sourceHeight * clampedScale).toInt())
        setTargetSize(decodeWidth, decodeHeight)
    }
}
