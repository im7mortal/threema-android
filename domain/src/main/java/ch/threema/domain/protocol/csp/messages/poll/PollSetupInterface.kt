package ch.threema.domain.protocol.csp.messages.poll

interface PollSetupInterface : PollMessageInterface {
    var pollData: PollData?
}
