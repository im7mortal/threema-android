package ch.threema.app.libthreema.conversion

import ch.threema.common.takeUnlessEmpty
import ch.threema.common.toEpochMilliInstant
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.models.ContactModelData
import ch.threema.libthreema.Contact as LibthreemaContact

fun ContactModelData.toLibthreemaContact() = LibthreemaContact(
    identity = identity,
    publicKey = publicKey,
    createdAt = createdAt.toEpochMilli().toULong(),
    lastUpdateAt = lastUpdateAt?.toEpochMilli()?.toULong(),
    firstName = firstName,
    lastName = lastName,
    nickname = nickname ?: "",
    verificationLevel = verificationLevel.toLibthreemaVerificationLevel(),
    workVerificationLevel = workVerificationLevel.toLibthreemaWorkVerificationLevel(),
    identityType = identityType.toLibthreemaIdentityType(),
    acquaintanceLevel = acquaintanceLevel.toLibthreemaAcquaintanceLevel(),
    activityState = activityState.toLibthreemaIdentityState(),
    featureMask = featureMask,
    syncState = syncState.toLibthreemaContactSyncState(),
    readReceiptPolicyOverride = readReceiptPolicy.toLibthreemaReadReceiptPolicy(),
    typingIndicatorPolicyOverride = typingIndicatorPolicy.toLibthreemaTypingIndicatorPolicy(),
    notificationTriggerPolicyOverride = notificationTriggerPolicyOverride?.toLibthreemaContactNotificationTriggerPolicy(),
    conversationCategory = conversationCategory.toLibthreemaConversationCategory(),
    conversationVisibility = conversationVisibility.toLibthreemaConversationVisibility(),
    workLastFullSyncAt = workLastFullSyncAt?.toEpochMilli()?.toULong(),
    workAvailabilityStatus = availabilityStatus.toLibthreemaWorkAvailabilityStatus(),
)

fun LibthreemaContact.toContactModelData() = ContactModelData(
    identity = identity,
    publicKey = publicKey,
    createdAt = createdAt.toEpochMilliInstant(),
    lastUpdateAt = lastUpdateAt?.toEpochMilliInstant(),
    firstName = firstName,
    lastName = lastName,
    nickname = nickname.takeUnlessEmpty(),
    verificationLevel = verificationLevel.toVerificationLevel(),
    workVerificationLevel = workVerificationLevel.toWorkVerificationLevel(),
    identityType = identityType.toIdentityType(),
    acquaintanceLevel = acquaintanceLevel.toAcquaintanceLevel(),
    activityState = activityState.toIdentityState(),
    syncState = syncState.toContactSyncState(),
    featureMask = featureMask,
    readReceiptPolicy = readReceiptPolicyOverride.toReadReceiptPolicy(),
    typingIndicatorPolicy = typingIndicatorPolicyOverride.toTypingIndicatorPolicy(),
    conversationVisibility = conversationVisibility.toConversationVisibility(),
    conversationCategory = conversationCategory.toConversationCategory(),
    androidContactLookupInfo = null,
    localAvatarExpires = null,
    isRestored = false,
    profilePictureBlobId = null,
    jobTitle = null,
    department = null,
    notificationTriggerPolicyOverride = notificationTriggerPolicyOverride?.toContactNotificationTriggerPolicyOverride(),
    workLastFullSyncAt = workLastFullSyncAt?.toEpochMilliInstant(),
    availabilityStatus = workAvailabilityStatus?.toAvailabilityStatus() ?: AvailabilityStatus.None,
)
