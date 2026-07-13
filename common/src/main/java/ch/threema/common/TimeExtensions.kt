package ch.threema.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

operator fun Instant.minus(other: Instant): Duration = (toEpochMilli() - other.toEpochMilli()).milliseconds

operator fun Instant.plus(duration: Duration): Instant = Instant.ofEpochMilli(toEpochMilli() + duration.inWholeMilliseconds)

operator fun Instant.minus(duration: Duration): Instant = Instant.ofEpochMilli(toEpochMilli() - duration.inWholeMilliseconds)

@JvmOverloads
fun Instant.isSameDayAs(other: Instant, zoneId: ZoneId = ZoneId.systemDefault()) =
    LocalDate.ofInstant(this, zoneId) == LocalDate.ofInstant(other, zoneId)

/**
 *  If the duration exceeds one hour, a string in the form of `h:mm:ss` will be returned. If not, it will just return `mm:ss`.
 */
fun Duration.toHMMSS(): String =
    toComponents { hours, minutes, seconds, _ ->
        when {
            hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
            else -> "%02d:%02d".format(minutes, seconds)
        }
    }

/**
 * As negative timestamps (aka dates before 1970) do not make sense in our case and are not supported
 * by the protocol this method can be used to coerce timestamps to a non-negative value.
 *
 * @return 0 if this Long is < 0, this Long otherwise
 */
fun Long.toNonNegativeTimestamp(): Long =
    coerceAtLeast(
        minimumValue = 0L,
    )
