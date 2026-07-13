package ch.threema.app.tasks

import ch.threema.app.BuildConfig
import ch.threema.app.services.ContactService.ProfilePictureUploadData
import ch.threema.app.services.ConversationCategoryService
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverridePolicy
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.models.ContactModelData
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.protobuf.common.DeltaImage
import ch.threema.protobuf.common.Image
import ch.threema.protobuf.common.blob
import ch.threema.protobuf.common.deltaImage
import ch.threema.protobuf.common.image
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.Contact
import ch.threema.protobuf.d2d.sync.ContactKt
import ch.threema.protobuf.d2d.sync.ContactKt.deprecatedNotificationSoundPolicyOverride
import ch.threema.protobuf.d2d.sync.ConversationCategory
import ch.threema.protobuf.d2d.sync.ConversationVisibility as ProtocolsConversationVisibility
import ch.threema.protobuf.d2d.sync.contact
import com.google.protobuf.kotlin.toByteString

abstract class ReflectContactSyncTask<TransactionResult, TaskResult>() : ReflectSyncTask<TransactionResult, TaskResult>(
    transactionScope = TransactionScope.Scope.CONTACT_SYNC,
) {
    protected suspend fun reflectContactSync(handle: ActiveTaskCodec): TaskResult {
        return reflectSync(handle)
    }
}

fun ContactModelData.toFullSyncContact(
    conversationCategoryService: ConversationCategoryService? = null,
    contactDefinedProfilePictureUpload: ProfilePictureUploadData? = null,
    userDefinedProfilePictureUpload: ProfilePictureUploadData? = null,
): Contact {
    val data = this
    return contact {
        identity = data.identity
        publicKey = data.publicKey.toByteString()
        createdAt = data.createdAt.toEpochMilli()
        firstName = data.firstName
        lastName = data.lastName
        data.nickname?.let { nickname = it }
        verificationLevel = data.getSyncVerificationLevel()
        workVerificationLevel = data.getSyncWorkVerificationLevel()
        identityType = data.getSyncIdentityType()
        acquaintanceLevel = data.getSyncAcquaintanceLevel()
        activityState = data.getSyncActivityState()
        featureMask = data.featureMask.toLong()
        syncState = data.getSyncSyncState()
        readReceiptPolicyOverride = data.getSyncReadReceiptPolicyOverride()
        typingIndicatorPolicyOverride = data.getSyncTypingIndicatorPolicyOverride()
        notificationTriggerPolicyOverride = data.getSyncNotificationTriggerPolicyOverride()
        deprecatedNotificationSoundPolicyOverride = deprecatedNotificationSoundPolicyOverride {
            default = unit {}
        }
        contactDefinedProfilePicture = contactDefinedProfilePictureUpload.toDeltaImage()
        userDefinedProfilePicture = userDefinedProfilePictureUpload.toDeltaImage()
        conversationCategory = data.getSyncConversationCategory(conversationCategoryService)
        conversationVisibility = data.getProtocolsConversationVisibility()

        if (BuildConfig.AVAILABILITY_STATUS_ENABLED && data.availabilityStatus != AvailabilityStatus.None) {
            workAvailabilityStatus = data.availabilityStatus.toProtocolModel()
        }
    }
}

private fun ContactModelData.getSyncVerificationLevel(): Contact.VerificationLevel =
    when (this.verificationLevel) {
        VerificationLevel.FULLY_VERIFIED -> Contact.VerificationLevel.FULLY_VERIFIED
        VerificationLevel.SERVER_VERIFIED -> Contact.VerificationLevel.SERVER_VERIFIED
        VerificationLevel.UNVERIFIED -> Contact.VerificationLevel.UNVERIFIED
    }

private fun ContactModelData.getSyncWorkVerificationLevel(): Contact.WorkVerificationLevel =
    when (this.workVerificationLevel) {
        WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
        WorkVerificationLevel.NONE -> Contact.WorkVerificationLevel.NONE
    }

private fun ContactModelData.getSyncIdentityType(): Contact.IdentityType =
    when (this.identityType) {
        IdentityType.REGULAR -> Contact.IdentityType.REGULAR
        IdentityType.WORK -> Contact.IdentityType.WORK
    }

private fun ContactModelData.getSyncAcquaintanceLevel(): Contact.AcquaintanceLevel =
    when (this.acquaintanceLevel) {
        AcquaintanceLevel.DIRECT -> Contact.AcquaintanceLevel.DIRECT
        AcquaintanceLevel.GROUP_OR_DELETED -> Contact.AcquaintanceLevel.GROUP_OR_DELETED
    }

private fun ContactModelData.getSyncActivityState(): Contact.ActivityState =
    when (this.activityState) {
        IdentityState.ACTIVE -> Contact.ActivityState.ACTIVE
        IdentityState.INACTIVE -> Contact.ActivityState.INACTIVE
        IdentityState.INVALID -> Contact.ActivityState.INVALID
    }

private fun ContactModelData.getSyncSyncState(): Contact.SyncState =
    // TODO(ANDR-2327): Consolidate this mechanism
    if (androidContactLookupInfo != null) {
        Contact.SyncState.IMPORTED
    } else if (lastName.isBlank() && firstName.isBlank()) {
        Contact.SyncState.INITIAL
    } else {
        Contact.SyncState.CUSTOM
    }

private fun ContactModelData.getSyncReadReceiptPolicyOverride(): Contact.ReadReceiptPolicyOverride =
    ContactKt.readReceiptPolicyOverride {
        when (readReceiptPolicy) {
            ReadReceiptPolicy.DEFAULT -> default = unit { }
            ReadReceiptPolicy.SEND -> policy = ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.SEND_READ_RECEIPT
            ReadReceiptPolicy.DONT_SEND -> policy = ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
        }
    }

private fun ContactModelData.getSyncTypingIndicatorPolicyOverride(): Contact.TypingIndicatorPolicyOverride =
    ContactKt.typingIndicatorPolicyOverride {
        when (typingIndicatorPolicy) {
            TypingIndicatorPolicy.DEFAULT -> default = unit { }
            TypingIndicatorPolicy.SEND -> policy = ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR
            TypingIndicatorPolicy.DONT_SEND -> policy = ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
        }
    }

private fun ContactModelData.getSyncNotificationTriggerPolicyOverride(): Contact.NotificationTriggerPolicyOverride {
    return ContactKt.notificationTriggerPolicyOverride {
        if (notificationTriggerPolicyOverride == null) {
            default = unit {}
        } else {
            when (notificationTriggerPolicyOverride.policy) {
                ContactNotificationTriggerPolicyOverridePolicy.NEVER ->
                    policy = ContactKt.NotificationTriggerPolicyOverrideKt.policy {
                        policy = Contact.NotificationTriggerPolicyOverride.Policy.NotificationTriggerPolicy.NEVER
                        notificationTriggerPolicyOverride.expiresAt?.toEpochMilli()?.let { expiresAtLong ->
                            expiresAt = expiresAtLong
                        }
                    }
            }
        }
    }
}

// TODO(ANDR-3034): Use conversation category from the new contact model
private fun ContactModelData.getSyncConversationCategory(
    conversationCategoryService: ConversationCategoryService?,
): ConversationCategory {
    val isMarkedAsPrivate = conversationCategoryService?.isMarkedAsPrivate(
        conversationId = ContactConversationId(
            identity = identity,
        ),
    )
    return if (isMarkedAsPrivate == true) {
        ConversationCategory.PROTECTED
    } else {
        ConversationCategory.DEFAULT
    }
}

private fun ContactModelData.getProtocolsConversationVisibility(): ProtocolsConversationVisibility = when (conversationVisibility) {
    ConversationVisibility.NORMAL -> ProtocolsConversationVisibility.NORMAL
    ConversationVisibility.ARCHIVED -> ProtocolsConversationVisibility.ARCHIVED
    ConversationVisibility.PINNED -> ProtocolsConversationVisibility.PINNED
}

private fun ProfilePictureUploadData?.toDeltaImage(): DeltaImage {
    return if (this == null) {
        deltaImage { removed = unit { } }
    } else {
        val uploadData = this
        deltaImage {
            updated = image {
                type = Image.Type.JPEG
                blob = blob {
                    id = uploadData.blobId.toByteString()
                    nonce = ProtocolDefines.CONTACT_PHOTO_NONCE.toByteString()
                    key = uploadData.encryptionKey.toByteString()
                    uploadedAt = uploadData.uploadedAt
                }
            }
        }
    }
}
