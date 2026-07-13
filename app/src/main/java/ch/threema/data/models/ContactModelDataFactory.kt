package ch.threema.data.models

import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverridePolicy
import ch.threema.data.datatypes.IdColor
import ch.threema.data.storage.DbContact
import ch.threema.logging.logAndReportError

private val logger = getThreemaLogger("ContactModelDataFactory")

internal object ContactModelDataFactory :
    ModelDataFactory<ContactModelData, DbContact> {
    override fun toDbType(value: ContactModelData): DbContact = DbContact(
        identity = value.identity,
        publicKey = value.publicKey,
        createdAt = value.createdAt,
        lastUpdateAt = value.lastUpdateAt,
        firstName = value.firstName,
        lastName = value.lastName,
        nickname = value.nickname,
        colorIndex = value.idColor.colorIndex,
        verificationLevel = value.verificationLevel,
        workVerificationLevel = value.workVerificationLevel,
        identityType = value.identityType,
        acquaintanceLevel = value.acquaintanceLevel,
        activityState = value.activityState,
        syncState = value.syncState,
        featureMask = value.featureMask,
        readReceiptPolicy = value.readReceiptPolicy,
        typingIndicatorPolicy = value.typingIndicatorPolicy,
        conversationVisibility = value.conversationVisibility,
        androidContactLookupKey = value.androidContactLookupInfo.toDatabaseString(),
        localAvatarExpires = value.localAvatarExpires,
        isRestored = value.isRestored,
        profilePictureBlobId = value.profilePictureBlobId,
        jobTitle = value.jobTitle,
        department = value.department,
        notificationTriggerPolicyOverridePolicy = value.notificationTriggerPolicyOverride?.policy?.serializedValue,
        notificationTriggerPolicyOverrideExpiresAt = value.notificationTriggerPolicyOverride?.expiresAt,
        availabilityStatusSet = when (value.availabilityStatus) {
            AvailabilityStatus.None -> null
            is AvailabilityStatus.Set -> value.availabilityStatus
        },
        workLastFullSyncAt = value.workLastFullSyncAt,
    )

    override fun toDataType(value: DbContact): ContactModelData = ContactModelData(
        identity = value.identity,
        publicKey = value.publicKey,
        createdAt = value.createdAt,
        lastUpdateAt = value.lastUpdateAt,
        firstName = value.firstName,
        lastName = value.lastName,
        nickname = value.nickname,
        idColor = IdColor(value.colorIndex),
        verificationLevel = value.verificationLevel,
        workVerificationLevel = value.workVerificationLevel,
        identityType = value.identityType,
        acquaintanceLevel = value.acquaintanceLevel,
        activityState = value.activityState,
        syncState = value.syncState,
        featureMask = value.featureMask,
        readReceiptPolicy = value.readReceiptPolicy,
        typingIndicatorPolicy = value.typingIndicatorPolicy,
        conversationVisibility = value.conversationVisibility,
        androidContactLookupInfo = value.androidContactLookupKey.toAndroidContactLookupKey(),
        localAvatarExpires = value.localAvatarExpires,
        isRestored = value.isRestored,
        profilePictureBlobId = value.profilePictureBlobId,
        jobTitle = value.jobTitle,
        department = value.department,
        notificationTriggerPolicyOverride = value.getNotificationTriggerPolicyOverride(),
        availabilityStatus = value.availabilityStatusSet ?: AvailabilityStatus.None,
        workLastFullSyncAt = value.workLastFullSyncAt,
    )

    private fun AndroidContactLookupInfo?.toDatabaseString(): String? = this?.run {
        // Note that we append '/null' on purpose if the contact id is null. This ensures that we parse the lookup key and contact id correctly if the
        // lookup key contains any slashes. When parsing the string, we rely on the last '/' for splitting the lookup key and the contact id.
        "$lookupKey/$contactId"
    }

    private fun String?.toAndroidContactLookupKey(): AndroidContactLookupInfo? = this?.let { androidContactLookupKeyString ->
        AndroidContactLookupInfo(
            lookupKey = androidContactLookupKeyString.substringBeforeLast(delimiter = "/"),
            contactId = androidContactLookupKeyString.substringAfterLast(delimiter = "/", missingDelimiterValue = "").toLongOrNull(),
        )
    }

    private fun DbContact.getNotificationTriggerPolicyOverride(): ContactNotificationTriggerPolicyOverride? {
        if (notificationTriggerPolicyOverridePolicy == null) {
            return null
        }

        return ContactNotificationTriggerPolicyOverride(
            policy = ContactNotificationTriggerPolicyOverridePolicy.deserialize(notificationTriggerPolicyOverridePolicy)
                ?: run {
                    logger.logAndReportError(
                        "Could not deserialize contact notification trigger policy override value {}",
                        notificationTriggerPolicyOverridePolicy,
                    )
                    return null
                },
            expiresAt = notificationTriggerPolicyOverrideExpiresAt,
        )
    }
}
