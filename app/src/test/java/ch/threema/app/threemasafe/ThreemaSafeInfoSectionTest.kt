package ch.threema.app.threemasafe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonPrimitive

class ThreemaSafeInfoSectionTest {

    @Test
    fun `create should include version in json object`() {
        val section = ThreemaSafeInfoSection.create(version = 1, device = "1.2.3/de_DE")

        val jsonObject = section.getJsonObject()

        assertEquals(JsonPrimitive(1), jsonObject[ThreemaSafeInfoSection.TAG_VERSION])
    }

    @Test
    fun `create should include device in json object`() {
        val section = ThreemaSafeInfoSection.create(version = 1, device = "1.2.3/de_DE")

        val jsonObject = section.getJsonObject()

        assertEquals(JsonPrimitive("1.2.3/de_DE"), jsonObject[ThreemaSafeInfoSection.TAG_DEVICE])
    }

    @Test
    fun `create should only contain the two expected keys`() {
        val section = ThreemaSafeInfoSection.create(version = 1, device = "1.2.3/de_DE")

        val jsonObject = section.getJsonObject()

        assertEquals(
            setOf(ThreemaSafeInfoSection.TAG_VERSION, ThreemaSafeInfoSection.TAG_DEVICE),
            jsonObject.keys,
        )
    }

    @Test
    fun `create should throw if version is not 1`() {
        assertFailsWith<IllegalArgumentException> {
            ThreemaSafeInfoSection.create(version = 2, device = "1.2.3/de_DE")
        }
    }
}
