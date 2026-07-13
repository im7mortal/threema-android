package ch.threema.app.eventbus.events

import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.storage.models.poll.PollModel

sealed class PollEvent {
    abstract val poll: PollModel

    data class NewPoll(override val poll: PollModel) : PollEvent()

    data class PollUpdated(override val poll: PollModel) : PollEvent()

    /**
     * Emitted when the user votes in a poll, including when they change existing votes.
     */
    data class PollSelfVoted(override val poll: PollModel) : PollEvent()

    /**
     * Emitted when another user (i.e., not the current user) votes in a poll, including when they change existing votes.
     *
     * @param isNewVote true if this vote is a new vote in the poll for the given voter, as opposed to the changing of an existing vote
     */
    data class PollVoted(override val poll: PollModel, val voterIdentity: Identity, val isNewVote: Boolean) : PollEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(poll: PollModel, voterIdentity: IdentityString, isFirstVote: Boolean) =
                PollVoted(poll, Identity(voterIdentity), isFirstVote)
        }
    }

    data class PollVoteRemoved(override val poll: PollModel, val voterIdentity: Identity) : PollEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(poll: PollModel, voterIdentity: IdentityString) =
                PollVoteRemoved(poll, Identity(voterIdentity))
        }
    }

    data class PollClosed(override val poll: PollModel) : PollEvent()

    data class PollRemoved(override val poll: PollModel) : PollEvent()
}
