package ch.threema.app.groupmanagement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ch.threema.app.DangerousTest
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.messages.GroupNameMessage
import ch.threema.domain.types.IdentityString
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/**
 * Tests that incoming group name messages are handled correctly.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupNameTest : GroupConversationListTest<GroupNameMessage>() {
    override fun createMessageForGroup(): GroupNameMessage {
        return GroupNameMessage()
            .apply { groupName = "New Group Name" }
    }

    /**
     * Tests that a (valid) group rename message really changes the group name.
     */
    @Test
    fun testValidGroupRename() = runTest {
        // Assert initial groups
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Create group rename message
        val groupARenamed =
            TestGroup(
                groupA.apiGroupId,
                groupA.groupCreator,
                groupA.members,
                "GroupARenamed",
                myContact.identity,
            )

        val message = createEncryptedRenameMessage(
            newGroupName = groupARenamed.groupName,
            groupCreatorIdentity = groupARenamed.groupCreator.identity,
            apiGroupId = groupARenamed.apiGroupId,
            fromContact = groupARenamed.groupCreator,
        )

        // Process the group rename message
        processMessage(message, groupARenamed.groupCreator.identityStore)

        // Assert that the group name change has been processed
        assertGroupConversations(
            expectedGroups = initialGroups.replace(groupA, groupARenamed),
        )
    }

    /**
     * Check that a group rename message from a wrong sender (not the group creator, just a member)
     * does not lead to a group name change.
     */
    @Test
    fun testInvalidGroupRenameSender() = runTest {
        // Assert initial groups
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Create group rename message (from wrong sender)
        val groupARenamed =
            TestGroup(
                groupA.apiGroupId,
                groupA.groupCreator,
                groupA.members,
                "GroupARenamed",
                myContact.identity,
            )

        val message = createEncryptedRenameMessage(
            newGroupName = groupARenamed.groupName,
            // Note that this will be ignored anyway
            groupCreatorIdentity = groupARenamed.groupCreator.identity,
            apiGroupId = groupARenamed.apiGroupId,
            // Not the creator of this group!
            fromContact = contactB,
        )

        // Process the group rename message
        processMessage(message, contactB.identityStore)

        assertGroupConversations(
            expectedGroups = initialGroups,
        )
    }

    override fun testCommonGroupReceiveStepUnknownGroupUserCreator() {
        // Don't test this as a group name message always comes from the group creator which would
        // be this user in this test
    }

    override fun testCommonGroupReceiveStepLeftGroupUserCreator() {
        // Don't test this step. The group rename message is always sent as creator of the group
        // and if the sender of the message is the creator of a group owned by this user, then the
        // message comes from this user itself - which is impossible.
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserCreator() {
        // Don't test this step. The group rename message is always sent as creator of the group
        // and therefore the sender of the message is never missing in the group. However, the group
        // model is (very likely) not found and therefore handled earlier in the common group
        // receive steps.
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserNotCreator() {
        // Don't test this step. The group rename message is always sent as creator of the group
        // and therefore the sender of the message is never missing in the group. However, the group
        // model is (very likely) not found and therefore handled earlier in the common group
        // receive steps.
    }

    private fun createEncryptedRenameMessage(
        newGroupName: String,
        groupCreatorIdentity: IdentityString,
        apiGroupId: GroupId,
        fromContact: TestContact,
    ) = GroupNameMessage().apply {
        groupName = newGroupName
        groupCreator = groupCreatorIdentity
        fromIdentity = fromContact.identity
        setApiGroupId(apiGroupId)
        toIdentity = myContact.identity
    }
}
