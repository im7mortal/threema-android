package ch.threema.common

import kotlin.test.Test
import kotlin.test.assertEquals

class MapExtensions {
    @Test
    fun `filter not null keys for empty map`() {
        val emptyMap = emptyMap<Unit?, Unit>()
        assertEquals(
            expected = emptyMap(),
            actual = emptyMap.filterNotNullKeys(),
        )
    }

    @Test
    fun `filter not null keys for map without null keys`() {
        val map = mapOf<Unit?, Unit>(Unit to Unit)
        assertEquals(
            expected = mapOf(Unit to Unit),
            actual = map.filterNotNullKeys(),
        )
    }

    @Test
    fun `filter not null keys for map with null key`() {
        val map = mapOf(
            1 to 1,
            null to 0,
            -1 to -1,
            2 to 2,
        )
        assertEquals(
            expected = mapOf(
                1 to 1,
                -1 to -1,
                2 to 2,
            ),
            actual = map.filterNotNullKeys(),
        )
    }
}
