package ch.threema.common

import java.time.Instant

/**
 * Interprets the unsigned long value as an epoch milliseconds timestamp and converts it to an instant.
 */
fun ULong.toEpochMilliInstant(): Instant =
    Instant.ofEpochMilli(toLong())
