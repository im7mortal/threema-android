package ch.threema.app.threemasafe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ThreemaSafeUserSectionTest {

    private val validPrivateKey = "A".repeat(43) + "="

    private fun userInfo(
        privateKey: String = validPrivateKey,
        nickname: String = "nickname",
        profilePic: String? = null,
        profilePicRelease: ThreemaSafeUserSection.ProfilePicRelease = ThreemaSafeUserSection.ProfilePicRelease.None,
        links: List<ThreemaSafeUserSection.LinkItem> = emptyList(),
    ) = ThreemaSafeUserSection.ThreemaSafeUserInfo(
        privateKey = privateKey,
        nickname = nickname,
        profilePic = profilePic,
        profilePicRelease = profilePicRelease,
        links = links,
    )

    @Test
    fun `create should include private key in json object`() {
        val section = ThreemaSafeUserSection.create(userInfo(privateKey = validPrivateKey))

        val jsonObject = section.getJsonObject()

        assertEquals(JsonPrimitive(validPrivateKey), jsonObject[ThreemaSafeUserSection.TAG_PRIVATE_KEY])
    }

    @Test
    fun `create should include nickname in json object`() {
        val section = ThreemaSafeUserSection.create(userInfo(nickname = "nick"))

        val jsonObject = section.getJsonObject()

        assertEquals(JsonPrimitive("nick"), jsonObject[ThreemaSafeUserSection.TAG_NICKNAME])
    }

    @Test
    fun `create should include profile pic in json object when present`() {
        val section = ThreemaSafeUserSection.create(userInfo(profilePic = "profilepicdata"))

        val jsonObject = section.getJsonObject()

        assertEquals(JsonPrimitive("profilepicdata"), jsonObject[ThreemaSafeUserSection.TAG_PROFILE_PIC])
    }

    @Test
    fun `create should not include profile pic key when null`() {
        val section = ThreemaSafeUserSection.create(userInfo(profilePic = null))

        val jsonObject = section.getJsonObject()

        assertEquals(false, jsonObject.containsKey(ThreemaSafeUserSection.TAG_PROFILE_PIC))
    }

    @Test
    fun `create should include profile pic release values for Everyone`() {
        val section = ThreemaSafeUserSection.create(
            userInfo(profilePicRelease = ThreemaSafeUserSection.ProfilePicRelease.Everyone),
        )

        val jsonObject = section.getJsonObject()

        assertEquals(
            JsonArray(listOf(JsonPrimitive("*"))),
            jsonObject[ThreemaSafeUserSection.TAG_PROFILE_PIC_RELEASE],
        )
    }

    @Test
    fun `create should include profile pic release values for None`() {
        val section = ThreemaSafeUserSection.create(
            userInfo(profilePicRelease = ThreemaSafeUserSection.ProfilePicRelease.None),
        )

        val jsonObject = section.getJsonObject()

        assertEquals(
            JsonArray(listOf(JsonNull)),
            jsonObject[ThreemaSafeUserSection.TAG_PROFILE_PIC_RELEASE],
        )
    }

    @Test
    fun `create should include profile pic release values for AllowList`() {
        val section = ThreemaSafeUserSection.create(
            userInfo(
                profilePicRelease = ThreemaSafeUserSection.ProfilePicRelease.AllowList(
                    listOf("ECHOECHO", "01234567"),
                ),
            ),
        )

        val jsonObject = section.getJsonObject()

        assertEquals(
            JsonArray(listOf(JsonPrimitive("ECHOECHO"), JsonPrimitive("01234567"))),
            jsonObject[ThreemaSafeUserSection.TAG_PROFILE_PIC_RELEASE],
        )
    }

    @Test
    fun `create should include links in json object`() {
        val section = ThreemaSafeUserSection.create(
            userInfo(
                links = listOf(
                    ThreemaSafeUserSection.LinkItem(
                        type = ThreemaSafeUserSection.LinkItemType.PHONE,
                        name = null,
                        value = "+41791234567",
                    ),
                    ThreemaSafeUserSection.LinkItem(
                        type = ThreemaSafeUserSection.LinkItemType.EMAIL,
                        name = "Private",
                        value = "test@example.com",
                    ),
                ),
            ),
        )

        val jsonObject = section.getJsonObject()

        assertEquals(
            JsonArray(
                listOf(
                    buildJsonObject {
                        put(ThreemaSafeUserSection.TAG_LINK_TYPE, ThreemaSafeUserSection.TAG_LINK_TYPE_MOBILE)
                        put(ThreemaSafeUserSection.TAG_LINK_VALUE, "+41791234567")
                    },
                    buildJsonObject {
                        put(ThreemaSafeUserSection.TAG_LINK_TYPE, ThreemaSafeUserSection.TAG_LINK_TYPE_EMAIL)
                        put(ThreemaSafeUserSection.TAG_LINK_NAME, "Private")
                        put(ThreemaSafeUserSection.TAG_LINK_VALUE, "test@example.com")
                    },
                ),
            ),
            jsonObject[ThreemaSafeUserSection.TAG_LINKS],
        )
    }

    @Test
    fun `ThreemaSafeUserInfo should throw if private key is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            userInfo(privateKey = "invalid")
        }
    }

    @Test
    fun `ThreemaSafeUserInfo should throw if profile pic is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            userInfo(profilePic = "not valid base64!")
        }
    }

    @Test
    fun `AllowList should throw if values are empty`() {
        assertFailsWith<IllegalArgumentException> {
            ThreemaSafeUserSection.ProfilePicRelease.AllowList(emptyList())
        }
    }

    @Test
    fun `AllowList should throw if an identity is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            ThreemaSafeUserSection.ProfilePicRelease.AllowList(listOf("invalid"))
        }
    }
}
