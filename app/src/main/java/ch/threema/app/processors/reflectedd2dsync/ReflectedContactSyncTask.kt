package ch.threema.app.processors.reflectedd2dsync

import ch.threema.app.BuildConfig
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.managers.ServiceManager
import ch.threema.app.utils.AppVersionProvider
import ch.threema.app.utils.ExifInterface
import ch.threema.base.ThreemaException
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.contentEquals
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.common.Blob
import ch.threema.protobuf.common.DeltaImage
import ch.threema.protobuf.d2d.ContactSync
import ch.threema.protobuf.d2d.sync.Contact
import ch.threema.protobuf.d2d.sync.ConversationCategory
import ch.threema.protobuf.d2d.sync.ConversationVisibility as ProtocolsConversationVisibility
import ch.threema.protobuf.d2d.sync.contactDefinedProfilePictureOrNull
import ch.threema.protobuf.d2d.sync.userDefinedProfilePictureOrNull
import ch.threema.protobuf.d2d.sync.workAvailabilityStatusOrNull
import ch.threema.protobuf.toDataType
import java.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectedContactSyncTask")

class ReflectedContactSyncTask(
    private val contactSync: ContactSync,
    private val contactModelRepository: ContactModelRepository,
    private val serviceManager: ServiceManager,
    private val globalEventBuses: GlobalEventBuses,
) : KoinComponent {
    private val conversationCategoryService by lazy { serviceManager.conversationCategoryService }
    private val fileService by lazy { serviceManager.fileService }
    private val symmetricEncryptionService: SymmetricEncryptionService by inject()

    fun run() {
        when (contactSync.actionCase) {
            ContactSync.ActionCase.CREATE -> handleContactCreate(contactSync.create)
            ContactSync.ActionCase.UPDATE -> handleContactUpdate(contactSync.update)
            ContactSync.ActionCase.ACTION_NOT_SET -> logger.warn("No action set for contact sync")
            null -> logger.warn("Action is null for contact sync")
        }
    }

    private fun handleContactCreate(contactCreate: ContactSync.Create) {
        logger.info("Processing reflected contact create")

        if (!contactCreate.hasContact()) {
            logger.warn("No contact provided in reflected contact create")
            return
        }

        // Check whether this contact already exists
        if (contactModelRepository.getByIdentity(contactCreate.contact.identity) != null) {
            logger.error(
                "Discarding 'create' message, contact {} already exists.",
                contactCreate.contact.identity,
            )
            return
        }

        // Build contact model data based on the reflected data
        val contactModelData = try {
            contactCreate.contact.toNewContactModelData()
        } catch (e: MissingPropertyException) {
            logger.error(
                "Property {} is missing for a new contact. Discarding contact sync create message.",
                e.propertyName,
            )
            return
        }

        // Create new contact
        try {
            contactModelRepository.createFromSync(contactModelData)
        } catch (e: ContactCreateException) {
            logger.error("Could not create contact", e)
            return
        }

        applyProfilePictures(contactCreate.contact)

        logger.info("New contact {} successfully created from sync", contactCreate.contact.identity)
    }

    private fun handleContactUpdate(contactUpdate: ContactSync.Update) {
        logger.info("Processing reflected contact update")

        val identity = contactUpdate.contact.identity

        val contactModel = contactModelRepository.getByIdentity(identity)
        if (contactModel != null) {
            applyContactModelUpdate(contactModel, contactUpdate.contact)
            globalEventBuses.contacts.emit(ContactEvent.ContactUpdated(Identity(identity)))
        } else {
            logger.error("Got a contact update for an unknown contact: {}", identity)
        }
    }

    private fun applyContactModelUpdate(contactModel: ContactModel, contact: Contact) {
        applyNames(contactModel, contact)

        applyVerificationLevel(contactModel, contact)

        applyIdentityType(contactModel, contact)

        applyAcquaintanceLevel(contactModel, contact)

        applyActivityState(contactModel, contact)

        applyFeatureMask(contactModel, contact)

        applySyncState(contactModel, contact)

        applyReadReceiptPolicyOverride(contactModel, contact)

        applyTypingIndicatorPolicyOverride(contactModel, contact)

        applyNotificationTriggerPolicy(contactModel, contact)

        applyProfilePictures(contact)

        applyConversationCategory(contact)

        applyConversationVisibility(contactModel, contact)

        if (BuildConfig.AVAILABILITY_STATUS_ENABLED) {
            applyAvailabilityStatus(contactModel, contact)
        }
    }

    private fun applyNames(contactModel: ContactModel, contact: Contact) {
        contact.getFirstNameOrNull()?.let {
            contactModel.setFirstNameFromSync(it)
        }
        contact.getLastNameOrNull()?.let {
            contactModel.setLastNameFromSync(it)
        }
        contact.getNicknameOrNull()?.let {
            contactModel.setNicknameFromSync(it)
        }
    }

    private fun applyVerificationLevel(contactModel: ContactModel, contact: Contact) {
        contact.getVerificationLevelOrNull()?.let {
            contactModel.setVerificationLevelFromSync(it)
        }
        contact.getWorkVerificationLevelOrNull()?.let {
            contactModel.setWorkVerificationLevelFromSync(it)
        }
    }

    private fun applyIdentityType(contactModel: ContactModel, contact: Contact) {
        contact.getIdentityTypeOrNull()?.let {
            contactModel.setIdentityTypeFromSync(it)
        }
    }

    private fun applyAcquaintanceLevel(contactModel: ContactModel, contact: Contact) {
        contact.getAcquaintanceLevelOrNull()?.let {
            contactModel.setAcquaintanceLevelFromSync(it)
        }
    }

    private fun applyActivityState(contactModel: ContactModel, contact: Contact) {
        contact.getActivityStateOrNull()?.let {
            contactModel.setActivityStateFromSync(it)
        }
    }

    private fun applyFeatureMask(contactModel: ContactModel, contact: Contact) {
        contact.getFeatureMaskOrNull()?.let {
            contactModel.setFeatureMaskFromSync(it)
        }
    }

    private fun applySyncState(contactModel: ContactModel, contact: Contact) {
        contact.getSyncStateOrNull()?.let {
            contactModel.setSyncStateFromSync(it)
        }
    }

    private fun applyReadReceiptPolicyOverride(contactModel: ContactModel, contact: Contact) {
        contact.getReadReceiptPolicyOrNull()?.let {
            contactModel.setReadReceiptPolicyFromSync(it)
        }
    }

    private fun applyTypingIndicatorPolicyOverride(contactModel: ContactModel, contact: Contact) {
        contact.getTypingIndicatorPolicyOrNull()?.let {
            contactModel.setTypingIndicatorPolicyFromSync(it)
        }
    }

    private fun applyAvailabilityStatus(contactModel: ContactModel, contact: Contact) {
        val availabilityStatus = contact.workAvailabilityStatusOrNull
            ?.let { workAvailabilityStatus ->
                AvailabilityStatus.fromProtocolModel(workAvailabilityStatus)
                    ?: unrecognizedValue("work availability status")
            }
        if (availabilityStatus != null) {
            contactModel.setAvailabilityStatusFromSync(availabilityStatus)
        }
    }

    private fun applyConversationCategory(contact: Contact) {
        if (!contact.hasConversationCategory()) {
            return
        }
        when (contact.conversationCategory) {
            ConversationCategory.DEFAULT -> false
            ConversationCategory.PROTECTED -> true
            ConversationCategory.UNRECOGNIZED -> unrecognizedValue("conversation category")
            null -> nullValue("conversation category")
        }?.let { isPrivateChat ->
            val contactConversationId = ContactConversationId(contact.identity)
            if (isPrivateChat) {
                conversationCategoryService.persistAddPrivateMark(contactConversationId)
            } else {
                conversationCategoryService.persistRemovePrivateMark(contactConversationId)
            }
        }
    }

    private fun applyNotificationTriggerPolicy(contactModel: ContactModel, contact: Contact) {
        if (contact.hasNotificationTriggerPolicyOverride()) {
            contactModel.setNotificationTriggerPolicyOverrideFromSync(
                contact.notificationTriggerPolicyOverride.toDataType(),
            )
        }
    }

    private fun applyProfilePictures(contact: Contact) {
        applyContactDefinedProfilePicture(contact)
        applyUserDefinedProfilePicture(contact)
    }

    private fun applyContactDefinedProfilePicture(contact: Contact) {
        when (contact.contactDefinedProfilePictureOrNull?.imageCase) {
            DeltaImage.ImageCase.UPDATED -> {
                contact.contactDefinedProfilePicture.updated.blob.loadAndMarkAsDone { blob ->
                    logger.info("Applying updated contact defined profile picture from sync")

                    if (!ExifInterface.isJpegFormat(blob)) {
                        logger.warn("Received contact defined profile picture that is not a jpeg")
                    }

                    if (!fileService.getContactDefinedProfilePictureStream(contact.identity).contentEquals(blob)) {
                        fileService.writeContactDefinedProfilePicture(contact.identity, blob)
                        onAvatarChanged(contact.identity)
                    }
                }
            }

            DeltaImage.ImageCase.REMOVED -> {
                if (fileService.hasContactDefinedProfilePicture(contact.identity)) {
                    logger.info("Removing contact defined profile picture from sync")
                    fileService.removeContactDefinedProfilePicture(contact.identity)
                    onAvatarChanged(contact.identity)
                }
            }

            DeltaImage.ImageCase.IMAGE_NOT_SET -> logger.warn("Contact defined profile picture is not set")
            null -> Unit
        }
    }

    private fun applyUserDefinedProfilePicture(contact: Contact) {
        when (contact.userDefinedProfilePictureOrNull?.imageCase) {
            DeltaImage.ImageCase.UPDATED -> {
                logger.info("Applying updated user defined profile picture from sync")
                contact.userDefinedProfilePicture.updated.blob.loadAndMarkAsDone { blob ->
                    if (!fileService.getUserDefinedProfilePictureStream(contact.identity).contentEquals(blob)) {
                        fileService.writeUserDefinedProfilePicture(contact.identity, blob)
                        onAvatarChanged(contact.identity)
                    }
                }
            }

            DeltaImage.ImageCase.REMOVED -> {
                if (fileService.hasUserDefinedProfilePicture(contact.identity)) {
                    logger.info("Removing user defined profile picture from sync")
                    fileService.removeUserDefinedProfilePicture(contact.identity)
                    onAvatarChanged(contact.identity)
                }
            }

            DeltaImage.ImageCase.IMAGE_NOT_SET -> logger.warn("User defined profile picture is not set")
            null -> Unit
        }
    }

    private fun onAvatarChanged(identity: IdentityString) {
        globalEventBuses.contacts.emit(ContactEvent.ContactProfilePictureUpdated(Identity(identity)))
    }

    private fun Blob.loadAndMarkAsDone(persistBlob: (blob: ByteArray) -> Unit) {
        val blobLoadingResult = loadAndMarkAsDone(
            okHttpClient = serviceManager.okHttpClient,
            version = AppVersionProvider.appVersion,
            serverAddressProvider = serviceManager.serverAddressProviderService.serverAddressProvider,
            multiDevicePropertyProvider = serviceManager.multiDeviceManager.propertiesProvider,
            symmetricEncryptionService = symmetricEncryptionService,
            fallbackNonce = ProtocolDefines.CONTACT_PHOTO_NONCE,
            downloadBlobScope = BlobScope.Local,
            markAsDoneBlobScope = BlobScope.Local,
        )
        when (blobLoadingResult) {
            is ReflectedBlobDownloader.BlobLoadingResult.Success -> {
                persistBlob(blobLoadingResult.blobBytes)
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobMirrorNotAvailable -> {
                logger.warn("Cannot download blob because blob mirror is not available", blobLoadingResult.exception)
                throw ProtocolException("Blob mirror not available")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.DecryptionFailed -> {
                logger.error("Could not decrypt profile picture blob", blobLoadingResult.exception)
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobNotFound -> {
                logger.error("Could not download profile picture because the blob was not found")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.BlobDownloadCancelled -> {
                logger.error("Could not download profile picture because the download was cancelled")
            }

            is ReflectedBlobDownloader.BlobLoadingResult.Other -> {
                logger.error("Could not download profile picture because of an exception", blobLoadingResult.exception)
            }
        }
    }

    private fun applyConversationVisibility(contactModel: ContactModel, contact: Contact) {
        if (contact.hasConversationVisibility()) {
            val conversationVisibility = contact.getConversationVisibilityOrNull()
                ?: run {
                    logger.warn("Conversation visibility is set but cannot be applied because it is null")
                    return
                }
            contactModel.setConversationVisibilityFromSync(conversationVisibility)
        }
    }

    private fun unrecognizedValue(valueName: String): Nothing? {
        logger.warn("Unrecognized {}", valueName)
        return null
    }

    private fun nullValue(valueName: String): Nothing? {
        logger.warn("Value {} is null", valueName)
        return null
    }

    private fun Contact.getFirstNameOrNull(): String? {
        return if (hasFirstName()) {
            firstName
        } else {
            null
        }
    }

    private fun Contact.getLastNameOrNull(): String? {
        return if (hasLastName()) {
            lastName
        } else {
            null
        }
    }

    private fun Contact.getNicknameOrNull(): String? {
        return if (hasNickname()) {
            nickname
        } else {
            null
        }
    }

    private fun Contact.getVerificationLevelOrNull(): VerificationLevel? {
        return if (hasVerificationLevel()) {
            when (verificationLevel) {
                Contact.VerificationLevel.FULLY_VERIFIED -> VerificationLevel.FULLY_VERIFIED
                Contact.VerificationLevel.SERVER_VERIFIED -> VerificationLevel.SERVER_VERIFIED
                Contact.VerificationLevel.UNVERIFIED -> VerificationLevel.UNVERIFIED
                Contact.VerificationLevel.UNRECOGNIZED -> unrecognizedValue("verification level")
                null -> nullValue("verification level")
            }
        } else {
            null
        }
    }

    private fun Contact.getWorkVerificationLevelOrNull(): WorkVerificationLevel? {
        return if (hasWorkVerificationLevel()) {
            when (workVerificationLevel) {
                Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
                Contact.WorkVerificationLevel.NONE -> WorkVerificationLevel.NONE
                Contact.WorkVerificationLevel.UNRECOGNIZED -> unrecognizedValue("work verification level")
                null -> nullValue("work verification level")
            }
        } else {
            null
        }
    }

    private fun Contact.getIdentityTypeOrNull(): IdentityType? {
        return if (hasIdentityType()) {
            when (identityType) {
                Contact.IdentityType.REGULAR -> IdentityType.REGULAR
                Contact.IdentityType.WORK -> IdentityType.WORK
                Contact.IdentityType.UNRECOGNIZED -> unrecognizedValue("identity type")
                null -> nullValue("identity type")
            }
        } else {
            null
        }
    }

    private fun Contact.getAcquaintanceLevelOrNull(): AcquaintanceLevel? {
        return if (hasAcquaintanceLevel()) {
            when (acquaintanceLevel) {
                Contact.AcquaintanceLevel.DIRECT -> AcquaintanceLevel.DIRECT
                Contact.AcquaintanceLevel.GROUP_OR_DELETED -> AcquaintanceLevel.GROUP_OR_DELETED
                Contact.AcquaintanceLevel.UNRECOGNIZED -> unrecognizedValue("acquaintance level")
                null -> nullValue("acquaintance level")
            }
        } else {
            null
        }
    }

    private fun Contact.getActivityStateOrNull(): IdentityState? {
        return if (hasActivityState()) {
            when (activityState) {
                Contact.ActivityState.ACTIVE -> IdentityState.ACTIVE
                Contact.ActivityState.INACTIVE -> IdentityState.INACTIVE
                Contact.ActivityState.INVALID -> IdentityState.INVALID
                Contact.ActivityState.UNRECOGNIZED -> unrecognizedValue("activity state")
                null -> nullValue("activity state")
            }
        } else {
            null
        }
    }

    private fun Contact.getFeatureMaskOrNull(): ULong? {
        return if (hasFeatureMask()) {
            featureMask.toULong()
        } else {
            null
        }
    }

    private fun Contact.getSyncStateOrNull(): ContactSyncState? {
        return if (hasSyncState()) {
            when (syncState) {
                Contact.SyncState.INITIAL -> ContactSyncState.INITIAL
                Contact.SyncState.IMPORTED -> ContactSyncState.IMPORTED
                Contact.SyncState.CUSTOM -> ContactSyncState.CUSTOM
                Contact.SyncState.UNRECOGNIZED -> unrecognizedValue("sync state")
                null -> nullValue("sync state")
            }
        } else {
            null
        }
    }

    private fun Contact.getReadReceiptPolicyOrNull(): ReadReceiptPolicy? {
        return if (hasReadReceiptPolicyOverride()) {
            when {
                readReceiptPolicyOverride.hasDefault() -> ReadReceiptPolicy.DEFAULT
                readReceiptPolicyOverride.hasPolicy() -> when (readReceiptPolicyOverride.policy) {
                    ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.SEND_READ_RECEIPT -> ReadReceiptPolicy.SEND
                    ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT -> ReadReceiptPolicy.DONT_SEND
                    ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.UNRECOGNIZED -> unrecognizedValue("read receipt policy override")
                    null -> nullValue("read receipt policy override")
                }

                else -> {
                    logger.warn("Read receipt policy override does not have default nor policy")
                    null
                }
            }
        } else {
            null
        }
    }

    private fun Contact.getTypingIndicatorPolicyOrNull(): TypingIndicatorPolicy? {
        return if (hasTypingIndicatorPolicyOverride()) {
            when {
                typingIndicatorPolicyOverride.hasDefault() -> TypingIndicatorPolicy.DEFAULT
                typingIndicatorPolicyOverride.hasPolicy() -> when (typingIndicatorPolicyOverride.policy) {
                    ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.SEND
                    ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.DONT_SEND
                    ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.UNRECOGNIZED -> unrecognizedValue("typing indicator policy")
                    null -> nullValue("typing indicator policy")
                }

                else -> {
                    logger.warn("Typing indicator policy override does not have default nor policy")
                    null
                }
            }
        } else {
            null
        }
    }

    private fun Contact.getWorkLastFullSyncAtOrNull(): Instant? {
        return if (this.hasWorkLastFullSyncAt()) {
            Instant.ofEpochMilli(workLastFullSyncAt)
        } else {
            null
        }
    }

    private fun Contact.getConversationVisibilityOrNull(): ConversationVisibility? {
        if (!hasConversationVisibility()) {
            return null
        }

        return when (conversationVisibility) {
            ProtocolsConversationVisibility.NORMAL -> ConversationVisibility.NORMAL
            ProtocolsConversationVisibility.PINNED -> ConversationVisibility.PINNED
            ProtocolsConversationVisibility.ARCHIVED -> ConversationVisibility.ARCHIVED
            ProtocolsConversationVisibility.UNRECOGNIZED -> unrecognizedValue("conversation visibility")
            null -> nullValue("conversation visibility")
        }
    }

    private fun Contact.getAvailabilityStatusOrNone(): AvailabilityStatus {
        return if (hasWorkAvailabilityStatus()) {
            AvailabilityStatus.fromProtocolModel(workAvailabilityStatus) ?: AvailabilityStatus.None
        } else {
            AvailabilityStatus.None
        }
    }

    private fun Contact.getPublicKeyOrNull(): ByteArray? {
        return if (hasPublicKey()) {
            publicKey.toByteArray()
        } else {
            null
        }
    }

    private fun Contact.getCreatedAtOrNull(): Instant? {
        return if (hasCreatedAt()) {
            Instant.ofEpochMilli(createdAt)
        } else {
            null
        }
    }

    /**
     * Get the contact model data for the synced contact. Note that this expects a new contact and
     * therefore requires all mandatory properties to be set according to the protocol. If an
     * optional property is not set, default values are used.
     *
     * @throws MissingPropertyException if a required property for a new contact is missing
     */
    private fun Contact.toNewContactModelData(): ContactModelData {
        val contactNotificationTriggerPolicyOverride = if (hasNotificationTriggerPolicyOverride()) {
            notificationTriggerPolicyOverride.toDataType()
        } else {
            null
        }

        return ContactModelData(
            identity = identity,
            publicKey = getPublicKeyOrNull() ?: missingProperty("publicKey"),
            createdAt = getCreatedAtOrNull() ?: missingProperty("createdAt"),
            lastUpdateAt = null,
            firstName = getFirstNameOrNull() ?: "",
            lastName = getLastNameOrNull() ?: "",
            nickname = getNicknameOrNull(),
            verificationLevel = getVerificationLevelOrNull() ?: VerificationLevel.UNVERIFIED,
            workVerificationLevel = getWorkVerificationLevelOrNull() ?: WorkVerificationLevel.NONE,
            identityType = getIdentityTypeOrNull() ?: IdentityType.REGULAR,
            acquaintanceLevel = getAcquaintanceLevelOrNull() ?: AcquaintanceLevel.DIRECT,
            activityState = getActivityStateOrNull() ?: IdentityState.ACTIVE,
            syncState = getSyncStateOrNull() ?: ContactSyncState.INITIAL,
            featureMask = getFeatureMaskOrNull() ?: missingProperty("featureMask"),
            readReceiptPolicy = getReadReceiptPolicyOrNull() ?: ReadReceiptPolicy.DEFAULT,
            typingIndicatorPolicy = getTypingIndicatorPolicyOrNull() ?: TypingIndicatorPolicy.DEFAULT,
            conversationVisibility = getConversationVisibilityOrNull() ?: ConversationVisibility.NORMAL,
            androidContactLookupInfo = null,
            localAvatarExpires = null,
            isRestored = false,
            profilePictureBlobId = null,
            jobTitle = null,
            department = null,
            notificationTriggerPolicyOverride = contactNotificationTriggerPolicyOverride,
            availabilityStatus = getAvailabilityStatusOrNone(),
            workLastFullSyncAt = getWorkLastFullSyncAtOrNull(),
        )
    }

    private fun missingProperty(propertyName: String): Nothing =
        throw MissingPropertyException(propertyName)

    private class MissingPropertyException(val propertyName: String) :
        ThreemaException("Missing property '")
}
