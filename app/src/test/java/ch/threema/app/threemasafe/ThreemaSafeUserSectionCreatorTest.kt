package ch.threema.app.threemasafe

import android.graphics.Bitmap
import ch.threema.android.toJpegByteArray
import ch.threema.android.toPngByteArray
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.services.UserService
import ch.threema.app.utils.BitmapUtil
import ch.threema.common.Base64
import ch.threema.common.string
import ch.threema.common.stringOrNull
import ch.threema.domain.stores.IdentityStore
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ThreemaSafeUserSectionCreatorTest {

    private val identityStoreMock = mockk<IdentityStore>()
    private val userServiceMock = mockk<UserService>()
    private val fileServiceMock = mockk<FileService>()
    private val preferenceServiceMock = mockk<PreferenceService>()
    private val profilePictureRecipientsServiceMock = mockk<ProfilePictureRecipientsService>()

    private fun creator() = ThreemaSafeUserSectionCreator(
        identityStore = identityStoreMock,
        userService = userServiceMock,
        fileService = fileServiceMock,
        preferenceService = preferenceServiceMock,
        profilePictureRecipientsService = profilePictureRecipientsServiceMock,
    )

    private fun setupDefaults() {
        every { identityStoreMock.getPrivateKey() } returns ByteArray(32) { 1 }
        every { identityStoreMock.getIdentityString() } returns "01234567"
        every { identityStoreMock.requireIdentityString() } returns "01234567"
        every { userServiceMock.publicNickname } returns "nickname"
        every { userServiceMock.mobileLinkingState } returns UserService.LinkingState_NONE
        every { userServiceMock.emailLinkingState } returns UserService.LinkingState_NONE
        every { userServiceMock.linkedMobileE164 } returns null
        every { userServiceMock.linkedEmail } returns ""
        every { fileServiceMock.getUserDefinedProfilePicture(any()) } returns null
        every { preferenceServiceMock.getProfilePicRelease() } returns PreferenceService.PROFILEPIC_RELEASE_NOBODY
        every { profilePictureRecipientsServiceMock.all } returns emptyArray()
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(Bitmap::toPngByteArray)
        unmockkStatic(BitmapUtil::class)
    }

    // --- private key ---

    @Test
    fun `create should throw if no private key exists`() {
        setupDefaults()
        every { identityStoreMock.getPrivateKey() } returns null

        assertFailsWith<IllegalStateException> {
            creator().create()
        }
    }

    @Test
    fun `create should base64 encode the private key`() {
        setupDefaults()
        val privateKeyBytes = ByteArray(32) { it.toByte() }
        every { identityStoreMock.getPrivateKey() } returns privateKeyBytes

        val section = creator().create()

        assertEquals(
            Base64.encode(privateKeyBytes),
            section.getJsonObject()[ThreemaSafeUserSection.TAG_PRIVATE_KEY]?.stringOrNull,
        )
    }

    // --- nickname ---

    @Test
    fun `create should use public nickname from user service`() {
        setupDefaults()
        every { userServiceMock.publicNickname } returns "somenickname"

        val section = creator().create()

        assertEquals(
            "somenickname",
            section.getJsonObject()[ThreemaSafeUserSection.TAG_NICKNAME]?.stringOrNull,
        )
    }

    // --- profile picture ---

    @Test
    fun `create should not include profile picture when none is set`() {
        setupDefaults()
        every { fileServiceMock.getUserDefinedProfilePicture(any()) } returns null

        val section = creator().create()

        assertEquals(false, section.getJsonObject().containsKey(ThreemaSafeUserSection.TAG_PROFILE_PIC))
    }

    @Test
    fun `create should not include profile picture when bitmap conversion throws`() {
        setupDefaults()
        val bitmapMock = mockk<Bitmap> {
            every { width } returns 100
            every { height } returns 100
        }
        every { fileServiceMock.getUserDefinedProfilePicture(any()) } returns bitmapMock
        mockkStatic(Bitmap::toPngByteArray)
        every { any<Bitmap>().toPngByteArray(any()) } throws RuntimeException("failed")

        val section = creator().create()

        assertEquals(false, section.getJsonObject().containsKey(ThreemaSafeUserSection.TAG_PROFILE_PIC))
    }

    @Test
    fun `create should include base64 encoded profile picture without resizing when small enough`() {
        setupDefaults()
        val bitmapMock = mockk<Bitmap> {
            every { recycle() } just runs
            every { width } returns 200
            every { height } returns 200
        }
        every { fileServiceMock.getUserDefinedProfilePicture(any()) } returns bitmapMock
        val encodedBytes = byteArrayOf(1, 2, 3)
        mockkStatic(Bitmap::toJpegByteArray)
        every { bitmapMock.toJpegByteArray(60) } returns encodedBytes
        mockkStatic(BitmapUtil::class)
        every { BitmapUtil.bitmapToByteArray(bitmapMock, Bitmap.CompressFormat.JPEG, 60) } returns encodedBytes

        val section = creator().create()

        assertEquals(
            Base64.encode(encodedBytes),
            section.getJsonObject()[ThreemaSafeUserSection.TAG_PROFILE_PIC]?.stringOrNull,
        )
        verify(exactly = 0) { BitmapUtil.resizeBitmap(any(), any(), any()) }
    }

    @Test
    fun `create should resize profile picture when larger than max width`() {
        setupDefaults()
        val bitmapMock = mockk<Bitmap> {
            every { recycle() } just runs
            every { width } returns 800
            every { height } returns 800
        }
        val resizedBitmapMock = mockk<Bitmap> {
            every { recycle() } just runs
        }
        every { fileServiceMock.getUserDefinedProfilePicture(any()) } returns bitmapMock
        val encodedBytes = byteArrayOf(4, 5, 6)
        mockkStatic(Bitmap::toJpegByteArray)
        mockkStatic(BitmapUtil::class)
        every { BitmapUtil.resizeBitmap(bitmapMock, 400, 400) } returns resizedBitmapMock
        every { resizedBitmapMock.toJpegByteArray(60) } returns encodedBytes
        every { BitmapUtil.bitmapToByteArray(resizedBitmapMock, Bitmap.CompressFormat.JPEG, 60) } returns encodedBytes

        val section = creator().create()

        assertEquals(
            Base64.encode(encodedBytes),
            section.getJsonObject()[ThreemaSafeUserSection.TAG_PROFILE_PIC]?.stringOrNull,
        )
        verify(exactly = 1) { BitmapUtil.resizeBitmap(bitmapMock, 400, 400) }
    }

    // --- profile pic release ---

    @Test
    fun `create should map profile pic release everyone`() {
        setupDefaults()
        every { preferenceServiceMock.getProfilePicRelease() } returns PreferenceService.PROFILEPIC_RELEASE_EVERYONE

        val section = creator().create()

        val values = section.getJsonObject()[ThreemaSafeUserSection.TAG_PROFILE_PIC_RELEASE]?.jsonArray
        assertEquals(listOf("*"), values?.map(JsonElement::string))
    }

    @Test
    fun `create should map profile pic release allow list`() {
        setupDefaults()
        every { preferenceServiceMock.getProfilePicRelease() } returns PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST
        every { profilePictureRecipientsServiceMock.all } returns arrayOf("01234567", "76543210")

        val section = creator().create()

        val values = section.getJsonObject()[ThreemaSafeUserSection.TAG_PROFILE_PIC_RELEASE]?.jsonArray
        assertEquals(setOf("01234567", "76543210"), values?.map(JsonElement::string)?.toSet())
    }

    @Test
    fun `create should map profile pic release none for any other value`() {
        setupDefaults()
        every { preferenceServiceMock.getProfilePicRelease() } returns PreferenceService.PROFILEPIC_RELEASE_NOBODY

        val section = creator().create()

        val values = section.getJsonObject()[ThreemaSafeUserSection.TAG_PROFILE_PIC_RELEASE]?.jsonArray
        assertEquals(JsonArray(listOf(JsonNull)), values)
    }

    @Test
    fun `create should map profile pic release none if allow list is empty`() {
        setupDefaults()
        every { preferenceServiceMock.getProfilePicRelease() } returns PreferenceService.PROFILEPIC_RELEASE_ALLOW_LIST
        every { profilePictureRecipientsServiceMock.all } returns emptyArray<String>()

        val section = creator().create()

        val values = section.getJsonObject()[ThreemaSafeUserSection.TAG_PROFILE_PIC_RELEASE]?.jsonArray
        assertEquals(JsonArray(listOf(JsonNull)), values)
    }

    // --- links ---

    @Test
    fun `create should include phone link when mobile is linked`() {
        setupDefaults()
        every { userServiceMock.mobileLinkingState } returns UserService.LinkingState_LINKED
        every { userServiceMock.linkedMobileE164 } returns "+41791234567"

        val section = creator().create()

        val links = section.getJsonObject()[ThreemaSafeUserSection.TAG_LINKS]?.jsonArray
        assertEquals(1, links?.size)
        val link = links!![0].jsonObject
        assertEquals(
            ThreemaSafeUserSection.TAG_LINK_TYPE_MOBILE,
            link[ThreemaSafeUserSection.TAG_LINK_TYPE]?.stringOrNull,
        )
        assertEquals("41791234567", link[ThreemaSafeUserSection.TAG_LINK_VALUE]?.stringOrNull)
    }

    @Test
    fun `create should not include phone link when mobile is linked but number is null`() {
        setupDefaults()
        every { userServiceMock.mobileLinkingState } returns UserService.LinkingState_LINKED
        every { userServiceMock.linkedMobileE164 } returns null

        val section = creator().create()

        val links = section.getJsonObject()[ThreemaSafeUserSection.TAG_LINKS]?.jsonArray
        assertEquals(0, links?.size)
    }

    @Test
    fun `create should not include phone link when mobile is not linked`() {
        setupDefaults()
        every { userServiceMock.mobileLinkingState } returns UserService.LinkingState_NONE
        every { userServiceMock.linkedMobileE164 } returns "+41791234567"

        val section = creator().create()

        val links = section.getJsonObject()[ThreemaSafeUserSection.TAG_LINKS]?.jsonArray
        assertEquals(0, links?.size)
    }

    @Test
    fun `create should include email link when email is linked`() {
        setupDefaults()
        every { userServiceMock.emailLinkingState } returns UserService.LinkingState_LINKED
        every { userServiceMock.linkedEmail } returns "test@example.com"

        val section = creator().create()

        val links = section.getJsonObject()[ThreemaSafeUserSection.TAG_LINKS]?.jsonArray
        assertEquals(1, links?.size)
        val link = links!![0].jsonObject
        assertEquals(
            ThreemaSafeUserSection.TAG_LINK_TYPE_EMAIL,
            link[ThreemaSafeUserSection.TAG_LINK_TYPE]?.stringOrNull,
        )
        assertEquals("test@example.com", link[ThreemaSafeUserSection.TAG_LINK_VALUE]?.stringOrNull)
    }

    @Test
    fun `create should not include email link when email is blank`() {
        setupDefaults()
        every { userServiceMock.emailLinkingState } returns UserService.LinkingState_LINKED
        every { userServiceMock.linkedEmail } returns ""

        val section = creator().create()

        val links = section.getJsonObject()[ThreemaSafeUserSection.TAG_LINKS]?.jsonArray
        assertEquals(0, links?.size)
    }

    @Test
    fun `create should not include email link when email is not linked`() {
        setupDefaults()
        every { userServiceMock.emailLinkingState } returns UserService.LinkingState_NONE
        every { userServiceMock.linkedEmail } returns "test@example.com"

        val section = creator().create()

        val links = section.getJsonObject()[ThreemaSafeUserSection.TAG_LINKS]?.jsonArray
        assertEquals(0, links?.size)
    }

    @Test
    fun `create should include both links when both are linked`() {
        setupDefaults()
        every { userServiceMock.mobileLinkingState } returns UserService.LinkingState_LINKED
        every { userServiceMock.linkedMobileE164 } returns "+41791234567"
        every { userServiceMock.emailLinkingState } returns UserService.LinkingState_LINKED
        every { userServiceMock.linkedEmail } returns "test@example.com"

        val section = creator().create()

        val links = section.getJsonObject()[ThreemaSafeUserSection.TAG_LINKS]?.jsonArray
        assertEquals(2, links?.size)
    }
}
