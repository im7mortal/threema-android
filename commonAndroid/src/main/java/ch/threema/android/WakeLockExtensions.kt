package ch.threema.android

import android.os.PowerManager
import kotlin.time.Duration

fun PowerManager.WakeLock.acquire(timeout: Duration) {
    acquire(timeout.inWholeMilliseconds)
}

fun PowerManager.WakeLock.releaseIfHeld() {
    if (isHeld) {
        release()
    }
}
