package ch.threema.app.groupflows

import android.content.Context
import ch.threema.app.apptaskexecutor.AppTaskExecutor
import ch.threema.app.apptaskexecutor.tasks.ReportGroupToDirectoryServerTask
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.GroupService
import ch.threema.app.tasks.OutgoingGroupLeaveTask
import ch.threema.app.tasks.ReflectReportGroupTask
import ch.threema.app.tasks.ReflectionResult
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.app.voip.services.VoipCallService
import ch.threema.app.voip.util.VoipUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.MessageId
import ch.threema.domain.protocol.connection.ConnectionState
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.IdentityString
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("ReportGroupFlow")

class ReportGroupFlow(
    private val groupModel: GroupModel,
    private val reportGroupIntent: ReportGroupIntent,
    private val groupModelRepository: GroupModelRepository,
    private val contactModelRepository: ContactModelRepository,
    private val taskManager: TaskManager,
    private val appTaskExecutor: AppTaskExecutor,
    private val multiDeviceManager: MultiDeviceManager,
    private val connection: ServerConnection,
    private val groupCallManager: GroupCallManager,
    private val groupService: GroupService,
    private val blockedIdentitiesService: BlockedIdentitiesService,
    private val identityProvider: IdentityProvider,
) : BackgroundTask<GroupFlowResult>, KoinComponent {

    private val appContext: Context? by injectNullableNonBinding<Context>()

    private val userIdentity by lazy { identityProvider.requireIdentity() }

    override fun runInBackground(): GroupFlowResult {
        logger.info("Running report group flow with intent {}", reportGroupIntent)

        val groupModelData = groupModel.data
            ?: run {
                logger.error("Group model data is null")
                return GroupFlowResult.Failure.Other
            }

        val creatorContactModel = contactModelRepository.getByIdentity(groupModel.groupIdentity.creatorIdentity)
        val creatorContactModelData = creatorContactModel?.data
            ?: run {
                logger.error("Missing group creator")
                return GroupFlowResult.Failure.Other
            }

        if (groupModelData.groupIdentity.creatorIdentity == userIdentity.value) {
            logger.error("The user is the creator")
            return GroupFlowResult.Failure.Other
        }

        // Fire task and forget
        @Suppress("DeferredResultUnused")
        runBlocking {
            appTaskExecutor.persistAndScheduleTask(
                ReportGroupToDirectoryServerTask(
                    groupIdentity = groupModel.groupIdentity,
                    groupId = GroupId(groupModel.groupIdentity.groupId),
                    groupCreatorNickname = creatorContactModelData.nickname,
                    groupMemberCount = groupModelData.countAllMembers(userIdentity.value),
                ),
            )
        }

        return if (multiDeviceManager.isMultiDeviceActive) {
            if (connection.connectionState != ConnectionState.LOGGED_IN) {
                return GroupFlowResult.Failure.Network
            }
            runBlocking {
                val reflectionResult: ReflectionResult<Unit> = taskManager
                    .schedule(
                        ReflectReportGroupTask(
                            groupIdentity = groupModelData.groupIdentity,
                            reportGroupIntent = reportGroupIntent,
                        ),
                    ).await()
                when (reflectionResult) {
                    is ReflectionResult.Success -> {
                        persistChanges(
                            groupModelData = null,
                        )
                    }
                    is ReflectionResult.Failed -> {
                        logger.warn("Reflection failed due to an undefined error")
                        GroupFlowResult.Failure.Other
                    }
                    is ReflectionResult.MultiDeviceNotActive -> {
                        // Note that this is a very rare edge case that should not be possible at all. If it happens, we need to return a failure as
                        // the changes are not persisted and not sent to the members.
                        logger.warn("Reflection failed because multi device is not active")
                        GroupFlowResult.Failure.Other
                    }
                    is ReflectionResult.PreconditionFailed -> {
                        logger.warn("Precondition for reporting group failed", reflectionResult.transactionException)
                        GroupFlowResult.Failure.Other
                    }
                }
            }
        } else {
            persistChanges(groupModelData)
        }
    }

    /**
     * @param groupModelData If `null` is passed, the model data will be re-read from the [groupModel]. This can prevent a stale model data object.
     */
    private fun persistChanges(groupModelData: GroupModelData?): GroupFlowResult {
        when (reportGroupIntent) {
            ReportGroupIntent.REPORT_GROUP -> {
                groupModel.persistSpamReportingState(ConversationSpamReportingState.REPORTED)
            }
            ReportGroupIntent.REPORT_GROUP_BLOCK_CREATOR_DELETE_GROUP -> {
                val groupModelData = groupModelData
                    ?: groupModel.data
                    ?: run {
                        logger.error("Group model data is null")
                        return GroupFlowResult.Failure.Other
                    }
                hangUpAnyAssociatedCall(groupModel)
                groupService.removeGroupBelongings(groupModel)
                groupModelRepository.persistRemovedGroup(groupModelData.groupIdentity)
                blockedIdentitiesService.blockIdentityWithoutReflection(groupModel.groupIdentity.creatorIdentity)
                sendLeaveGroupCspMessages(
                    memberIdentities = groupModelData.otherMembersAndCreator,
                )
            }
        }
        return GroupFlowResult.Success(groupModel)
    }

    /**
     * Hang up a group call from the given [groupModel] or a 1:1 call with the group creator.
     *
     * No-op if there is no ongoing call.
     */
    private fun hangUpAnyAssociatedCall(groupModel: GroupModel) {
        // Hang up an ongoing 1:1 call with the group creator (if there is one)
        if (VoipCallService.isRunning() && groupModel.groupIdentity.creatorIdentity == VoipCallService.getOtherPartysIdentity()) {
            val context = appContext
            if (context != null) {
                VoipUtil.sendOneToOneCallHangupCommand(context)
            } else {
                logger.warn("Could not hang up the ongoing VoIP call with the group creator")
            }
        }
        // Leave ongoing group call for this group (if there is one)
        groupCallManager.removeGroupCallParticipant(
            identity = userIdentity.value,
            groupModel = groupModel,
        )
    }

    /**
     * Schedule persistent task to send out CSP group-leave messages
     */
    private fun sendLeaveGroupCspMessages(memberIdentities: Set<IdentityString>) {
        val groupLeaveTask = OutgoingGroupLeaveTask(
            groupIdentity = groupModel.groupIdentity,
            messageId = MessageId.random(),
            memberIdentities = memberIdentities,
        )
        @Suppress("DeferredResultUnused")
        taskManager.schedule(groupLeaveTask)
    }
}

enum class ReportGroupIntent(val serializedValue: Int) {
    /**
     * Report the group.
     */
    REPORT_GROUP(
        serializedValue = 0,
    ),

    /**
     * This intent is a combination of actions:
     * - report the group
     * - block the group creator
     * - delete the group
     */
    REPORT_GROUP_BLOCK_CREATOR_DELETE_GROUP(
        serializedValue = 1,
    ),
    ;

    companion object {

        @JvmStatic
        fun fromSerializedValue(serializedValue: Int): ReportGroupIntent? =
            when (serializedValue) {
                0 -> REPORT_GROUP
                1 -> REPORT_GROUP_BLOCK_CREATOR_DELETE_GROUP
                else -> {
                    logger.error("Unrecognized serialized value of {} for ReportGroupIntent", serializedValue)
                    null
                }
            }
    }
}
