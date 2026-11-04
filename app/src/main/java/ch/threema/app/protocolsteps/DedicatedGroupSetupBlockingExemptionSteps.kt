package ch.threema.app.protocolsteps

import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage

class DedicatedGroupSetupBlockingExemptionSteps(
    val groupModelRepository: GroupModelRepository,
    val identityBlockedSteps: IdentityBlockedSteps,
) {

    enum class Result {
        /**
         * The group-setup message successfully passes these steps.
         */
        PASS,

        /**
         * The group-setup message should be blocked because it's creator is implicitly or explicitly blocked.
         */
        BLOCK,
    }

    fun run(groupSetupMessage: GroupSetupMessage): Result {
        val groupIdentity = GroupIdentity(
            creatorIdentity = groupSetupMessage.groupCreator,
            groupId = groupSetupMessage.apiGroupId.toLong(),
        )
        // 1. Look up the group.
        val groupExists = groupModelRepository.exists(groupIdentity)
        if (groupExists) {
            // 2. If the group could be found, return that the message passed the blocking check.
            return Result.PASS
        }
        // 3. Run the _Identity Blocked Steps_ for the creator. If the result indicates that the creator is not blocked, return that the message
        // passed the blocking check. Otherwise, return that the message needs to be discarded.
        val creatorIdentityBlockState = identityBlockedSteps.run(
            identity = groupSetupMessage.groupCreator,
        )
        return when (creatorIdentityBlockState) {
            BlockState.EXPLICITLY_BLOCKED, BlockState.IMPLICITLY_BLOCKED -> Result.BLOCK
            BlockState.NOT_BLOCKED -> Result.PASS
        }
    }
}
