package ch.threema.app.protocolsteps

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.minus
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.VerificationLevel
import ch.threema.storage.factories.GroupMessageModelFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("GroupSpamPotentialDetectionSteps")

/**
 *  Implements the _Group Spam Potential Detection Steps_.
 */
class GroupSpamPotentialDetectionSteps(
    private val contactModelRepository: ContactModelRepository,
    private val groupMessageModelFactory: GroupMessageModelFactory,
    private val dispatcherProvider: DispatcherProvider,
) {

    /**
     * @return `true` if the given [group] is considered a potential spam group.
     */
    suspend fun run(group: GroupModel): Boolean {
        logger.info("Running Group Spam Potential Detection Steps")

        val groupData = group.data
            ?: run {
                logger.error("Missing group data for group {}", group.groupIdentity.groupId)
                return false
            }

        // If the spam reporting state of the associated conversation is either NOT_SPAM or REPORTED, return that no potential spam was detected.
        if (
            groupData.spamReportingState == ConversationSpamReportingState.NOT_SPAM ||
            groupData.spamReportingState == ConversationSpamReportingState.REPORTED
        ) {
            return false
        }

        // If the user is the creator or the group is marked as left, return that no spam potential was detected.
        if (group.isCreator() || !groupData.isMember) {
            return false
        }

        return withContext(dispatcherProvider.io) {
            val creator = contactModelRepository
                .getByIdentity(group.groupIdentity.creatorIdentity)
                ?.data
            if (creator == null) {
                logger.error("Group creator was not found (identity = {})", group.groupIdentity.creatorIdentity)
                return@withContext false
            }

            val groupDatabaseId =
                try {
                    group.getDatabaseId()
                } catch (e: Exception) {
                    logger.error("Failed to read group database id of group {}", group.groupIdentity, e)
                    return@withContext false
                }

            // If creator's verification level is not UNVERIFIED, return that no spam potential was detected.
            if (creator.verificationLevel != VerificationLevel.UNVERIFIED) {
                return@withContext false
            }

            // If the conversation contains at least one outgoing content message, return that no spam potential was detected.
            val existsOutgoingContentMessage = groupMessageModelFactory.existsOutgoingContentMessage(groupDatabaseId)
            if (existsOutgoingContentMessage) {
                return@withContext false
            }

            // If no incoming _content_ message exists, return that no spam potential was detected.
            val earliestIncomingContentMessage = groupMessageModelFactory.getEarliestIncomingContentMessage(groupDatabaseId)
                ?: return@withContext false

            val earliestMessageReceivedAt = earliestIncomingContentMessage.createdAt
                ?: run {
                    logger.error("Missing createdAt timestamp for message {}", earliestIncomingContentMessage.uid)
                    return@withContext false
                }

            // If the timespan between earliest-message's received-at date and the creator's creation date
            // is greater than 7 days, return that no spam potential was detected.
            val diff: Duration = earliestMessageReceivedAt.minus(creator.createdAt)
            if (diff > 7.days) {
                return@withContext false
            }

            // Return that potential spam was detected.
            true
        }
    }
}
