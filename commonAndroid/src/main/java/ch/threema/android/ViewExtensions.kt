package ch.threema.android

import android.graphics.ColorMatrixColorFilter
import android.view.View
import android.widget.ImageView
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration

/**
 * Suspends until the next main thread looper iteration.
 * This is mainly useful when a view's measured dimensions (i.e., width and height) need to be known.
 */
suspend fun View.awaitLayout() = suspendCoroutine { continuation ->
    post {
        continuation.resume(Unit)
    }
}

fun View.postDelayed(delay: Duration, action: () -> Unit) {
    postDelayed(action, delay.inWholeMilliseconds)
}

/**
 * Gets the coordinates of this view in the coordinate space of the window that contains the view.
 */
fun View.getLocation(xOffset: Int = 0, yOffset: Int = 0): IntArray {
    val location = IntArray(2)
    getLocationInWindow(location)
    location[0] += xOffset
    location[1] += yOffset
    return location
}

fun View.getTopCenterLocation(): IntArray =
    getLocation(xOffset = width / 2)

fun View.getBottomCenterLocation(): IntArray =
    getLocation(xOffset = width / 2, yOffset = height)

private val NEGATIVE_MATRIX = floatArrayOf(
    -1.0f, 0f, 0f, 0f, 255f, // red
    0f, -1.0f, 0f, 0f, 255f, // green
    0f, 0f, -1.0f, 0f, 255f, // blue
    0f, 0f, 0f, 1.0f, 0f, // alpha
)

fun ImageView.invertColors() {
    colorFilter = ColorMatrixColorFilter(NEGATIVE_MATRIX)
}
