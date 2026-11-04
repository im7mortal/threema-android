package ch.threema.app.threemasafe

import ch.threema.app.services.DistributionListService
import ch.threema.common.booleanOrNull
import ch.threema.common.longOrNull
import ch.threema.common.string
import ch.threema.common.stringOrNull
import ch.threema.common.toByteArray
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.storage.models.DistributionListModel
import io.mockk.every
import io.mockk.mockk
import java.nio.ByteOrder
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import testdata.TestData

class ThreemaSafeDistributionListsSectionCreatorTest {

    private val distributionListServiceMock = mockk<DistributionListService>()

    private fun creator() = ThreemaSafeDistributionListsSectionCreator(
        distributionListService = distributionListServiceMock,
    )

    private fun distributionListModelMock(
        id: Long = 123456789L,
        name: String? = "Some List",
        createdAt: Instant = Instant.ofEpochMilli(1_000L),
        lastUpdate: Instant = Instant.ofEpochMilli(2_000L),
        conversationCategory: ConversationCategory = ConversationCategory.DEFAULT,
    ) = mockk<DistributionListModel> {
        every { this@mockk.id } returns id
        every { this@mockk.name } returns name
        every { this@mockk.createdAt } returns createdAt
        every { this@mockk.lastUpdate } returns lastUpdate
        every { this@mockk.conversationCategory } returns conversationCategory
    }

    private fun setup(
        distributionListModels: List<DistributionListModel>,
        members: Array<String> = emptyArray(),
    ) {
        every { distributionListServiceMock.getAll(any()) } returns distributionListModels
        every { distributionListServiceMock.getDistributionListIdentities(any()) } returns members
    }

    private fun firstDistributionListJsonObject() =
        creator().create().getJsonArray()[0].jsonObject

    @Test
    fun `create should map id as little endian hex string`() {
        setup(listOf(distributionListModelMock(id = 123456789L)))

        val jsonObject = firstDistributionListJsonObject()

        val expectedId = 123456789L.toByteArray(ByteOrder.LITTLE_ENDIAN).toHexString()
        assertEquals(expectedId, jsonObject[ThreemaSafeDistributionListsSection.TAG_ID]?.stringOrNull)
    }

    @Test
    fun `create should map name when present`() {
        setup(listOf(distributionListModelMock(name = "Some List")))

        val jsonObject = firstDistributionListJsonObject()

        assertEquals("Some List", jsonObject[ThreemaSafeDistributionListsSection.TAG_NAME]?.stringOrNull)
    }

    @Test
    fun `create should map name to empty string when null`() {
        setup(listOf(distributionListModelMock(name = null)))

        val jsonObject = firstDistributionListJsonObject()

        assertEquals("", jsonObject[ThreemaSafeDistributionListsSection.TAG_NAME]?.stringOrNull)
    }

    @Test
    fun `create should map created at to epoch millis`() {
        setup(listOf(distributionListModelMock(createdAt = Instant.ofEpochMilli(12_345L))))

        val jsonObject = firstDistributionListJsonObject()

        assertEquals(12_345L, jsonObject[ThreemaSafeDistributionListsSection.TAG_CREATED_AT]?.longOrNull)
    }

    @Test
    fun `create should map members from distribution list service`() {
        setup(
            distributionListModels = listOf(distributionListModelMock()),
            members = arrayOf(TestData.Identities.OTHER_1.value, TestData.Identities.OTHER_2.value),
        )

        val jsonObject = firstDistributionListJsonObject()

        val members = jsonObject[ThreemaSafeDistributionListsSection.TAG_MEMBERS]?.jsonArray?.map(JsonElement::string)
        assertEquals(setOf(TestData.Identities.OTHER_1.value, TestData.Identities.OTHER_2.value), members?.toSet())
    }

    @Test
    fun `create should map last update to epoch millis`() {
        setup(listOf(distributionListModelMock(lastUpdate = Instant.ofEpochMilli(5_000L))))

        val jsonObject = firstDistributionListJsonObject()

        assertEquals(5_000L, jsonObject[ThreemaSafeDistributionListsSection.TAG_LAST_UPDATE]?.longOrNull)
    }

    @Test
    fun `create should mark private false when conversation category is default`() {
        setup(listOf(distributionListModelMock(conversationCategory = ConversationCategory.DEFAULT)))

        val jsonObject = firstDistributionListJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeDistributionListsSection.TAG_PRIVATE]?.booleanOrNull)
    }

    @Test
    fun `create should mark private true when conversation category is protected`() {
        setup(listOf(distributionListModelMock(conversationCategory = ConversationCategory.PROTECTED)))

        val jsonObject = firstDistributionListJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeDistributionListsSection.TAG_PRIVATE]?.booleanOrNull)
    }

    @Test
    fun `create should produce one entry per distribution list model`() {
        setup(listOf(distributionListModelMock(id = 1L), distributionListModelMock(id = 2L)))

        val section = creator().create()

        assertEquals(2, section.getJsonArray().size)
    }
}
