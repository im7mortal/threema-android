package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.systemupdates.updates.SystemUpdateToVersion127
import ch.threema.data.datatypes.PredefinedContact
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.awaitReflectAck
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedContactSyncUpdate
import ch.threema.protobuf.d2d.TransactionScope
import ch.threema.protobuf.d2d.sync.Contact
import ch.threema.protobuf.d2d.sync.contact
import ch.threema.storage.databaseupdate.DatabaseUpdateToVersion122
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * This task is needed for [SystemUpdateToVersion127]. It reflects all predefined contacts that exist in the database. They may have been updated by
 * [DatabaseUpdateToVersion122] and need to be reflected in case multi device is active.
 */
class SyncPredefinedContactsTask : ActiveTask<Unit>, PersistableTask, KoinComponent {
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val contactModelRepository: ContactModelRepository by inject()

    override val type = "SyncPredefinedContactsTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!multiDeviceManager.isMultiDeviceActive) {
            return
        }

        val contactModels = contactModelRepository.getByIdentities(
            PredefinedContact
                .getAllPredefinedContacts()
                .map(PredefinedContact::identity)
                .toSet(),
        )

        if (contactModels.isEmpty()) {
            return
        }

        val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()
        handle.createTransaction(
            keys = multiDeviceProperties.keys,
            scope = TransactionScope.Scope.CONTACT_SYNC,
            ttl = TRANSACTION_TTL_MAX,
        ).execute {
            contactModels
                .mapNotNull(ContactModel::data)
                .map { contactModelData ->
                    contact {
                        identity = contactModelData.identity
                        verificationLevel = when (contactModelData.verificationLevel) {
                            VerificationLevel.UNVERIFIED -> Contact.VerificationLevel.UNVERIFIED
                            VerificationLevel.SERVER_VERIFIED -> Contact.VerificationLevel.SERVER_VERIFIED
                            VerificationLevel.FULLY_VERIFIED -> Contact.VerificationLevel.FULLY_VERIFIED
                        }
                    }
                }
                .map { contact ->
                    val encryptedEnvelopeResult = getEncryptedContactSyncUpdate(
                        contact = contact,
                        multiDeviceProperties = multiDeviceProperties,
                    )
                    handle.reflect(encryptedEnvelopeResult)
                }
                .forEach { reflectId ->
                    handle.awaitReflectAck(reflectId)
                }
        }
    }

    override fun serialize() = SyncPredefinedContactsTaskData

    @Serializable
    object SyncPredefinedContactsTaskData : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> = SyncPredefinedContactsTask()
    }
}
