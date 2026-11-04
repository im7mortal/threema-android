package ch.threema.app.threemasafe

import ch.threema.common.booleanOrNull
import ch.threema.common.longOrNull
import ch.threema.common.string
import ch.threema.common.stringOrNull
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.UserState
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import testdata.TestData

class ThreemaSafeGroupsSectionCreatorTest {

    private val identityProviderMock = mockk<IdentityProvider>()
    private val groupModelRepositoryMock = mockk<GroupModelRepository>()

    private fun creator() = ThreemaSafeGroupsSectionCreator(
        identityProvider = identityProviderMock,
        groupModelRepository = groupModelRepositoryMock,
    )

    private fun createGroupModelMock(data: GroupModelData?) = mockk<GroupModel> {
        every { this@mockk.data } returns data
    }

    private fun setup(
        groupModels: List<GroupModel>,
        myIdentity: String = TestData.Identities.ME.value,
    ) {
        every { groupModelRepositoryMock.getAll() } returns groupModels
        every { identityProviderMock.requireIdentityString() } returns myIdentity
    }

    private fun firstGroupJsonObject() =
        creator().create().getJsonArray()[0].jsonObject

    @Test
    fun `create should exclude group models with null data`() {
        setup(
            groupModels = listOf(
                createGroupModelMock(null),
                createGroupModelMock(TestData.createGroupModelData()),
            ),
        )

        val section = creator().create()

        assertEquals(1, section.getJsonArray().size)
    }

    @Test
    fun `create should map id and creator from group identity`() {
        val groupId = GroupId(42)
        setup(
            listOf(
                createGroupModelMock(
                    TestData.createGroupModelData(
                        groupIdentity = GroupIdentity(
                            creatorIdentity = TestData.Identities.OTHER_1.value,
                            groupId = groupId.toLong(),
                        ),
                    ),
                ),
            ),
        )

        val jsonObject = firstGroupJsonObject()

        assertEquals(
            groupId.toString(),
            jsonObject[ThreemaSafeGroupsSection.TAG_ID]?.stringOrNull,
        )
        assertEquals(
            TestData.Identities.OTHER_1.value,
            jsonObject[ThreemaSafeGroupsSection.TAG_CREATOR]?.stringOrNull,
        )
    }

    @Test
    fun `create should map groupname`() {
        setup(listOf(createGroupModelMock(TestData.createGroupModelData(name = "Some Group"))))

        val jsonObject = firstGroupJsonObject()

        assertEquals("Some Group", jsonObject[ThreemaSafeGroupsSection.TAG_NAME]?.stringOrNull)
    }

    @Test
    fun `create should map created at to epoch millis`() {
        setup(listOf(createGroupModelMock(TestData.createGroupModelData(createdAt = Instant.ofEpochMilli(12_345L)))))

        val jsonObject = firstGroupJsonObject()

        assertEquals(12_345L, jsonObject[ThreemaSafeGroupsSection.TAG_CREATED_AT]?.longOrNull)
    }

    @Test
    fun `create should include own identity in members when user is a member`() {
        setup(
            groupModels = listOf(
                createGroupModelMock(
                    TestData.createGroupModelData(
                        userState = UserState.MEMBER,
                        otherMembers = setOf(TestData.Identities.OTHER_2.value),
                    ),
                ),
            ),
            myIdentity = TestData.Identities.ME.value,
        )

        val jsonObject = firstGroupJsonObject()

        val members = jsonObject[ThreemaSafeGroupsSection.TAG_MEMBERS]?.jsonArray?.map(JsonElement::string)
        assertEquals(setOf(TestData.Identities.OTHER_2.value, TestData.Identities.ME.value), members?.toSet())
    }

    @Test
    fun `create should not include own identity in members when user is not a member`() {
        setup(
            groupModels = listOf(
                createGroupModelMock(
                    TestData.createGroupModelData(
                        userState = UserState.KICKED,
                        otherMembers = setOf(TestData.Identities.OTHER_2.value),
                    ),
                ),
            ),
            myIdentity = TestData.Identities.ME.value,
        )

        val jsonObject = firstGroupJsonObject()

        val members = jsonObject[ThreemaSafeGroupsSection.TAG_MEMBERS]?.jsonArray?.map(JsonElement::string)
        assertEquals(setOf(TestData.Identities.OTHER_2.value), members?.toSet())
    }

    @Test
    fun `create should always mark group as not deleted`() {
        setup(listOf(createGroupModelMock(TestData.createGroupModelData())))

        val jsonObject = firstGroupJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeGroupsSection.TAG_DELETED]?.booleanOrNull)
    }

    @Test
    fun `create should map last update when present`() {
        setup(listOf(createGroupModelMock(TestData.createGroupModelData(lastUpdate = Instant.ofEpochMilli(5_000L)))))

        val jsonObject = firstGroupJsonObject()

        assertEquals(5_000L, jsonObject[ThreemaSafeGroupsSection.TAG_LAST_UPDATE]?.longOrNull)
    }

    @Test
    fun `create should not include last update key when null`() {
        setup(listOf(createGroupModelMock(TestData.createGroupModelData(lastUpdate = null))))

        val jsonObject = firstGroupJsonObject()

        assertEquals(false, jsonObject.containsKey(ThreemaSafeGroupsSection.TAG_LAST_UPDATE))
    }

    @Test
    fun `create should mark private false when conversation category is default`() {
        setup(listOf(createGroupModelMock(TestData.createGroupModelData(conversationCategory = ConversationCategory.DEFAULT))))

        val jsonObject = firstGroupJsonObject()

        assertEquals(false, jsonObject[ThreemaSafeGroupsSection.TAG_PRIVATE]?.booleanOrNull)
    }

    @Test
    fun `create should mark private true when conversation category is protected`() {
        setup(listOf(createGroupModelMock(TestData.createGroupModelData(conversationCategory = ConversationCategory.PROTECTED))))

        val jsonObject = firstGroupJsonObject()

        assertEquals(true, jsonObject[ThreemaSafeGroupsSection.TAG_PRIVATE]?.booleanOrNull)
    }
}
