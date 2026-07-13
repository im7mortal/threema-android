package ch.threema.test

import ch.threema.base.crypto.NaCl
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.models.ContactModelData
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.Identity
import java.time.Instant

object TestData {
    val PUBLIC_KEY = ByteArray(NaCl.PUBLIC_KEY_BYTES) { it.toByte() }

    fun createContactModelData(
        identity: Identity,
        publicKey: ByteArray = PUBLIC_KEY,
        createdAt: Instant = Instant.now(),
        lastUpdateAt: Instant? = null,
        firstName: String = "First",
        lastName: String = "Last",
        nickname: String? = null,
        verificationLevel: VerificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel: WorkVerificationLevel = WorkVerificationLevel.NONE,
        identityType: IdentityType = IdentityType.REGULAR,
        acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState: IdentityState = IdentityState.ACTIVE,
        syncState: ContactSyncState = ContactSyncState.INITIAL,
        featureMask: ULong = 0u,
        readReceiptPolicy: ReadReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy: TypingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        androidContactLookupInfo: AndroidContactLookupInfo? = null,
        localAvatarExpires: Instant? = null,
        isRestored: Boolean = false,
        profilePictureBlobId: ByteArray? = null,
        jobTitle: String? = null,
        department: String? = null,
        notificationTriggerPolicyOverride: ContactNotificationTriggerPolicyOverride? = null,
        availabilityStatus: AvailabilityStatus = AvailabilityStatus.None,
        workLastFullSyncAt: Instant? = null,
    ) = ContactModelData(
        identity = identity.value,
        publicKey = publicKey,
        createdAt = createdAt,
        lastUpdateAt = lastUpdateAt,
        firstName = firstName,
        lastName = lastName,
        nickname = nickname,
        verificationLevel = verificationLevel,
        workVerificationLevel = workVerificationLevel,
        identityType = identityType,
        acquaintanceLevel = acquaintanceLevel,
        activityState = activityState,
        syncState = syncState,
        featureMask = featureMask,
        readReceiptPolicy = readReceiptPolicy,
        typingIndicatorPolicy = typingIndicatorPolicy,
        conversationVisibility = conversationVisibility,
        androidContactLookupInfo = androidContactLookupInfo,
        localAvatarExpires = localAvatarExpires,
        isRestored = isRestored,
        profilePictureBlobId = profilePictureBlobId,
        jobTitle = jobTitle,
        department = department,
        notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
        availabilityStatus = availabilityStatus,
        workLastFullSyncAt = workLastFullSyncAt,
    )
}
