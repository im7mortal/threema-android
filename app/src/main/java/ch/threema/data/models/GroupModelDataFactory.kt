package ch.threema.data.models

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.toLong
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.data.datatypes.IdColor
import ch.threema.data.storage.DbGroup
import ch.threema.logging.logAndReportError
import java.nio.ByteOrder

private val logger = getThreemaLogger("GroupModelDataFactory")

internal object GroupModelDataFactory :
    ModelDataFactory<GroupModelData, DbGroup> {
    override fun toDbType(value: GroupModelData): DbGroup = DbGroup(
        creatorIdentity = value.groupIdentity.creatorIdentity,
        groupId = value.groupIdentity.apiGroupIdHexString,
        name = value.name,
        createdAt = value.createdAt,
        synchronizedAt = value.synchronizedAt,
        lastUpdate = value.lastUpdate,
        conversationVisibility = value.conversationVisibility,
        conversationCategory = value.conversationCategory.serializedValue,
        colorIndex = value.idColor.colorIndex,
        groupDescription = value.groupDescription,
        groupDescriptionChangedAt = value.groupDescriptionChangedAt,
        members = value.otherMembers,
        userState = value.userState,
        notificationTriggerPolicyOverridePolicy = value.notificationTriggerPolicyOverride?.policy?.serializedValue,
        notificationTriggerPolicyOverrideExpiresAt = value.notificationTriggerPolicyOverride?.expiresAt,
        spamReportingState = value.spamReportingState.serializedValue,
    )

    override fun toDataType(value: DbGroup): GroupModelData = GroupModelData(
        groupIdentity = GroupIdentity(value.creatorIdentity, groupIdDbToData(value.groupId)),
        name = value.name,
        createdAt = value.createdAt,
        synchronizedAt = value.synchronizedAt,
        lastUpdate = value.lastUpdate,
        conversationVisibility = value.conversationVisibility,
        conversationCategory = value.getConversationCategory(),
        precomputedIdColor = IdColor(value.colorIndex),
        groupDescription = value.groupDescription,
        groupDescriptionChangedAt = value.groupDescriptionChangedAt,
        otherMembers = value.members,
        userState = value.userState,
        notificationTriggerPolicyOverride = value.getNotificationTriggerPolicyOverride(),
        spamReportingState = value.getSpamReportingState(),
    )

    private fun groupIdDbToData(littleEndianHexGroupId: String): Long =
        littleEndianHexGroupId.hexToByteArray()
            .toLong(ByteOrder.LITTLE_ENDIAN)

    private fun DbGroup.getNotificationTriggerPolicyOverride(): GroupNotificationTriggerPolicyOverride? {
        if (notificationTriggerPolicyOverridePolicy == null) {
            return null
        }

        return GroupNotificationTriggerPolicyOverride(
            policy = GroupNotificationTriggerPolicyOverridePolicy.deserialize(notificationTriggerPolicyOverridePolicy)
                ?: run {
                    logger.logAndReportError(
                        "Could not deserialize group notification trigger policy override value {}",
                        notificationTriggerPolicyOverridePolicy,
                    )
                    return null
                },
            expiresAt = notificationTriggerPolicyOverrideExpiresAt,
        )
    }

    private fun DbGroup.getConversationCategory(): ConversationCategory =
        ConversationCategory.deserialize(conversationCategory)
            ?: run {
                logger.logAndReportError(
                    "Could not deserialize conversation category value {}",
                    conversationCategory,
                )
                ConversationCategory.DEFAULT
            }

    private fun DbGroup.getSpamReportingState(): ConversationSpamReportingState =
        ConversationSpamReportingState.fromSerializedValue(spamReportingState)
            ?: ConversationSpamReportingState.OPEN
}
