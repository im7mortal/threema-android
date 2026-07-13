package ch.threema.app.groupmanagement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import ch.threema.app.DangerousTest
import ch.threema.app.testutils.TestHelpers.TestContact
import ch.threema.app.testutils.TestHelpers.TestGroup
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.ContactModelData
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.UserState
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.test.TestData.PUBLIC_KEY
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/**
 * Runs different tests that verify that incoming group setup messages are handled according to the
 * protocol.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
@DangerousTest
class IncomingGroupSetupTest : GroupConversationListTest<GroupSetupMessage>() {
    private val groupService by lazy { serviceManager.groupService }
    private val groupModelRepository by lazy { serviceManager.modelRepositories.groups }

    override fun createMessageForGroup() = GroupSetupMessage()

    /**
     * Test a group setup message of an unknown group where the user is not not a member.
     */
    @Test
    fun testUnknownGroupNotMember() = runTest {
        // Assert initial group conversations
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Create the group setup message
        val message = createGroupSetupMessage(groupAUnknown)
        // Remove this user from the members
        message.members = message.members.filter { it != myContact.identity }.toTypedArray()
        // Create message box from contact A (group creator)
        processMessage(message, groupAUnknown.groupCreator.identityStore)

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)
    }

    /**
     * Test a group setup message of an unknown group that has no members.
     */
    @Test
    fun testUnknownEmptyGroup() = runTest {
        // Assert initial group conversations
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Create the group setup message
        val message = createGroupSetupMessage(groupAUnknown)
        // Group is empty
        message.members = emptyArray()
        // Create message box from contact A (group creator)
        processMessage(message, groupAUnknown.groupCreator.identityStore)

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)
    }

    /**
     * Test a group setup message of a blocked contact.
     */
    @Test
    fun testBlocked() = runTest {
        // Assert initial group conversations
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        serviceManager.blockedIdentitiesService.blockIdentity(contactA.identity)
        serviceManager.blockedIdentitiesService.blockIdentity(contactB.identity)

        val newGroup = TestGroup(
            newAGroup.apiGroupId,
            newAGroup.groupCreator,
            newAGroup.members,
            // Note that this will be the group name because we only test the group setup message
            // that is not followed by a group rename
            "Me, 12345678, ABCDEFGH",
            myContact.identity,
        )
        testNewGroup(newGroup)
    }

    /**
     * Test a group setup message of a group where the user is not a member anymore.
     */
    @Test
    fun testKicked() = runTest {
        // Assert initial group conversations
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Assert that the user is a member of groupAB
        val beforeKicked = groupService.getById(groupAB.groupModel.id)
        assertNotNull(beforeKicked)
        assertEquals(UserState.MEMBER, beforeKicked.userState)
        assertTrue(groupService.isGroupMember(beforeKicked))

        // Create the group setup message
        val message = createGroupSetupMessage(groupAB)
        // Only contact B is a member of this group, so this user has been kicked
        message.members = arrayOf(contactB.identity)
        // Create message box from contact A (group creator)
        processMessage(message, groupAB.groupCreator.identityStore)

        // Assert that the user state has been changed to 'kicked'
        val afterKicked = groupModelRepository.getByGroupIdentity(
            GroupIdentity(groupAB.groupCreator.identity, groupAB.apiGroupId.toLong()),
        )
        assertNotNull(afterKicked)
        assertEquals(UserState.KICKED, afterKicked.data?.userState)

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)
    }

    /**
     * Test a group setup message of a group where the members changed.
     */
    @Test
    fun testMembersChanged() = runTest {
        // Assert initial group conversations
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Create the group setup message
        val message = createGroupSetupMessage(groupAB).apply {
            // Remove contact B from group and add contact C to group
            members = members.toList().replace(contactB.identity, contactC.identity).toTypedArray()
        }
        // Create message box from contact A (group creator)
        processMessage(message, groupAB.groupCreator.identityStore)

        // Assert that group conversations did not appear, disappear, or change their name
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)
    }

    /**
     * Test a group setup message of a newly created group.
     */
    @Test
    fun testNewGroup() = runTest {
        val newGroup = TestGroup(
            newBGroup.apiGroupId,
            newBGroup.groupCreator,
            newBGroup.members,
            // Note that this will be the group name because we only test the group setup message
            // that is not followed by a group rename
            "Me, ABCDEFGH",
            myContact.identity,
        )

        testNewGroup(newGroup)
    }

    /**
     * Test two group setup messages that remove and then add the user.
     */
    @Test
    fun testRemoveJoin() = runTest {
        // Assert initial group conversations
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Create the group setup message
        val removeMessage = createGroupSetupMessage(groupAB)
        // Only contact B is a member of this group, so this user has been kicked
        removeMessage.members = arrayOf(contactB.identity)
        // Create message box from contact A (group creator)
        processMessage(removeMessage, groupAB.groupCreator.identityStore)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Create the group setup message (now again with this user)
        val addMessage = createGroupSetupMessage(groupAB)
        // Now we again include this user
        addMessage.members = arrayOf(contactB.identity, myContact.identity)
        // Create message box from contact A (group creator)
        processMessage(addMessage, groupAB.groupCreator.identityStore)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)
    }

    @Test
    fun testGroupContainingInvalidIDs() = runTest {
        // Assert initial group conversations
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        val invalidMemberId = ",,,,,,,,"

        val newGroup = TestGroup(
            newAGroup.apiGroupId,
            newAGroup.groupCreator,
            // Note that this ID is not valid
            newAGroup.members + TestContact(invalidMemberId),
            // Note that this will be the group name because we only test the group setup message
            // that is not followed by a group rename
            "Me, 12345678, ABCDEFGH",
            myContact.identity,
        )

        // Create the group setup message
        val message = createGroupSetupMessage(newGroup)
        // Create message box from contact A (group creator)
        processMessage(message, newGroup.groupCreator.identityStore)

        // Assert that the new group appears in the list
        assertGroupConversations(
            expectedGroups = listOf(newGroup) + initialGroups,
        )

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)
    }

    @Test
    fun testGroupContainingRevokedButKnownContact() = runTest {
        // Assert initial group conversations
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Add a revoked contact
        serviceManager.modelRepositories.contacts.createFromLocal(
            contactModelData = revokedContactModelData,
        )

        val newGroup = TestGroup(
            newAGroup.apiGroupId,
            newAGroup.groupCreator,
            // Note that the activity state of this contact is INVALID
            newAGroup.members + TestContact(revokedContactModelData.identity),
            // Note that this will be the group name because we only test the group setup message
            // that is not followed by a group rename
            "Me, 12345678, ABCDEFGH",
            myContact.identity,
        )

        // Create the group setup message
        val message = createGroupSetupMessage(newGroup)

        // Check that the group setup message contains the revoked ID. Otherwise this test does not make sense.
        assertTrue(message.members.contains(revokedContactModelData.identity))

        // Create message box from contact A (group creator)
        processMessage(message, newGroup.groupCreator.identityStore)

        // Assert that the new group appears in the list
        assertGroupConversations(
            expectedGroups = listOf(newGroup) + initialGroups,
        )

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Get the group model of the group and check that it exists and the revoked identity is not listed as a member
        val newGroupModel = groupModelRepository.getByCreatorIdentityAndId(newGroup.groupCreator.identity, newGroup.apiGroupId)
        assertNotNull(newGroupModel)
        val data = newGroupModel.data
        assertNotNull(data)
        assertFalse(data.otherMembers.contains(revokedContactModelData.identity))
    }

    private suspend fun testNewGroup(newGroup: TestGroup) {
        assertNull(
            groupModelRepository.getByCreatorIdentityAndId(
                newGroup.groupCreator.identity,
                newGroup.apiGroupId,
            )?.data,
        )

        // Assert initial group conversations
        assertGroupConversations(
            expectedGroups = initialGroups,
        )

        // Create the group setup message
        val message = createGroupSetupMessage(newGroup)
        // Create message box from contact A (group creator)
        processMessage(message, newGroup.groupCreator.identityStore)

        // Assert that the new group model exists
        val groupModel = groupModelRepository.getByCreatorIdentityAndId(
            creatorIdentity = newGroup.groupCreator.identity,
            groupId = newGroup.apiGroupId,
        )
        assertNotNull(groupModel)

        // Assert that no message is sent
        assertEquals(0, sentMessagesInsideTask.size)

        // Assert that the group has the correct members
        val group = groupService.getByApiGroupIdAndCreator(
            newGroup.apiGroupId,
            newGroup.groupCreator.identity,
        )
        assertNotNull(group!!)
        val expectedMemberCount = newGroup.members.size + 1
        // Assert that there are two more members than member models (as the user and the creator is not stored into the database).
        assertEquals(
            expectedMemberCount,
            serviceManager.databaseService.groupMemberModelFactory.getByGroupId(group.id).size + 2,
        )

        // Assert that the group service returns the member lists including the user
        assertEquals(expectedMemberCount, groupService.getMembers(group).size)
        assertEquals(expectedMemberCount, groupService.getGroupMemberIdentities(group).size)
        assertEquals(expectedMemberCount, groupService.getMembersWithoutUser(group).size + 1)
        assertEquals(expectedMemberCount, groupService.countMembers(group))
        assertEquals(expectedMemberCount, groupService.countMembersWithoutUser(group) + 1)

        // Assert that the new group appears in the list
        assertGroupConversations(
            expectedGroups = listOf(newGroup) + initialGroups,
        )
    }

    private fun createGroupSetupMessage(testGroup: TestGroup) = GroupSetupMessage()
        .apply {
            apiGroupId = testGroup.apiGroupId
            groupCreator = testGroup.groupCreator.identity
            fromIdentity = testGroup.groupCreator.identity
            toIdentity = myContact.identity
            members =
                testGroup.members.map { it.identity }
                    .filter { it != testGroup.groupCreator.identity }
                    .toTypedArray()
        }

    private val revokedContactModelData = ContactModelData(
        identity = "01238765",
        publicKey = PUBLIC_KEY,
        createdAt = Instant.now(),
        lastUpdateAt = null,
        firstName = "1234",
        lastName = "8765",
        nickname = null,
        verificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        identityType = IdentityType.REGULAR,
        acquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState = IdentityState.INVALID,
        syncState = ContactSyncState.INITIAL,
        featureMask = 0u,
        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        conversationVisibility = ConversationVisibility.NORMAL,
        androidContactLookupInfo = null,
        localAvatarExpires = null,
        isRestored = false,
        profilePictureBlobId = null,
        jobTitle = null,
        department = null,
        notificationTriggerPolicyOverride = null,
        availabilityStatus = AvailabilityStatus.None,
        workLastFullSyncAt = null,
    )

    override fun testCommonGroupReceiveStepUnknownGroupUserCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepUnknownGroupUserNotCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepLeftGroupUserCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepLeftGroupUserNotCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserCreator() {
        // The common group receive steps are not executed for group setup messages
    }

    override fun testCommonGroupReceiveStepSenderNotMemberUserNotCreator() {
        // The common group receive steps are not executed for group setup messages
    }
}
