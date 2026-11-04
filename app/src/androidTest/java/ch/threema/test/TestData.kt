package ch.threema.test

import ch.threema.base.crypto.NaCl
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.ContactModelData
import ch.threema.data.storage.DbContact
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.MessageId
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.MessageUid
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.testhelpers.utcDate
import java.time.Instant

object TestData {
    val PUBLIC_KEY = ByteArray(NaCl.PUBLIC_KEY_BYTES) { it.toByte() }

    object Identities {

        val ME = Identity("12345678")

        val OTHER_1 = Identity("11111111")
        val OTHER_2 = Identity("22222222")
        val OTHER_3 = Identity("33333333")
        val OTHER_4 = Identity("44444444")
        val OTHER_5 = Identity("55555555")

        val BROADCAST = Identity("*0000000")

        const val INVALID = "INVALID"
    }

    object GroupIdentities {

        val GROUP_ME = GroupIdentity(
            creatorIdentity = Identities.ME.value,
            groupId = 0L,
        )
        val GROUP_1 = GroupIdentity(
            creatorIdentity = Identities.OTHER_1.value,
            groupId = 1L,
        )
        val GROUP_2 = GroupIdentity(
            creatorIdentity = Identities.OTHER_2.value,
            groupId = 2L,
        )
        val GROUP_3 = GroupIdentity(
            creatorIdentity = Identities.OTHER_3.value,
            groupId = 3L,
        )
        val GROUP_4 = GroupIdentity(
            creatorIdentity = Identities.OTHER_4.value,
            groupId = 4L,
        )
    }

    object PublicKeys {
        val ME = ByteArray(NaCl.PUBLIC_KEY_BYTES) { it.toByte() }
        val OTHER_1 = ByteArray(NaCl.PUBLIC_KEY_BYTES) { 1.toByte() }
        val OTHER_2 = ByteArray(NaCl.PUBLIC_KEY_BYTES) { 2.toByte() }
        val OTHER_3 = ByteArray(NaCl.PUBLIC_KEY_BYTES) { 3.toByte() }
        val OTHER_4 = ByteArray(NaCl.PUBLIC_KEY_BYTES) { 4.toByte() }
        val OTHER_5 = ByteArray(NaCl.PUBLIC_KEY_BYTES) { 5.toByte() }
    }

    object UUIDs {
        const val UUID_1 = "00000000-0000-4000-8000-000000000001"
        const val UUID_2 = "00000000-0000-4000-8000-000000000002"
        const val UUID_3 = "00000000-0000-4000-8000-000000000003"
        const val UUID_4 = "00000000-0000-4000-8000-000000000004"
    }

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
        conversationCategory: ConversationCategory = ConversationCategory.DEFAULT,
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
        conversationCategory = conversationCategory,
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

    fun createDbContact(
        identity: Identity,
        publicKey: ByteArray,
        acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
    ) = DbContact(
        identity = identity.value,
        publicKey = publicKey,
        createdAt = utcDate(2026, 7, 28),
        lastUpdateAt = null,
        firstName = "",
        lastName = "",
        nickname = null,
        colorIndex = 0,
        verificationLevel = VerificationLevel.UNVERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        identityType = IdentityType.REGULAR,
        acquaintanceLevel = acquaintanceLevel,
        activityState = IdentityState.ACTIVE,
        syncState = ContactSyncState.INITIAL,
        featureMask = 42u,
        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        conversationVisibility = ConversationVisibility.NORMAL,
        conversationCategory = ConversationCategory.DEFAULT.serializedValue,
        androidContactLookupKey = null,
        localAvatarExpires = null,
        isRestored = false,
        profilePictureBlobId = null,
        jobTitle = null,
        department = null,
        notificationTriggerPolicyOverridePolicy = null,
        notificationTriggerPolicyOverrideExpiresAt = null,
        availabilityStatusSet = null,
        workLastFullSyncAt = null,
    )

    fun createContactTextMessage(
        id: Int,
        uid: MessageUid,
        messageId: MessageId,
        identity: Identity,
        isOutbox: Boolean,
        text: String,
    ) = ContactMessageModel().apply {
        this.id = id
        this.uid = uid
        this.messageId = messageId
        this.identity = identity.value
        this.isOutbox = isOutbox
        this.type = MessageType.TEXT
        this.body = text
    }

    fun createGroupTextMessage(
        groupDatabaseId: GroupDatabaseId,
        id: Int,
        uid: MessageUid,
        messageId: MessageId,
        identity: Identity?,
        isOutbox: Boolean,
        text: String,
    ) = GroupMessageModel().apply {
        this.groupId = groupDatabaseId.toInt()
        this.id = id
        this.uid = uid
        this.messageId = messageId
        this.identity = identity?.value
        this.isOutbox = isOutbox
        this.type = MessageType.TEXT
        this.body = text
    }
}
