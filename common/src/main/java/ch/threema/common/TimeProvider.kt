package ch.threema.common

import java.time.Instant
import java.time.LocalDateTime

/**
 * Provides the current absolute time. Note that this time is not monotonic, as the user or the system may adjust the device's time at any point.
 * If you need monotonic time, use [kotlin.time.TimeSource] instead.
 */
interface TimeProvider {
    fun get(): Instant

    fun getLocal(): LocalDateTime

    companion object {
        @JvmField
        val default = object : TimeProvider {
            override fun get() = Instant.now()

            override fun getLocal() = LocalDateTime.now()
        }
    }
}
