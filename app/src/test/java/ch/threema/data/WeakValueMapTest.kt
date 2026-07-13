package ch.threema.data

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class WeakValueMapTest {
    @Test
    fun testReferences() {
        val map = WeakValueMap<String, Instant>()
        val date1 = map.getOrCreate("hello") { Instant.now() }
        val date2 = map.getOrCreate("hello") { Instant.now() }
        val date3 = map.getOrCreate("world") { Instant.now() }
        assertSame(date1, date2)
        assertNotSame(date1, date3)
        assertSame(date1, map.get("hello"))
        assertSame(date3, map.get("world"))
        assertNull(map.get("something-else"))
    }

    @Test
    fun testMissNull() {
        val map = WeakValueMap<String, String>()
        val string1 = map.getOrCreate("hello") { "guten tag!" }
        val string2 = map.getOrCreate("not-found") { null }
        assertEquals("guten tag!", string1)
        assertNull(string2)
    }
}
