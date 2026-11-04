package ch.threema.app.apptaskexecutor.tasks

import ch.threema.app.di.awaitAppFullyReady
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.stores.IdentityStore
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("ReportGroupToDirectoryServerTask")

/**
 * Report the given group to the directory server.
 *
 * Pass all used group- and creator-values directly into this task, as the group might have already been deleted when this task runs.
 */
class ReportGroupToDirectoryServerTask(
    private val groupIdentity: GroupIdentity,
    private val groupId: GroupId,
    private val groupCreatorNickname: String?,
    private val groupMemberCount: Int,
) : PersistableAppTask, KoinComponent {

    private val identityStore: IdentityStore by inject()
    private val apiConnector: APIConnector by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private val backoffIterator = listOf(
        30.seconds,
        1.minutes,
        2.minutes,
        4.minutes,
        8.minutes,
        16.minutes,
        32.minutes,
    ).iterator()

    override suspend fun run() {
        var attempt = 0
        while (true) {
            awaitAppFullyReady()
            val userIdentity = identityStore.getIdentity()
                ?: run {
                    logger.error("Missing user identity")
                    return
                }
            if (userIdentity.value == groupIdentity.creatorIdentity) {
                logger.error("Cannot report a group created by the user")
                return
            }
            try {
                attempt++
                reportToDirectory()
                logger.info("Reported group {} to directory server", groupIdentity)
                return
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                logger.warn("Failed to report group {} to directory server (attempt {})", groupIdentity, attempt, exception)
            }
            if (backoffIterator.hasNext()) {
                delay(backoffIterator.next())
            } else {
                logger.error("Giving up on reporting group {} to directory after {} tries.", groupIdentity, attempt)
                return
            }
        }
    }

    private suspend fun reportToDirectory() {
        withContext(dispatcherProvider.io) {
            apiConnector.reportSpamGroup(
                /* identityStore = */
                identityStore,
                /* groupId = */
                groupId,
                /* groupCreatorIdentity = */
                groupIdentity.creatorIdentity,
                /* groupCreatorNickname = */
                groupCreatorNickname,
                /* groupMemberCount = */
                groupMemberCount,
            )
        }
    }

    override fun serialize(): AppTaskData =
        ReportGroupToDirectoryServerTaskData(
            groupIdentity = groupIdentity,
            groupIdHexString = groupId.toString(),
            groupCreatorNickname = groupCreatorNickname,
            groupMemberCount = groupMemberCount,
        )

    @Serializable
    private data class ReportGroupToDirectoryServerTaskData(
        private val groupIdentity: GroupIdentity,
        private val groupIdHexString: String,
        private val groupCreatorNickname: String?,
        private val groupMemberCount: Int,
    ) : AppTaskData {

        override fun createTask() =
            ReportGroupToDirectoryServerTask(
                groupIdentity = groupIdentity,
                groupId = GroupId(groupIdHexString),
                groupCreatorNickname = groupCreatorNickname,
                groupMemberCount = groupMemberCount,
            )
    }
}
