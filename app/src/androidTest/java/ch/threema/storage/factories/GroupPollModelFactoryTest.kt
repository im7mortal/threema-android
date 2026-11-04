package ch.threema.storage.factories

import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DbGroup
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.models.UserState
import ch.threema.storage.models.poll.GroupPollModel
import ch.threema.storage.models.poll.PollModel
import ch.threema.test.TestData
import ch.threema.test.TestDatabaseProvider
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.utcDate
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupPollModelFactoryTest {

    @Test
    fun testGroupPollModelFactory() {
        val databaseProvider = TestDatabaseProvider()
        val databaseBackend = SqliteDatabaseBackend(
            databaseProvider = databaseProvider,
            identityProvider = TestIdentityProvider(identity = null),
        )
        val groupPollModelFactory = GroupPollModelFactory(databaseProvider)
        val groupIdentity1 = GroupIdentity(
            creatorIdentity = TestData.Identities.OTHER_1.value,
            groupId = 9001L,
        )
        val groupIdentity2 = GroupIdentity(
            creatorIdentity = TestData.Identities.OTHER_2.value,
            groupId = 9002L,
        )
        val groupIdentity3 = GroupIdentity(
            creatorIdentity = TestData.Identities.OTHER_3.value,
            groupId = 9003L,
        )
        createGroups(
            databaseBackend,
            groupIdentities = listOf(
                groupIdentity1,
                groupIdentity2,
                groupIdentity3,
            ),
        )
        val pollModel = PollModel(
            id = 1,
            apiPollId = "ff000000000000aa",
            creatorIdentity = "12345678",
            name = "Poll",
            state = PollModel.State.OPEN,
            assessment = PollModel.Assessment.SINGLE_CHOICE,
            type = PollModel.Type.INTERMEDIATE,
            choiceType = PollModel.ChoiceType.TEXT,
            displayType = PollModel.DisplayType.SUMMARY_MODE,
            createdAt = utcDate(year = 2026, month = 1, dayOfMonth = 1),
            modifiedAt = utcDate(year = 2026, month = 1, dayOfMonth = 1),
            lastViewedAt = null,
        )
        PollModelFactory(databaseProvider).create(pollModel)

        // Create 3 entries
        groupPollModelFactory.create(
            GroupPollModel(
                groupId = databaseBackend.getGroupDatabaseId(groupIdentity1)!!.toInt(),
                pollId = 1,
            ),
        )
        groupPollModelFactory.create(
            GroupPollModel(
                groupId = databaseBackend.getGroupDatabaseId(groupIdentity2)!!.toInt(),
                pollId = 2,
            ),
        )
        val groupPollModel3 = GroupPollModel(
            groupId = databaseBackend.getGroupDatabaseId(groupIdentity3)!!.toInt(),
            pollId = 3,
        )
        groupPollModelFactory.create(groupPollModel3)

        // Verify that we can test for existence by api poll id and group identity
        assertTrue(
            groupPollModelFactory.exists(
                apiPollId = "ff000000000000aa",
                groupIdentity = groupIdentity1,
            ),
        )
        assertFalse(
            groupPollModelFactory.exists(
                apiPollId = "ff000000000000aa",
                groupIdentity = groupIdentity2,
            ),
        )

        // Verify that we can query by poll id
        groupPollModelFactory.getByPollId(pollId = 3)
            .let { groupPollModel ->
                assertNotNull(groupPollModel)
                assertTrue(groupPollModel.id != 0)
                assertEquals(3, groupPollModel.pollId)
                assertEquals(groupPollModel3.groupId, groupPollModel.groupId)
            }

        // Verify that querying by a poll id that does not exist returns null
        assertNull(groupPollModelFactory.getByPollId(pollId = 4))

        // Verify that we can query by a group id and a poll id at the same time
        groupPollModelFactory.getByGroupIdAndPollId(
            groupId = groupPollModel3.groupId,
            pollId = 3,
        )
            .let { groupPollModel ->
                assertNotNull(groupPollModel)
                assertTrue(groupPollModel.id != 0)
                assertEquals(3, groupPollModel.pollId)
                assertEquals(groupPollModel3.groupId, groupPollModel.groupId)
            }

        // Verify that querying by a group-id-poll-id-combo that doesn't exist returns null
        assertNull(
            groupPollModelFactory.getByGroupIdAndPollId(
                groupId = groupPollModel3.groupId,
                pollId = 4,
            ),
        )

        // Verify that we can query group identifiers by multiple poll ids at once
        assertEquals(
            mapOf(
                1 to GroupIdentity(
                    creatorIdentity = TestData.Identities.OTHER_1.value,
                    groupId = 9001L,
                ),
                2 to GroupIdentity(
                    creatorIdentity = TestData.Identities.OTHER_2.value,
                    groupId = 9002L,
                ),
            ),
            groupPollModelFactory.getGroupIdentitiesByPollIds(setOf(1, 2)),
        )

        // Verify that an entry can no longer be queried after being deleted
        groupPollModelFactory.deleteByPollId(pollId = 3)
        assertNull(groupPollModelFactory.getByPollId(pollId = 3))
    }

    private fun createGroups(databaseBackend: DatabaseBackend, groupIdentities: List<GroupIdentity>) {
        groupIdentities.forEach { groupIdentity ->
            databaseBackend.createGroup(
                DbGroup(
                    creatorIdentity = groupIdentity.creatorIdentity,
                    groupId = groupIdentity.apiGroupIdHexString,
                    name = null,
                    createdAt = Instant.ofEpochMilli(42),
                    synchronizedAt = null,
                    lastUpdate = null,
                    conversationVisibility = ConversationVisibility.NORMAL,
                    conversationCategory = ConversationCategory.DEFAULT.serializedValue,
                    colorIndex = 0,
                    groupDescription = null,
                    groupDescriptionChangedAt = null,
                    members = emptySet(),
                    userState = UserState.MEMBER,
                    notificationTriggerPolicyOverridePolicy = null,
                    notificationTriggerPolicyOverrideExpiresAt = null,
                    spamReportingState = ConversationSpamReportingState.DEFAULT.serializedValue,
                ),
            )
        }
    }
}
