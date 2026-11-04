package ch.threema.app.tasks

import ch.threema.app.groupflows.ReportGroupIntent
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.multidevice.MultiDeviceKeys
import ch.threema.domain.protocol.multidevice.MultiDeviceProperties
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.ActiveTask
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TRANSACTION_TTL_MAX
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.taskmanager.createTransaction
import ch.threema.domain.taskmanager.getEncryptedGroupSyncDelete
import ch.threema.domain.taskmanager.getEncryptedSettingsSyncUpdate
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.common.identities
import ch.threema.protobuf.d2d.TransactionScope.Scope
import ch.threema.protobuf.d2d.sync.settings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReflectReportGroupTask")

class ReflectReportGroupTask(
    private val groupIdentity: GroupIdentity,
    private val reportGroupIntent: ReportGroupIntent,
) : ActiveTask<ReflectionResult<Unit>>, PersistableTask, KoinComponent {

    private val identityStore: IdentityStore by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val multiDeviceManager: MultiDeviceManager by inject()
    private val nonceFactory: NonceFactory by inject()
    private val blockedIdentitiesService: BlockedIdentitiesService by inject()

    override val type: String = "ReflectReportGroupTask"

    override val shortLogInfo: String = "group=$groupIdentity,intent=$reportGroupIntent"

    override suspend fun invoke(handle: ActiveTaskCodec): ReflectionResult<Unit> {
        val userIdentity = identityStore.getIdentity()
            ?: run {
                logger.error("Missing user identity - cancelling any substeps")
                return ReflectionResult.Failed(
                    exception = IllegalStateException("Missing user identity"),
                )
            }
        if (userIdentity.value == groupIdentity.creatorIdentity) {
            logger.error("Cannot report a group created by the user - cancelling any substeps")
            return ReflectionResult.Failed(
                exception = IllegalArgumentException("Cannot report a group created by the user"),
            )
        }
        val groupModel = groupModelRepository.getByGroupIdentity(groupIdentity)
            ?: run {
                logger.warn("Group model race: group does not exist ({}) - cancelling any substeps", groupIdentity)
                return ReflectionResult.Failed(
                    exception = IllegalStateException("Group does not exist"),
                )
            }
        if (!multiDeviceManager.isMultiDeviceActive) {
            logger.warn("Cannot reflect a group report with intent {} when MD is not active", reportGroupIntent)
            return ReflectionResult.MultiDeviceNotActive()
        }
        try {
            reflect(
                handle = handle,
                reportGroupIntent = reportGroupIntent,
                groupModel = groupModel,
            )
        } catch (e: TransactionScope.TransactionException) {
            logger.error("Could not reflect as the precondition failed", e)
            return ReflectionResult.PreconditionFailed(e)
        }
        return ReflectionResult.Success(Unit)
    }

    /**
     * **Steps for [ReportGroupIntent.REPORT_GROUP]:**
     * - TODO(PRD-1007): Set the group spam reporting state to ´REPORTED´
     *
     * **Steps for [ReportGroupIntent.REPORT_GROUP_BLOCK_CREATOR_DELETE_GROUP]:**
     * - Block the group creator
     * - Exclude the group creator from contact sync
     * - Mark group as deleted
     *
     * @throws TransactionScope.TransactionException if the precondition failed
     */
    private suspend fun reflect(
        handle: ActiveTaskCodec,
        reportGroupIntent: ReportGroupIntent,
        groupModel: GroupModel,
    ) {
        when (reportGroupIntent) {
            ReportGroupIntent.REPORT_GROUP -> {
                // TODO(PRD-1007): reflect the updated spam reporting state of the group (in a transaction)
            }
            ReportGroupIntent.REPORT_GROUP_BLOCK_CREATOR_DELETE_GROUP -> {
                val multiDeviceProperties = multiDeviceManager.propertiesProvider.get()
                handle.createTransaction(
                    keys = multiDeviceProperties.keys,
                    scope = Scope.GROUP_SYNC,
                    ttl = TRANSACTION_TTL_MAX,
                    precondition = {
                        if (groupModel.isDeleted) {
                            logger.warn("Group model race: group does not exist ({})", groupIdentity)
                            false
                        } else if (!groupModel.isMember()) {
                            logger.warn("Group model race: user is not a member anymore ({})", groupIdentity)
                            false
                        } else {
                            true
                        }
                    },
                ).execute {
                    reflectDeleteGroup(
                        handle = handle,
                        multiDeviceProperties = multiDeviceProperties,
                    )
                    reflectCreatorBlockedState(
                        handle = handle,
                        multiDeviceProperties = multiDeviceProperties,
                        updatedBlockedIdentities = blockedIdentitiesService.getAllBlockedIdentities() + groupIdentity.creatorIdentity,
                    )
                }
            }
        }
    }

    /**
     * Reflect a `SettingsSync.Update` containing updated `Settings.blockedIdentities` values.
     *
     * @param updatedBlockedIdentities The full, updated list of blocked identities.
     */
    private suspend fun reflectCreatorBlockedState(
        handle: ActiveTaskCodec,
        multiDeviceProperties: MultiDeviceProperties,
        updatedBlockedIdentities: Set<IdentityString>,
    ) {
        val settings = settings {
            blockedIdentities = identities {
                identities += updatedBlockedIdentities
            }
        }
        handle.reflectAndAwaitAck(
            encryptedEnvelopeResult = getEncryptedSettingsSyncUpdate(
                settings = settings,
                multiDeviceProperties = multiDeviceProperties,
            ),
        )
    }

    /**
     * Reflect a `GroupSync.Delete` message for this task's [groupIdentity].
     */
    private suspend fun reflectDeleteGroup(
        handle: ActiveTaskCodec,
        multiDeviceProperties: MultiDeviceProperties,
    ) {
        val encryptedEnvelopeResult = getEncryptedGroupSyncDelete(
            groupIdentity = groupIdentity.getProtoGroupIdentity(),
            multiDeviceProperties = multiDeviceProperties,
        )
        handle.reflectAndAwaitAck(encryptedEnvelopeResult)
    }

    override fun serialize(): SerializableTaskData =
        ReportGroupTaskData(
            groupIdentity = groupIdentity,
            reportGroupIntentSerialized = reportGroupIntent.serializedValue,
        )

    @Serializable
    private data class ReportGroupTaskData(
        @SerialName("groupIdentity")
        private val groupIdentity: GroupIdentity,
        @SerialName("reportGroupIntent")
        private val reportGroupIntentSerialized: Int,
    ) : SerializableTaskData {

        override fun createTask(): Task<*, TaskCodec> =
            ReflectReportGroupTask(
                groupIdentity = groupIdentity,
                reportGroupIntent = ReportGroupIntent.fromSerializedValue(reportGroupIntentSerialized)!!,
            )
    }

    private suspend fun ActiveTaskCodec.reflectAndAwaitAck(
        encryptedEnvelopeResult: MultiDeviceKeys.EncryptedEnvelopeResult,
    ) {
        reflectAndAwaitAck(
            encryptedEnvelopeResult = encryptedEnvelopeResult,
            storeD2dNonce = true,
            nonceFactory = nonceFactory,
        )
    }
}
