package ch.threema.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimeExtensionsTest {
    @Test
    fun `difference between instants`() {
        val instant1 = Instant.ofEpochMilli(11L * 60 * 60 * 1000)
        val instant2 = Instant.ofEpochMilli(9L * 60 * 60 * 1000)

        assertEquals(2.hours, instant1 - instant2)
    }

    @Test
    fun `instant minus a duration`() {
        val instant1 = Instant.ofEpochMilli(11L * 60 * 60 * 1000)
        val instant2 = Instant.ofEpochMilli(9L * 60 * 60 * 1000)

        assertEquals(instant2, instant1 - 2.hours)
    }

    @Test
    fun `instant plus a duration`() {
        val instant1 = Instant.ofEpochMilli(11L * 60 * 60 * 1000)
        val instant2 = Instant.ofEpochMilli(9L * 60 * 60 * 1000)

        assertEquals(instant1, instant2 + 2.hours)
    }

    @Test
    fun `duration string format`() {
        // arrange
        val input: List<Duration> = listOf(
            Duration.ZERO,
            1.milliseconds,
            1.seconds,
            1.minutes,
            1.hours,
            200.hours,
            3.hours + 10.minutes + 3.seconds,
            3.hours + 2.minutes + 30.seconds,
        )

        // act
        val results: List<String> = input.map(Duration::toHMMSS)

        // assert
        assertContentEquals(
            expected = listOf(
                "00:00",
                "00:00",
                "00:01",
                "01:00",
                "1:00:00",
                "200:00:00",
                "3:10:03",
                "3:02:30",
            ),
            actual = results,
        )
    }

    @Test
    fun `normalize negative timestamps`() {
        assertEquals(0, (-1L).toNonNegativeTimestamp())
        assertEquals(0, (-79200000L).toNonNegativeTimestamp())
        assertEquals(0, Long.MIN_VALUE.toNonNegativeTimestamp())
    }

    @Test
    fun `normalize valid timestamps`() {
        assertEquals(0, 0L.toNonNegativeTimestamp())
        assertEquals(1, 1L.toNonNegativeTimestamp())
        assertEquals(1355270400000L, 1355270400000L.toNonNegativeTimestamp())
        assertEquals(Long.MAX_VALUE, Long.MAX_VALUE.toNonNegativeTimestamp())
    }

    @Test
    fun `is same day as`() {
        run {
            // 2 hours apart but within the same UTC day
            val time1 = OffsetDateTime.of(2026, 2, 24, 10, 0, 0, 0, ZoneOffset.UTC).toInstant()
            val time2 = OffsetDateTime.of(2026, 2, 24, 12, 0, 0, 0, ZoneOffset.UTC).toInstant()
            assertTrue(time1.isSameDayAs(time2, zoneId = ZoneOffset.UTC))
            assertFalse(time1.isSameDayAs(time2, zoneId = ZoneOffset.ofHours(-12)))
            assertTrue(time1.isSameDayAs(time2, zoneId = ZoneOffset.ofHours(-4)))
            assertTrue(time1.isSameDayAs(time2, zoneId = ZoneOffset.ofHours(4)))
            assertFalse(time1.isSameDayAs(time2, zoneId = ZoneOffset.ofHours(12)))
        }

        run {
            // 2 hours apart but not within the same UTC day
            val time1 = OffsetDateTime.of(2026, 2, 24, 23, 0, 0, 0, ZoneOffset.UTC).toInstant()
            val time2 = OffsetDateTime.of(2026, 2, 25, 1, 0, 0, 0, ZoneOffset.UTC).toInstant()
            assertFalse(time1.isSameDayAs(time2, zoneId = ZoneOffset.UTC))
            assertTrue(time1.isSameDayAs(time2, zoneId = ZoneOffset.ofHours(-12)))
            assertTrue(time1.isSameDayAs(time2, zoneId = ZoneOffset.ofHours(-4)))
            assertTrue(time1.isSameDayAs(time2, zoneId = ZoneOffset.ofHours(4)))
            assertTrue(time1.isSameDayAs(time2, zoneId = ZoneOffset.ofHours(12)))
        }
    }
}
