package ch.threema.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.IntRange
import java.io.ByteArrayOutputStream

/**
 * Runs [block] with [Bitmap] as the argument and then recycles the [Bitmap] afterward.
 */
inline fun <T> Bitmap.use(block: (Bitmap) -> T): T =
    try {
        block(this)
    } finally {
        recycle()
    }

/**
 * Runs [block] with [Bitmap] as the argument and returns the result from [block].
 *
 * If the returned [Bitmap] is different from the original [Bitmap], the original will
 * be recycled. Similarly, if an exception occurs, [Bitmap] is recycled and the exception
 * is re-thrown.
 */
inline fun <T : Bitmap?> Bitmap.transform(block: (Bitmap) -> T): T =
    try {
        val transformed = block(this)
        if (transformed != this) {
            recycle()
        }
        transformed
    } catch (e: Throwable) {
        recycle()
        throw e
    }

fun Bitmap.toJpegByteArray(quality: Int = 80): ByteArray? =
    toByteArray(format = Bitmap.CompressFormat.JPEG, quality = quality)

fun Bitmap.toPngByteArray(quality: Int = 100): ByteArray? =
    toByteArray(format = Bitmap.CompressFormat.PNG, quality = quality)

private fun Bitmap.toByteArray(format: Bitmap.CompressFormat, quality: Int): ByteArray? {
    val stream = ByteArrayOutputStream()
    return if (compress(format, quality, stream)) {
        stream.toByteArray()
    } else {
        null
    }
}

/**
 * Calculates the estimated brightness of an Android Bitmap.
 *
 * @param pixelSpacing tells how many pixels to skip each pixel. Higher values result in better performance, but a more rough estimate.
 * When [pixelSpacing] = 1, the function actually calculates the real average brightness, not an estimate.
 * Do not use values for [pixelSpacing] that are smaller than 1.
 */
@JvmOverloads
fun Bitmap.calculateBrightness(@IntRange(from = 1) pixelSpacing: Int = 2): Int {
    val bitmap = this
    val width = bitmap.getWidth()
    val height = bitmap.getHeight()
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    var red = 0
    var green = 0
    var blue = 0
    var n = 0
    var i = 0
    while (i < pixels.size) {
        val color = pixels[i]
        red += Color.red(color)
        green += Color.green(color)
        blue += Color.blue(color)
        n++
        i += pixelSpacing
    }
    if (n != 0) {
        return (red + blue + green) / (n * 3)
    }
    return 0
}
