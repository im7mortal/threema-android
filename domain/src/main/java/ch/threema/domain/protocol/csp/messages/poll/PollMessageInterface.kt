package ch.threema.domain.protocol.csp.messages.poll

import ch.threema.domain.types.IdentityString

interface PollMessageInterface {
    var pollId: PollId?

    var pollCreatorIdentity: IdentityString?
}
