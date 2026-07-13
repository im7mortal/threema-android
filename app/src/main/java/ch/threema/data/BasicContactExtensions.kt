package ch.threema.data

import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.models.ContactModelData
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import java.time.Instant

fun BasicContact.toContactModelData() = ContactModelData(
    identity = identity,
    publicKey = publicKey,
    createdAt = Instant.now(),
    lastUpdateAt = null,
    firstName = firstName.orEmpty(),
    lastName = firstName.orEmpty(),
    nickname = null,
    verificationLevel = verificationLevel,
    workVerificationLevel = workVerificationLevel,
    identityType = identityType,
    activityState = identityState,
    featureMask = featureMask,
    typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
    readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
    syncState = ContactSyncState.INITIAL,
    acquaintanceLevel = AcquaintanceLevel.DIRECT,
    conversationVisibility = ConversationVisibility.NORMAL,
    localAvatarExpires = null,
    androidContactLookupInfo = null,
    profilePictureBlobId = null,
    isRestored = false,
    jobTitle = jobTitle,
    department = department,
    notificationTriggerPolicyOverride = null,
    availabilityStatus = AvailabilityStatus.None,
    workLastFullSyncAt = null,
)
