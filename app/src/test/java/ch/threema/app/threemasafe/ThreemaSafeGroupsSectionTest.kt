package ch.threema.app.threemasafe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class ThreemaSafeGroupsSectionTest {

    private fun group(
        id: String = "0123456789abcdef",
        creator: String = "76543210",
        groupname: String? = "Some Group",
        createdAt: Long = 1_000L,
        members: List<String> = listOf("76543210", "01234567"),
        deleted: Boolean = false,
        lastUpdate: Long? = 2_000L,
        private: Boolean = false,
    ) = ThreemaSafeGroupsSection.ThreemaSafeGroup(
        id = id,
        creator = creator,
        groupname = groupname,
        createdAt = createdAt,
        members = members,
        deleted = deleted,
        lastUpdate = lastUpdate,
        private = private,
    )

    private fun jsonObjectOf(groups: List<ThreemaSafeGroupsSection.ThreemaSafeGroup>) =
        ThreemaSafeGroupsSection.create(groups).getJsonArray()[0].jsonObject

    @Test
    fun `create should include id in json object`() {
        val jsonObject = jsonObjectOf(listOf(group(id = "0123456789abcdef")))

        assertEquals(JsonPrimitive("0123456789abcdef"), jsonObject[ThreemaSafeGroupsSection.TAG_ID])
    }

    @Test
    fun `create should include creator in json object`() {
        val jsonObject = jsonObjectOf(listOf(group(creator = "01234567")))

        assertEquals(JsonPrimitive("01234567"), jsonObject[ThreemaSafeGroupsSection.TAG_CREATOR])
    }

    @Test
    fun `create should include groupname when present`() {
        val jsonObject = jsonObjectOf(listOf(group(groupname = "Some Group")))

        assertEquals(JsonPrimitive("Some Group"), jsonObject[ThreemaSafeGroupsSection.TAG_NAME])
    }

    @Test
    fun `create should not include groupname key when null`() {
        val jsonObject = jsonObjectOf(listOf(group(groupname = null)))

        assertEquals(false, jsonObject.containsKey(ThreemaSafeGroupsSection.TAG_NAME))
    }

    @Test
    fun `create should include created at in json object`() {
        val jsonObject = jsonObjectOf(listOf(group(createdAt = 1_234L)))

        assertEquals(JsonPrimitive(1_234L), jsonObject[ThreemaSafeGroupsSection.TAG_CREATED_AT])
    }

    @Test
    fun `create should include members in json object`() {
        val jsonObject = jsonObjectOf(listOf(group(members = listOf("76543210", "01234567"))))

        assertEquals(
            JsonArray(listOf(JsonPrimitive("76543210"), JsonPrimitive("01234567"))),
            jsonObject[ThreemaSafeGroupsSection.TAG_MEMBERS],
        )
    }

    @Test
    fun `create should include deleted in json object`() {
        val jsonObject = jsonObjectOf(listOf(group(deleted = false)))

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeGroupsSection.TAG_DELETED])
    }

    @Test
    fun `create should include last update when present`() {
        val jsonObject = jsonObjectOf(listOf(group(lastUpdate = 5_000L)))

        assertEquals(JsonPrimitive(5_000L), jsonObject[ThreemaSafeGroupsSection.TAG_LAST_UPDATE])
    }

    @Test
    fun `create should not include last update key when null`() {
        val jsonObject = jsonObjectOf(listOf(group(lastUpdate = null)))

        assertEquals(false, jsonObject.containsKey(ThreemaSafeGroupsSection.TAG_LAST_UPDATE))
    }

    @Test
    fun `create should include private (true) in json object`() {
        val jsonObject = jsonObjectOf(listOf(group(private = true)))

        assertEquals(JsonPrimitive(true), jsonObject[ThreemaSafeGroupsSection.TAG_PRIVATE])
    }

    @Test
    fun `create should include private (false) in json object`() {
        val jsonObject = jsonObjectOf(listOf(group(private = false)))

        assertEquals(JsonPrimitive(false), jsonObject[ThreemaSafeGroupsSection.TAG_PRIVATE])
    }

    @Test
    fun `create should only contain the expected keys when groupname and last update are null`() {
        val jsonObject = jsonObjectOf(listOf(group(groupname = null, lastUpdate = null)))

        assertEquals(
            setOf(
                ThreemaSafeGroupsSection.TAG_ID,
                ThreemaSafeGroupsSection.TAG_CREATOR,
                ThreemaSafeGroupsSection.TAG_CREATED_AT,
                ThreemaSafeGroupsSection.TAG_MEMBERS,
                ThreemaSafeGroupsSection.TAG_DELETED,
                ThreemaSafeGroupsSection.TAG_PRIVATE,
            ),
            jsonObject.keys,
        )
    }

    @Test
    fun `create should produce one entry per group`() {
        val section = ThreemaSafeGroupsSection.create(
            listOf(group(id = "0123456789abcdef"), group(id = "fedcba9876543210")),
        )

        val jsonArray = section.getJsonArray()

        assertEquals(2, jsonArray.size)
    }

    @Test
    fun `ThreemaSafeGroup should throw if id is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            group(id = "not-a-valid-id")
        }
    }

    @Test
    fun `ThreemaSafeGroup should throw if creator identity is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            group(creator = "invalid")
        }
    }

    @Test
    fun `ThreemaSafeGroup should throw if created at is negative`() {
        assertFailsWith<IllegalArgumentException> {
            group(createdAt = -1L)
        }
    }

    @Test
    fun `ThreemaSafeGroup should throw if a member identity is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            group(members = listOf("76543210", "invalid"))
        }
    }

    @Test
    fun `ThreemaSafeGroup should throw if last update is negative`() {
        assertFailsWith<IllegalArgumentException> {
            group(lastUpdate = -1L)
        }
    }
}
