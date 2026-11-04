package ch.threema.app.threemasafe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class ThreemaSafeDistributionListsSectionTest {

    private fun distributionList(
        id: String = "0123456789abcdef",
        name: String = "Some List",
        createdAt: Long = 1_000L,
        members: List<String> = listOf("76543210", "01234567"),
        lastUpdate: Long = 2_000L,
        private: Boolean = false,
    ) = ThreemaSafeDistributionListsSection.ThreemaSafeDistributionList(
        id = id,
        name = name,
        createdAt = createdAt,
        members = members,
        lastUpdate = lastUpdate,
        private = private,
    )

    private fun jsonObjectOf(distributionList: ThreemaSafeDistributionListsSection.ThreemaSafeDistributionList) =
        ThreemaSafeDistributionListsSection.create(listOf(distributionList)).getJsonArray()[0].jsonObject

    @Test
    fun `create should include id in json object`() {
        val jsonObject = jsonObjectOf(distributionList(id = "0123456789abcdef"))

        assertEquals(JsonPrimitive("0123456789abcdef"), jsonObject[ThreemaSafeDistributionListsSection.TAG_ID])
    }

    @Test
    fun `create should include name in json object`() {
        val jsonObject = jsonObjectOf(distributionList(name = "Some List"))

        assertEquals(JsonPrimitive("Some List"), jsonObject[ThreemaSafeDistributionListsSection.TAG_NAME])
    }

    @Test
    fun `create should include created at in json object`() {
        val jsonObject = jsonObjectOf(distributionList(createdAt = 1_234L))

        assertEquals(JsonPrimitive(1_234L), jsonObject[ThreemaSafeDistributionListsSection.TAG_CREATED_AT])
    }

    @Test
    fun `create should include members in json object`() {
        val jsonObject = jsonObjectOf(distributionList(members = listOf("76543210", "01234567")))

        assertEquals(
            JsonArray(listOf(JsonPrimitive("76543210"), JsonPrimitive("01234567"))),
            jsonObject[ThreemaSafeDistributionListsSection.TAG_MEMBERS],
        )
    }

    @Test
    fun `create should include last update in json object`() {
        val jsonObject = jsonObjectOf(distributionList(lastUpdate = 5_000L))

        assertEquals(JsonPrimitive(5_000L), jsonObject[ThreemaSafeDistributionListsSection.TAG_LAST_UPDATE])
    }

    @Test
    fun `create should include private in json object`() {
        val jsonObject = jsonObjectOf(distributionList(private = true))

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeDistributionListsSection.TAG_PRIVATE])
    }

    @Test
    fun `create should only contain the expected keys`() {
        val jsonObject = jsonObjectOf(distributionList())

        assertEquals(
            setOf(
                ThreemaSafeDistributionListsSection.TAG_ID,
                ThreemaSafeDistributionListsSection.TAG_NAME,
                ThreemaSafeDistributionListsSection.TAG_CREATED_AT,
                ThreemaSafeDistributionListsSection.TAG_MEMBERS,
                ThreemaSafeDistributionListsSection.TAG_LAST_UPDATE,
                ThreemaSafeDistributionListsSection.TAG_PRIVATE,
            ),
            jsonObject.keys,
        )
    }

    @Test
    fun `create should produce one entry per distribution list`() {
        val section = ThreemaSafeDistributionListsSection.create(
            listOf(distributionList(id = "0123456789abcdef"), distributionList(id = "fedcba9876543210")),
        )

        val jsonArray = section.getJsonArray()

        assertEquals(2, jsonArray.size)
    }

    @Test
    fun `ThreemaSafeDistributionList should throw if id is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            distributionList(id = "not-a-valid-id")
        }
    }

    @Test
    fun `ThreemaSafeDistributionList should throw if created at is negative`() {
        assertFailsWith<IllegalArgumentException> {
            distributionList(createdAt = -1L)
        }
    }

    @Test
    fun `ThreemaSafeDistributionList should throw if a member identity is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            distributionList(members = listOf("76543210", "invalid"))
        }
    }

    @Test
    fun `ThreemaSafeDistributionList should throw if last update is negative`() {
        assertFailsWith<IllegalArgumentException> {
            distributionList(lastUpdate = -1L)
        }
    }
}
