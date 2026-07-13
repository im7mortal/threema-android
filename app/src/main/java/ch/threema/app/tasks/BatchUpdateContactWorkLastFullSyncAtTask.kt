package ch.threema.app.tasks

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedContactSyncUpdate
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.d2d.TransactionScope.Scope
import ch.threema.protobuf.d2d.sync.contact
import java.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("BatchUpdateContactWorkLastFullSyncAtTask")

/**
 *  Handling a batch-update of `contact.workLastFullSyncAt` timestamps.
 *
 *  If multi-device is active at the time this tasks executes, all *valid* changes of [workLastFullSyncAtTimestamps] are reflected in a *single*
 *  transaction. No changes will be persisted locally in case the transaction did not commit successfully.
 *
 *  *Valid* changes are persisted locally using a single database call from [ContactModelRepository.persistWorkLastFullSyncAtTimestampUpdates].
 */
class BatchUpdateContactWorkLastFullSyncAtTask private constructor(
    private val workLastFullSyncAtTimestamps: Map<IdentityString, Instant>,
) : ActiveTask<Unit>, PersistableTask, KoinComponent {

    private val multiDeviceManager: MultiDeviceManager by inject()
    private val mdProperties by lazy { multiDeviceManager.propertiesProvider.get() }

    private val nonceFactory: NonceFactory by inject()
    private val contactModelRepository: ContactModelRepository by inject()

    override val type: String = "BatchUpdateContactWorkLastFullSyncAtTask"

    override suspend fun invoke(handle: ActiveTaskCodec) {
        // By the time this task runs, the local values may have changed in the meantime. We therefore have to validate every requested change before
        // reflecting and persisting it.
        val validChanges: Map<IdentityString, Instant> = filterValidChanges()
        if (validChanges.isEmpty()) {
            logger.info(
                "Skipping task because it contained no valid changes by the time of execution ({} change(s) requested)",
                workLastFullSyncAtTimestamps.size,
            )
            return
        }
        if (multiDeviceManager.isMultiDeviceActive) {
            reflectChanges(handle, validChanges)
        }
        contactModelRepository.persistWorkLastFullSyncAtTimestampUpdates(
            workLastFullSyncAtTimestamps = validChanges,
        )
    }

    /**
     *  A change is considered valid, if the contact exists and the currently stored timestamp value of `workLastFullSyncAt` is either `null`, or
     *  smaller than the new value.
     *
     *  @return Map containing only valid changes to `workLastFullSyncAt` values *at the time of execution*
     */
    private fun filterValidChanges(): Map<IdentityString, Instant> {
        if (workLastFullSyncAtTimestamps.isEmpty()) {
            return emptyMap()
        }
        val contactModels = contactModelRepository
            .getByIdentities(workLastFullSyncAtTimestamps.keys)
            .associateBy(ContactModel::identity)
        return workLastFullSyncAtTimestamps
            .filter { (identity, updatedWorkLastFullSyncAt) ->
                val contactModelData = contactModels[identity]?.data
                    ?: return@filter false
                contactModelData.workLastFullSyncAt == null || contactModelData.workLastFullSyncAt < updatedWorkLastFullSyncAt
            }
    }

    private suspend fun reflectChanges(handle: ActiveTaskCodec, changes: Map<IdentityString, Instant>) {
        handle.createTransaction(
            keys = mdProperties.keys,
            scope = Scope.CONTACT_SYNC,
            ttl = TRANSACTION_TTL_MAX,
        ).execute {
            coroutineScope {
                val encryptedEnvelopeResults = changes
                    .map { (identity, workLastFullSyncAt) ->
                        val contact = contact {
                            this.identity = identity
                            this.workLastFullSyncAt = workLastFullSyncAt.toEpochMilli()
                        }
                        getEncryptedContactSyncUpdate(
                            contact = contact,
                            multiDeviceProperties = mdProperties,
                        )
                    }
                val reflectMessages: List<Deferred<ULong>> = encryptedEnvelopeResults
                    .map { encryptedEnvelopeResult ->
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

    override fun serialize(): SerializableTaskData =
        BatchUpdateContactWorkLastFullSyncAtUpdateTaskData(
            workLastFullSyncAtTimestamps = workLastFullSyncAtTimestamps.mapValues { (_, workLastFullSyncAt) ->
                workLastFullSyncAt.toKotlinInstant()
            },
        )

    /**
     *  @param workLastFullSyncAtTimestamps Using type [kotlin.time.Instant] for values, to support serialization
     */
    @Serializable
    private data class BatchUpdateContactWorkLastFullSyncAtUpdateTaskData(
        private val workLastFullSyncAtTimestamps: Map<IdentityString, kotlin.time.Instant>,
    ) : SerializableTaskData {
        override fun createTask(): Task<*, TaskCodec> =
            BatchUpdateContactWorkLastFullSyncAtTask(
                workLastFullSyncAtTimestamps = workLastFullSyncAtTimestamps
                    .mapValues { (_, workLastFullSyncAt) ->
                        workLastFullSyncAt.toJavaInstant()
                    },
            )
    }

    companion object {

        private const val BATCH_SIZE_DEFAULT = 100

        /**
         *  Divides the given [workLastFullSyncAtTimestamps] into multiple tasks, depending on [batchSize].
         *
         *  This is a tradeoff between keeping the amount of total transactions low and keeping the runtime of such a task low.
         */
        fun createBatchedTasks(
            workLastFullSyncAtTimestamps: Map<IdentityString, Instant>,
            batchSize: Int = BATCH_SIZE_DEFAULT,
        ): List<BatchUpdateContactWorkLastFullSyncAtTask> =
            workLastFullSyncAtTimestamps
                .entries
                .chunked(batchSize)
                .map { chunk -> chunk.associate { it.key to it.value } }
                .map(::BatchUpdateContactWorkLastFullSyncAtTask)
    }
}
