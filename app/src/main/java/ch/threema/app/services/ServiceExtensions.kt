package ch.threema.app.services

import kotlin.time.Duration

fun LifetimeService.releaseConnectionLinger(sourceTag: String, timeout: Duration) {
    releaseConnectionLinger(sourceTag, timeout.inWholeMilliseconds)
}

inline fun LifetimeService.withConnection(sourceTag: String, linger: Duration, block: () -> Unit) {
    acquireConnection(sourceTag)
    try {
        block()
    } finally {
        releaseConnectionLinger(sourceTag, linger)
    }
}
