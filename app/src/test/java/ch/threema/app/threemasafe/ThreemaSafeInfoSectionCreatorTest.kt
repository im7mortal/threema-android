package ch.threema.app.threemasafe

import ch.threema.common.intOrNull
import ch.threema.common.stringOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreemaSafeInfoSectionCreatorTest {

    @Test
    fun `create should use protocol version 1`() {
        val creator = ThreemaSafeInfoSectionCreator(appVersion = "1.2.3", locale = "de_DE")

        val section = creator.create()

        assertEquals(1, section.getJsonObject()[ThreemaSafeInfoSection.TAG_VERSION]?.intOrNull)
    }

    @Test
    fun `create should combine app version and locale into device string`() {
        val creator = ThreemaSafeInfoSectionCreator(appVersion = "1.2.3", locale = "de_DE")

        val section = creator.create()

        assertEquals(
            "1.2.3A/de_DE",
            section.getJsonObject()[ThreemaSafeInfoSection.TAG_DEVICE]?.stringOrNull,
        )
    }
}
