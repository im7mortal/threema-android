package ch.threema.app.tasks

import ch.threema.app.BuildConfig
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.AvailabilityStatusTaskData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedContactSyncUpdate
import ch.threema.domain.types.IdentityString
import ch.threema.logging.logAndReportError
import ch.threema.protobuf.d2d.TransactionScope.Scope
import ch.threema.protobuf.d2d.sync.contact
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("BatchUpdateContactAvailabilityStatusTask")

/**
 *  Handling a batch update of `contact.availabilityStatus` values.
 *
 *  If multi-device is active at the time this tasks executes, all changes of [availabilityStatuses] are reflected in a *single* transaction.
 *  No changes will be persisted locally in case the transaction did not commit successfully.
 *
 *  Changes are persisted locally using a single database call from [ContactModelRepository.persistAvailabilityStatuses].
 */
class BatchUpdateContactAvailabilityStatusTask private constructor(
    private val availabilityStatuses: Map<IdentityString, AvailabilityStatus>,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {

    private val multiDeviceManager: MultiDeviceManager by inject()
    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    private val nonceFactory: NonceFactory by inject()
    private val contactModelRepository: ContactModelRepository by inject()

    override val type: String = "BatchUpdateContactAvailabilityStatusTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        if (!BuildConfig.AVAILABILITY_STATUS_ENABLED) {
            logger.error("Cannot process availability status changes because this feature is not supported by this build")
            return
        }
        if (availabilityStatuses.isEmpty()) {
            logger.warn("No availability status changes given")
            return
        }
        if (multiDeviceManager.isMultiDeviceActive) {
            reflectChanges(handle)
        }
        contactModelRepository.persistAvailabilityStatuses(availabilityStatuses)
    }

    suspend fun reflectChanges(handle: ActiveTaskCodec) {
        handle.createTransaction(
            keys = mdProperties.keys,
            scope = Scope.CONTACT_SYNC,
            ttl = TRANSACTION_TTL_MAX,
        ).execute {
            coroutineScope {
                val encryptedEnvelopeResults = availabilityStatuses
                    .map { (identity, availabilityStatus) ->
                        val contact = contact {
                            this.identity = identity
                            this.workAvailabilityStatus = availabilityStatus.toProtocolModel()
                        }
                        getEncryptedContactSyncUpdate(
                            contact = contact,
                            multiDeviceProperties = mdProperties,
                        )
                    }
                val reflectMessages = encryptedEnvelopeResults.map { encryptedEnvelopeResult ->
                    async {
                        handle.reflectAndAwaitAck(
                            encryptedEnvelopeResult = encryptedEnvelopeResult,
                            storeD2dNonce = true,
                            nonceFactory = nonceFactory,
                        )
                    }
                }
                reflectMessages.awaitAll()
            }
        }
    }

    override fun serialize(): SerializableTaskData = BatchUpdateContactAvailabilityStatusTaskData(
        availabilityStatusesTaskData = availabilityStatuses
            .map { (identity, availabilityStatus) -> availabilityStatus.toTaskDataModel(identity) }
            .toSet(),
    )

    @Serializable
    private data class BatchUpdateContactAvailabilityStatusTaskData(
        private val availabilityStatusesTaskData: Set<AvailabilityStatusTaskData>,
    ) : SerializableTaskData {

        override fun createTask(): Task<*, TaskCodec> {
            val availabilityStatuses = deserializeAvailabilityStatuses(availabilityStatusesTaskData)
            return BatchUpdateContactAvailabilityStatusTask(availabilityStatuses)
        }

        private fun deserializeAvailabilityStatuses(
            availabilityStatusesTaskData: Set<AvailabilityStatusTaskData>,
        ): Map<IdentityString, AvailabilityStatus> =
            availabilityStatusesTaskData.mapNotNull { availabilityStatusTaskData ->
                val availabilityStatuses = AvailabilityStatus.fromTaskDataModel(availabilityStatusTaskData)
                if (availabilityStatuses != null) {
                    availabilityStatusTaskData.identity to availabilityStatuses
                } else {
                    logger.logAndReportError(
                        "Failed to deserialize availability status task data value of category {}",
                        availabilityStatusTaskData.category,
                    )
                    null
                }
            }.toMap()
    }

    companion object {

        private const val BATCH_SIZE_DEFAULT = 100

        /**
         *  Divides the given [availabilityStatuses] into multiple tasks, depending on [batchSize].
         *
         *  This is a tradeoff between keeping the amount of total transactions low and keeping the runtime of such a task low.
         */
        fun createBatchedTasks(
            availabilityStatuses: Map<IdentityString, AvailabilityStatus>,
            batchSize: Int = BATCH_SIZE_DEFAULT,
        ): List<BatchUpdateContactAvailabilityStatusTask> =
            availabilityStatuses
                .entries
                .chunked(batchSize)
                .map { chunk -> chunk.associate { it.key to it.value } }
                .map(::BatchUpdateContactAvailabilityStatusTask)
    }
}
