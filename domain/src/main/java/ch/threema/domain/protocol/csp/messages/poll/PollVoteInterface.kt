package ch.threema.domain.protocol.csp.messages.poll

interface PollVoteInterface : PollMessageInterface {
    val votes: List<PollVote>

    fun addVotes(votes: List<PollVote>)
}
