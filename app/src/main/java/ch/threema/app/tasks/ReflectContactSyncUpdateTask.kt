package ch.threema.app.tasks

import ch.threema.app.services.ApiService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.FileService
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.SymmetricEncryptionService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverridePolicy
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.blob.BlobScope
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.getEncryptedContactSyncUpdate
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.common.Image
import ch.threema.protobuf.common.blob
import ch.threema.protobuf.common.deltaImage
import ch.threema.protobuf.common.image
import ch.threema.protobuf.common.unit
import ch.threema.protobuf.d2d.sync.Contact
import ch.threema.protobuf.d2d.sync.ContactKt.readReceiptPolicyOverride
import ch.threema.protobuf.d2d.sync.ContactKt.typingIndicatorPolicyOverride
import ch.threema.protobuf.d2d.sync.ConversationCategory
import ch.threema.protobuf.d2d.sync.ConversationVisibility as ProtocolsConversationVisibility
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.protobuf.toProtobuf
import com.google.protobuf.kotlin.toByteString
import java.time.Instant
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectContactSyncUpdateTask")

abstract class ReflectContactSyncUpdateBaseTask(
    protected val contactIdentity: IdentityString,
) : ReflectContactSyncTask<Unit, Unit>(), KoinComponent {
    private val contactModelRepository: ContactModelRepository by inject()
    private val nonceFactory: NonceFactory by inject()

    /**
     * The task type. This is just used for debugging.
     */
    protected abstract val type: String

    /**
     * This method is called to check whether the contact reflection is still necessary. Note that
     * the general strategy to decide may differ between different types of tasks.
     */
    abstract fun isUpdateRequired(currentData: ContactModelData): Boolean

    /**
     * Get the contact sync that contains the delta updates.
     */
    abstract fun getContactSync(): Contact

    /**
     * As a precondition for an update task, the contact with the given identity must exist and the
     * changed data must differ from the current data.
     */
    final override val runPrecondition: () -> Boolean = {
        // The contact must exist and there needs to be a change
        contactModelRepository.getByIdentity(contactIdentity)?.data?.let {
            isUpdateRequired(it)
        } ?: false
    }

    /**
     * Inside the transaction we just reflect the contact sync update from [getContactSync] with the
     * [runPrecondition].
     */
    override val runInsideTransaction: suspend (handle: ActiveTaskCodec) -> Unit = { handle ->
        logger.info("Reflecting contact sync update of type {} for {}", type, contactIdentity)

        val encryptedEnvelopeResult = getEncryptedContactSyncUpdate(
            getContactSync().also {
                check(it.identity == contactIdentity) {
                    "Identity must match"
                }
            },
            mdProperties,
        )
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }

    override val runAfterSuccessfulTransaction: suspend (transactionResult: Unit) -> Unit = {
        // Nothing to do
    }
}

/**
 * This task must be run *before* the changes have been persisted. It cannot be run using the task
 * manager as it must be run immediately inside another task.
 */
abstract class ReflectContactSyncUpdateImmediateTask(contactIdentity: IdentityString) : ReflectContactSyncUpdateBaseTask(
    contactIdentity = contactIdentity,
) {
    /**
     * Check whether the data has changed. Note that immediate tasks are executed before the update
     * has been persisted. In this case, we reflect only if the current data is different to the new
     * data.
     */
    abstract fun hasDataChanged(currentData: ContactModelData): Boolean

    /**
     * An update of an immediate task is required if the data has changed.
     */
    final override fun isUpdateRequired(currentData: ContactModelData) = hasDataChanged(currentData)

    /**
     * Reflect the current change.
     *
     * @throws TransactionScope.TransactionException if the current contact data already contains
     * this change
     */
    suspend fun reflect(handle: ActiveTaskCodec) {
        reflectContactSync(handle)
    }

    /**
     * A task to reflect a new nickname.
     */
    class ReflectContactNickname(
        contactIdentity: IdentityString,
        private val newNickname: String,
    ) : ReflectContactSyncUpdateImmediateTask(
        contactIdentity,
    ) {
        override val type = "ReflectContactNickname"

        override fun hasDataChanged(currentData: ContactModelData): Boolean {
            return currentData.nickname != newNickname
        }

        override fun getContactSync() = contact {
            identity = contactIdentity
            nickname = newNickname
        }
    }

    /**
     * A task to reflect a new profile picture. Note that this task should always be run when a
     * profile picture has been received to ensure that the other devices can mark the blob as done
     * on the blob mirror.
     */
    class ReflectContactProfilePicture(
        contactIdentity: IdentityString,
        private val profilePictureUpdate: ProfilePictureUpdate,
    ) : ReflectContactSyncUpdateImmediateTask(
        contactIdentity = contactIdentity,
    ) {
        sealed interface ProfilePictureUpdate

        class UpdatedProfilePicture(
            val blobId: ByteArray,
            val nonce: ByteArray,
            val encryptionKey: ByteArray,
        ) : ProfilePictureUpdate

        data object RemovedProfilePicture : ProfilePictureUpdate

        override val type: String = "ReflectContactProfilePicture"

        /**
         * Note that we cannot check this based on the contact model data. Therefore, we assume that
         * the data has changed and rely on the caller to check this.
         */
        override fun hasDataChanged(currentData: ContactModelData): Boolean = true

        override fun getContactSync() = contact {
            identity = contactIdentity
            contactDefinedProfilePicture = deltaImage {
                when (profilePictureUpdate) {
                    is UpdatedProfilePicture -> {
                        updated = image {
                            type = Image.Type.JPEG
                            blob = blob {
                                id = profilePictureUpdate.blobId.toByteString()
                                nonce = profilePictureUpdate.nonce.toByteString()
                                key = profilePictureUpdate.encryptionKey.toByteString()
                            }
                        }
                    }

                    is RemovedProfilePicture -> {
                        removed = unit { }
                    }
                }
            }
        }
    }
}

/**
 * This task must be executed *after* the updates have been persisted to local storage. It must be
 * run using the task manager.
 */
abstract class ReflectContactSyncUpdateTask(
    contactIdentity: IdentityString,
) : ReflectContactSyncUpdateBaseTask(
    contactIdentity = contactIdentity,
),
    ActiveTask<Unit>,
    PersistableTask {
    /**
     * Return true if the change that should be reflected still matches the current data. Note that
     * if a task performs several changes, then *all* of the new values must be equal to
     * [currentData].
     */
    abstract fun isChangeValid(currentData: ContactModelData): Boolean

    /**
     * We only reflect a change if it is still valid. This is a consequence of the strategy to first
     * persist changes locally followed by scheduling a persistent task to reflect the changes.
     *
     * There are two cases where the data has been changed in the meantime:
     * - Bypassed d2d messages may have altered the contact, as the same fields have been changed on
     *   a linked device. In this case we do not reflect the change, as it already is outdated.
     * - The user has already changed the values again. In this case we can also skip the reflection
     *   because a new update task containing the changes will be scheduled.
     */
    final override fun isUpdateRequired(currentData: ContactModelData) = isChangeValid(currentData)

    /**
     * Invoke the task. Note that the reflected changes must already be written to the database.
     */
    final override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cannot reflect contact sync update of type {} when md is not active", type)
            return
        }

        reflectContactSync(handle)
    }

    /**
     * Reflect a new name.
     */
    class ReflectNameUpdate(
        private val newFirstName: String,
        private val newLastName: String,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type: String = "ReflectNameUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            currentData.firstName == newFirstName &&
                currentData.lastName == newLastName

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            firstName = newFirstName
            lastName = newLastName
        }

        override fun serialize(): SerializableTaskData = ReflectNameUpdateData(
            firstName = newFirstName,
            lastName = newLastName,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectNameUpdateData(
            private val firstName: String,
            private val lastName: String,
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectNameUpdate(
                    newFirstName = firstName,
                    newLastName = lastName,
                    contactIdentity = identity,
                )
        }
    }

    /**
     * Reflect a new read receipt policy.
     */
    class ReflectReadReceiptPolicyUpdate(
        private val readReceiptPolicy: ReadReceiptPolicy,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type: String = "ReflectReadReceiptPolicyUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            currentData.readReceiptPolicy == readReceiptPolicy

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            readReceiptPolicyOverride = readReceiptPolicyOverride {
                when (readReceiptPolicy) {
                    ReadReceiptPolicy.DEFAULT -> default = unit {}
                    ReadReceiptPolicy.SEND -> policy = ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.SEND_READ_RECEIPT
                    ReadReceiptPolicy.DONT_SEND -> policy = ch.threema.protobuf.d2d.sync.ReadReceiptPolicy.DONT_SEND_READ_RECEIPT
                }
            }
        }

        override fun serialize(): SerializableTaskData = ReflectReadReceiptPolicyUpdateData(
            readReceiptPolicy = readReceiptPolicy,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectReadReceiptPolicyUpdateData(
            private val readReceiptPolicy: ReadReceiptPolicy,
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectReadReceiptPolicyUpdate(
                    readReceiptPolicy = readReceiptPolicy,
                    contactIdentity = identity,
                )
        }
    }

    /**
     * Reflect a new typing indicator policy.
     */
    class ReflectTypingIndicatorPolicyUpdate(
        private val typingIndicatorPolicy: TypingIndicatorPolicy,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type: String = "ReflectTypingIndicatorPolicyUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            currentData.typingIndicatorPolicy == typingIndicatorPolicy

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            typingIndicatorPolicyOverride = typingIndicatorPolicyOverride {
                when (typingIndicatorPolicy) {
                    TypingIndicatorPolicy.DEFAULT -> default = unit {}

                    TypingIndicatorPolicy.SEND -> policy = ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.SEND_TYPING_INDICATOR

                    TypingIndicatorPolicy.DONT_SEND -> policy = ch.threema.protobuf.d2d.sync.TypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
                }
            }
        }

        override fun serialize(): SerializableTaskData = ReflectTypingIndicatorPolicyUpdateData(
            typingIndicatorPolicy = typingIndicatorPolicy,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectTypingIndicatorPolicyUpdateData(
            private val typingIndicatorPolicy: TypingIndicatorPolicy,
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectTypingIndicatorPolicyUpdate(
                    typingIndicatorPolicy = typingIndicatorPolicy,
                    contactIdentity = identity,
                )
        }
    }

    /**
     * Reflect a new activity state.
     */
    class ReflectActivityStateUpdate(
        private val newIdentityState: IdentityState,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type = "ReflectActivityStateUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.activityState == newIdentityState

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            activityState = when (newIdentityState) {
                IdentityState.ACTIVE -> Contact.ActivityState.ACTIVE
                IdentityState.INACTIVE -> Contact.ActivityState.INACTIVE
                IdentityState.INVALID -> Contact.ActivityState.INVALID
            }
        }

        override fun serialize(): SerializableTaskData = ReflectActivityStateUpdateData(
            identityState = newIdentityState,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectActivityStateUpdateData(
            private val identityState: IdentityState,
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectActivityStateUpdate(
                    newIdentityState = identityState,
                    contactIdentity = identity,
                )
        }
    }

    /**
     * Reflect a new feature mask.
     */
    class ReflectFeatureMaskUpdate(
        private val newFeatureMask: Long,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type = "ReflectFeatureMaskUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.featureMask == newFeatureMask.toULong()

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            featureMask = newFeatureMask
        }

        override fun serialize(): SerializableTaskData = ReflectFeatureMaskUpdateData(
            featureMask = newFeatureMask,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectFeatureMaskUpdateData(
            private val featureMask: Long,
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectFeatureMaskUpdate(
                    newFeatureMask = featureMask,
                    contactIdentity = identity,
                )
        }
    }

    /**
     * Reflect a new verification level.
     */
    class ReflectVerificationLevelUpdate(
        private val newVerificationLevel: VerificationLevel,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type = "ReflectVerificationLevelUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.verificationLevel == newVerificationLevel

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            verificationLevel = when (newVerificationLevel) {
                VerificationLevel.FULLY_VERIFIED -> Contact.VerificationLevel.FULLY_VERIFIED
                VerificationLevel.SERVER_VERIFIED -> Contact.VerificationLevel.SERVER_VERIFIED
                VerificationLevel.UNVERIFIED -> Contact.VerificationLevel.UNVERIFIED
            }
        }

        override fun serialize(): SerializableTaskData = ReflectVerificationLevelUpdateData(
            verificationLevel = newVerificationLevel,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectVerificationLevelUpdateData(
            private val verificationLevel: VerificationLevel,
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectVerificationLevelUpdate(
                    newVerificationLevel = verificationLevel,
                    contactIdentity = identity,
                )
        }
    }

    /**
     * Reflect a new work verification level.
     */
    class ReflectWorkVerificationLevelUpdate(
        private val newWorkVerificationLevel: WorkVerificationLevel,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type = "ReflectWorkVerificationLevelUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.workVerificationLevel == newWorkVerificationLevel

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            workVerificationLevel = when (newWorkVerificationLevel) {
                WorkVerificationLevel.NONE -> Contact.WorkVerificationLevel.NONE
                WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> Contact.WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
            }
        }

        override fun serialize(): SerializableTaskData = ReflectWorkVerificationLevelUpdateData(
            workVerificationLevel = newWorkVerificationLevel,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectWorkVerificationLevelUpdateData(
            private val workVerificationLevel: WorkVerificationLevel,
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectWorkVerificationLevelUpdate(
                    newWorkVerificationLevel = workVerificationLevel,
                    contactIdentity = identity,
                )
        }
    }

    /**
     * Reflect a new identity type.
     */
    class ReflectIdentityTypeUpdate(
        private val newIdentityType: IdentityType,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type = "ReflectIdentityTypeUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.identityType == newIdentityType

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            identityType = when (newIdentityType) {
                IdentityType.REGULAR -> Contact.IdentityType.REGULAR
                IdentityType.WORK -> Contact.IdentityType.WORK
            }
        }

        override fun serialize(): SerializableTaskData = ReflectIdentityTypeUpdateData(
            identityType = newIdentityType,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectIdentityTypeUpdateData(
            private val identityType: IdentityType,
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectIdentityTypeUpdate(
                    newIdentityType = identityType,
                    contactIdentity = identity,
                )
        }
    }

    /**
     * Reflect a new acquaintance level.
     */
    class ReflectAcquaintanceLevelUpdate(
        private val newAcquaintanceLevel: AcquaintanceLevel,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(contactIdentity) {
        override val type = "ReflectAcquaintanceLevelUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            currentData.acquaintanceLevel == newAcquaintanceLevel

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            acquaintanceLevel = when (newAcquaintanceLevel) {
                AcquaintanceLevel.DIRECT -> Contact.AcquaintanceLevel.DIRECT
                AcquaintanceLevel.GROUP_OR_DELETED -> Contact.AcquaintanceLevel.GROUP_OR_DELETED
            }
        }

        override fun serialize(): SerializableTaskData = ReflectAcquaintanceLevelUpdateData(
            acquaintanceLevel = newAcquaintanceLevel,
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectAcquaintanceLevelUpdateData(
            private val acquaintanceLevel: AcquaintanceLevel,
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectAcquaintanceLevelUpdate(
                    newAcquaintanceLevel = acquaintanceLevel,
                    contactIdentity = identity,
                )
        }
    }

    /**
     * Reflect a new user defined profile picture.
     */
    class ReflectUserDefinedProfilePictureUpdate(
        identity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity = identity,
    ),
        KoinComponent {
        private val apiService: ApiService by inject()
        private val fileService: FileService by inject()
        private val symmetricEncryptionService: SymmetricEncryptionService by inject()

        override val type = "ReflectUserDefinedProfilePictureUpdate"

        /**
         * Note that we do always sync the currently set user defined profile picture. This is to
         * prevent having to persist the whole profile picture. In the worst case, this can lead to
         * the same profile picture uploaded twice: once from a reflected device and later from this
         * device (if a race occurs).
         */
        override fun isChangeValid(currentData: ContactModelData) = true

        override fun getContactSync(): Contact {
            val userDefinedProfilePictureBytes =
                fileService.getUserDefinedProfilePictureStream(contactIdentity)
                    ?.buffered()
                    ?.use { it.readBytes() }
            return if (userDefinedProfilePictureBytes != null) {
                val encryptionResult = symmetricEncryptionService.encrypt(
                    userDefinedProfilePictureBytes,
                    symmetricEncryptionService.generateSymmetricKey(),
                    ProtocolDefines.CONTACT_PHOTO_NONCE,
                )
                val blobId = apiService.createUploader(
                    data = encryptionResult.data,
                    shouldPersist = false,
                    blobScope = BlobScope.Local,
                ).upload()

                checkNotNull(blobId) { "UploadCancelled" }

                contact {
                    identity = contactIdentity
                    userDefinedProfilePicture = deltaImage {
                        updated = image {
                            type = Image.Type.JPEG
                            blob = blob {
                                id = blobId.toByteString()
                                nonce = ProtocolDefines.CONTACT_PHOTO_NONCE.toByteString()
                                key = encryptionResult.key.toByteString()
                            }
                        }
                    }
                }
            } else {
                contact {
                    identity = contactIdentity
                    userDefinedProfilePicture = deltaImage {
                        removed = unit { }
                    }
                }
            }
        }

        override fun serialize(): SerializableTaskData = ReflectUserDefinedProfilePictureUpdateData(
            identity = contactIdentity,
        )

        @Serializable
        data class ReflectUserDefinedProfilePictureUpdateData(
            private val identity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectUserDefinedProfilePictureUpdate(
                    identity = identity,
                )
        }
    }

    /**
     * Reflect a new notification-trigger-policy-override
     */
    class ReflectNotificationTriggerPolicyOverrideUpdate(
        private val newNotificationTriggerPolicyOverride: ContactNotificationTriggerPolicyOverride?,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type = "ReflectNotificationTriggerPolicyOverrideUpdate"

        override fun isChangeValid(currentData: ContactModelData) =
            currentData.notificationTriggerPolicyOverride == newNotificationTriggerPolicyOverride

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity

            notificationTriggerPolicyOverride = newNotificationTriggerPolicyOverride.toProtobuf()
        }

        override fun serialize(): SerializableTaskData = ReflectNotificationTriggerPolicyOverrideUpdateDataV2(
            notificationTriggerPolicyOverridePolicy = newNotificationTriggerPolicyOverride?.policy?.serializedValue,
            notificationTriggerPolicyOverrideExpiresAt = newNotificationTriggerPolicyOverride?.expiresAt?.toEpochMilli(),
            contactIdentity = contactIdentity,
        )

        /**
         * Note that this is only used for backwards compatibility as we used to persist this task data. We still need to be able to restore this task
         * data as some clients may have persisted it.
         */
        @Deprecated("Use ReflectNotificationTriggerPolicyOverrideUpdateDataV2 instead")
        @Serializable
        data class ReflectNotificationTriggerPolicyOverrideUpdateData(
            private val notificationTriggerPolicyOverride: Long?,
            private val contactIdentity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> = ReflectNotificationTriggerPolicyOverrideUpdate(
                newNotificationTriggerPolicyOverride = deserializeContactNotificationTriggerPolicyOverride(),
                contactIdentity = contactIdentity,
            )

            private fun deserializeContactNotificationTriggerPolicyOverride(): ContactNotificationTriggerPolicyOverride? {
                if (notificationTriggerPolicyOverride == null) {
                    return null
                }

                return when {
                    // In case the value is -1, the contact is indefinitely muted
                    notificationTriggerPolicyOverride == -1L -> ContactNotificationTriggerPolicyOverride(
                        policy = ContactNotificationTriggerPolicyOverridePolicy.NEVER,
                        expiresAt = null,
                    )

                    // In case the value is greater than 0, it denotes the timestamp until when the contact is muted
                    notificationTriggerPolicyOverride > 0 -> ContactNotificationTriggerPolicyOverride(
                        policy = ContactNotificationTriggerPolicyOverridePolicy.NEVER,
                        expiresAt = Instant.ofEpochMilli(notificationTriggerPolicyOverride),
                    )

                    // In case of 0 or any negative value other than -1, there is no notification trigger policy override set
                    else -> null
                }
            }
        }

        @Serializable
        data class ReflectNotificationTriggerPolicyOverrideUpdateDataV2(
            private val notificationTriggerPolicyOverridePolicy: Int?,
            private val notificationTriggerPolicyOverrideExpiresAt: Long?,
            private val contactIdentity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectNotificationTriggerPolicyOverrideUpdate(
                    newNotificationTriggerPolicyOverride = deserializeContactNotificationTriggerPolicyOverride(),
                    contactIdentity = contactIdentity,
                )

            private fun deserializeContactNotificationTriggerPolicyOverride(): ContactNotificationTriggerPolicyOverride? {
                if (notificationTriggerPolicyOverridePolicy == null) {
                    return null
                }

                val policy = ContactNotificationTriggerPolicyOverridePolicy.deserialize(notificationTriggerPolicyOverridePolicy)
                    ?: error("Could not deserialize contact notification trigger policy with value $notificationTriggerPolicyOverridePolicy")
                val expiresAt = notificationTriggerPolicyOverrideExpiresAt?.let { expiresAt -> Instant.ofEpochMilli(expiresAt) }

                return ContactNotificationTriggerPolicyOverride(
                    policy = policy,
                    expiresAt = expiresAt,
                )
            }
        }
    }

    /**
     * Note that this task currently just reflects the current conversation category state of the contact as the conversation category is not part of
     * the contact model.
     */
    class ReflectConversationCategoryUpdate(
        contactIdentity: IdentityString,
        private val isPrivateChat: Boolean,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ),
        KoinComponent {
        private val conversationCategoryService: ConversationCategoryService by inject()

        override val type = "ReflectConversationCategoryUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            conversationCategoryService.isMarkedAsPrivate(
                conversationId = ContactConversationId(contactIdentity),
            ) == isPrivateChat

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity
            conversationCategory = if (isPrivateChat) {
                ConversationCategory.PROTECTED
            } else {
                ConversationCategory.DEFAULT
            }
        }

        override fun serialize(): SerializableTaskData = ReflectContactConversationCategoryUpdateData(
            contactIdentity = contactIdentity,
            isPrivateChat = isPrivateChat,
        )

        @Serializable
        data class ReflectContactConversationCategoryUpdateData(
            private val contactIdentity: IdentityString,
            private val isPrivateChat: Boolean,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectConversationCategoryUpdate(
                    isPrivateChat = isPrivateChat,
                    contactIdentity = contactIdentity,
                )
        }
    }

    class ReflectConversationVisibilityUpdate(
        private val conversationVisibility: ConversationVisibility,
        contactIdentity: IdentityString,
    ) : ReflectContactSyncUpdateTask(
        contactIdentity,
    ) {
        override val type = "ReflectConversationVisibilityUpdate"

        override fun isChangeValid(currentData: ContactModelData): Boolean =
            currentData.conversationVisibility == conversationVisibility

        override fun getContactSync(): Contact = contact {
            identity = contactIdentity

            conversationVisibility = when (this@ReflectConversationVisibilityUpdate.conversationVisibility) {
                ConversationVisibility.NORMAL -> ProtocolsConversationVisibility.NORMAL
                ConversationVisibility.ARCHIVED -> ProtocolsConversationVisibility.ARCHIVED
                ConversationVisibility.PINNED -> ProtocolsConversationVisibility.PINNED
            }
        }

        override fun serialize(): SerializableTaskData = ReflectConversationVisibilityUpdateData(
            conversationVisibilitySerialized = conversationVisibility.serializedValue,
            contactIdentity = contactIdentity,
        )

        @Serializable
        data class ReflectConversationVisibilityUpdateData(
            private val conversationVisibilitySerialized: Int,
            private val contactIdentity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> =
                ReflectConversationVisibilityUpdate(
                    conversationVisibility = requireConversationVisibility(),
                    contactIdentity = contactIdentity,
                )

            private fun requireConversationVisibility() = ConversationVisibility
                .deserialize(conversationVisibilitySerialized)
                ?: error("Could not deserialize conversation visibility from value $conversationVisibilitySerialized")
        }
    }

    /**
     * Note: This is only used to create the correct fully qualified name for the contained task data.
     */
    @Suppress("unused")
    object ReflectConversationVisibilityArchiveUpdate {
        /**
         * There used to be a separate task for reflecting the information whether a contact was archived or not. This task does not exist anymore and
         * is replaced by [ReflectConversationVisibilityUpdate]. This task data may still be persisted on some devices. In that case we now create a
         * [ReflectConversationVisibilityUpdate] from this task.
         *
         * TODO(ANDR-4974): Remove this.
         */
        @Deprecated("This data should not be used anymore. It just exists in case it has been persisted.")
        @Serializable
        data class ReflectConversationVisibilityArchiveUpdateData(
            private val isArchived: Boolean,
            private val contactIdentity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> {
                val conversationVisibility = when (isArchived) {
                    true -> ConversationVisibility.ARCHIVED
                    false -> ConversationVisibility.NORMAL
                }
                return ReflectConversationVisibilityUpdate(
                    conversationVisibility = conversationVisibility,
                    contactIdentity = contactIdentity,
                )
            }
        }
    }

    /**
     * Note: This is only used to create the correct fully qualified name for the contained task data.
     */
    @Suppress("unused")
    object ReflectConversationVisibilityPinnedUpdate {

        /**
         * There used to be a separate task for reflecting the information whether a contact was archived or not. This task does not exist anymore and
         * is replaced by [ReflectConversationVisibilityUpdate]. This task data may still be persisted on some devices. In that case we create now a
         * [ReflectConversationVisibilityUpdate] from this task.
         *
         * TODO(ANDR-4974): Remove this.
         */
        @Deprecated("This data should not be used anymore. It just exists in case it has been persisted.")
        @Serializable
        data class ReflectConversationVisibilityPinnedUpdateData(
            private val isPinned: Boolean,
            private val contactIdentity: IdentityString,
        ) : SerializableTaskData {
            override fun createTask(): Task<*, TaskCodec> {
                val conversationVisibility = when (isPinned) {
                    true -> ConversationVisibility.PINNED
                    false -> ConversationVisibility.NORMAL
                }
                return ReflectConversationVisibilityUpdate(
                    conversationVisibility = conversationVisibility,
                    contactIdentity = contactIdentity,
                )
            }
        }
    }
}
