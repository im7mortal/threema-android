package ch.threema.app.libthreema.conversion

import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.data.models.GroupModelData
import ch.threema.domain.models.UserState
import ch.threema.libthreema.ConversationCategory as LibthreemaConversationCategory
import ch.threema.libthreema.ConversationVisibility as LibthreemaConversationVisibility
import ch.threema.libthreema.Group as LibthreemaGroup
import ch.threema.libthreema.GroupIdentity as LibthreemaGroupIdentity
import ch.threema.libthreema.GroupNotificationTriggerPolicyOverride as LibthreemaGroupNotificationTriggerPolicyOverride
import ch.threema.libthreema.GroupNotificationTriggerPolicyOverridePolicy as LibthreemaGroupNotificationTriggerPolicyOverridePolicy
import ch.threema.libthreema.UserState as LibthreemaUserState
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData

class GroupConversionTest {
    private val baseGroupModelData = GroupModelData(
        groupIdentity = ch.threema.data.datatypes.GroupIdentity(
            creatorIdentity = TestData.Identities.OTHER_1.value,
            groupId = 42,
        ),
        name = "Group",
        createdAt = Instant.ofEpochMilli(1),
        synchronizedAt = null,
        lastUpdate = Instant.ofEpochMilli(2),
        conversationVisibility = ConversationVisibility.PINNED,
        conversationCategory = ConversationCategory.PROTECTED,
        groupDescription = null,
        groupDescriptionChangedAt = null,
        otherMembers = setOf(TestData.Identities.OTHER_2.value, TestData.Identities.OTHER_3.value),
        userState = UserState.MEMBER,
        notificationTriggerPolicyOverride = GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
            expiresAt = null,
        ),
        spamReportingState = ConversationSpamReportingState.DEFAULT,
    )

    private val baseLibthreemaGroup = LibthreemaGroup(
        identity = LibthreemaGroupIdentity(
            groupId = 42u,
            creatorIdentity = TestData.Identities.OTHER_1.value,
        ),
        name = "Group",
        createdAt = 1u,
        lastUpdateAt = 2u,
        userState = LibthreemaUserState.MEMBER,
        members = setOf(TestData.Identities.OTHER_2.value, TestData.Identities.OTHER_3.value),
        notificationTriggerPolicyOverride = LibthreemaGroupNotificationTriggerPolicyOverride(
            policy = LibthreemaGroupNotificationTriggerPolicyOverridePolicy.MENTIONED,
            expiresAt = null,
        ),
        conversationCategory = LibthreemaConversationCategory.PROTECTED,
        conversationVisibility = LibthreemaConversationVisibility.PINNED,
    )

    @Test
    fun `base group model data can be converted to libthreema group`() {
        val actualLibthreemaGroup = baseGroupModelData.toLibthreemaGroup()

        assertEquals(baseLibthreemaGroup, actualLibthreemaGroup)
    }

    @Test
    fun `base libthreema group can be converted to base group model data`() {
        val actualGroupModelData = baseLibthreemaGroup.toGroupModelData()

        assertEquals(baseGroupModelData, actualGroupModelData)
    }
}
