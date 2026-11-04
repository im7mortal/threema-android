package ch.threema.app

import ch.threema.app.work.MdmSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MdmSourceTest {

    @Test
    fun `returns null when no mdm restrictions are active`() {
        val source = MdmSource(
            hasThreemaMdmRestrictions = false,
            hasExternalMdmRestrictions = false,
        )

        assertNull(source.getStringRepresentation())
    }

    @Test
    fun `returns m when only threema mdm restrictions are active`() {
        val source = MdmSource(
            hasThreemaMdmRestrictions = true,
            hasExternalMdmRestrictions = false,
        )

        assertEquals("m", source.getStringRepresentation())
    }

    @Test
    fun `returns e when only external mdm restrictions are active`() {
        val source = MdmSource(
            hasThreemaMdmRestrictions = false,
            hasExternalMdmRestrictions = true,
        )

        assertEquals("e", source.getStringRepresentation())
    }

    @Test
    fun `returns me when both threema and external mdm restrictions are active`() {
        val source = MdmSource(
            hasThreemaMdmRestrictions = true,
            hasExternalMdmRestrictions = true,
        )

        assertEquals("me", source.getStringRepresentation())
    }
}
