package ch.threema.app.libthreema.conversion

import ch.threema.common.takeUnlessEmpty
import ch.threema.common.toEpochMilliInstant
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.models.GroupModelData
import ch.threema.libthreema.Group as LibthreemaGroup

fun GroupModelData.toLibthreemaGroup() = LibthreemaGroup(
    identity = groupIdentity.toLibthreemaGroupIdentity(),
    name = name ?: "",
    createdAt = createdAt.toEpochMilli().toULong(),
    lastUpdateAt = lastUpdate?.toEpochMilli()?.toULong(),
    userState = userState.toLibthreemaUserState(),
    members = otherMembers.toSet(),
    notificationTriggerPolicyOverride = notificationTriggerPolicyOverride?.toLibthreemaGroupNotificationTriggerPolicy(),
    conversationVisibility = conversationVisibility.toLibthreemaConversationVisibility(),
    conversationCategory = conversationCategory.toLibthreemaConversationCategory(),
)

fun LibthreemaGroup.toGroupModelData() = GroupModelData(
    groupIdentity = identity.toGroupIdentity(),
    name = name.takeUnlessEmpty(),
    createdAt = createdAt.toEpochMilliInstant(),
    synchronizedAt = null,
    lastUpdate = lastUpdateAt?.toEpochMilliInstant(),
    conversationVisibility = conversationVisibility.toConversationVisibility(),
    conversationCategory = conversationCategory.toConversationCategory(),
    groupDescription = null,
    otherMembers = members.toSet(),
    groupDescriptionChangedAt = null,
    userState = userState.toUserState(),
    notificationTriggerPolicyOverride = notificationTriggerPolicyOverride?.toGroupNotificationTriggerPolicy(),
    spamReportingState = ConversationSpamReportingState.DEFAULT,
)
