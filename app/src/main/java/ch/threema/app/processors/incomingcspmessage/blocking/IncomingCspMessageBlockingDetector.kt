package ch.threema.app.processors.incomingcspmessage.blocking

import ch.threema.app.protocolsteps.BlockState
import ch.threema.app.protocolsteps.DedicatedGroupSetupBlockingExemptionSteps
import ch.threema.app.protocolsteps.IdentityBlockedSteps
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.ExemptFromBlocking
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.logging.logAndReportError

private val logger = getThreemaLogger("IncomingCspMessageBlockingDetector")

class IncomingCspMessageBlockingDetector(
    private val identityBlockedSteps: IdentityBlockedSteps,
    private val dedicatedGroupSetupBlockingExemptionSteps: DedicatedGroupSetupBlockingExemptionSteps,
) {

    fun shouldBlockMessage(message: AbstractMessage): Boolean =
        when (message.exemptFromBlocking()) {
            ExemptFromBlocking.DEDICATED_STEPS -> {
                // If `inner-type` has dedicated blocking exemption steps, run these with `sender-identity` and `inner-message`. If the
                // result indicates that the message should be discarded, _Acknowledge_ the message and abort these steps.
                shouldBlockMessageByDedicatedBlockedSteps(message)
            }
            ExemptFromBlocking.NO -> {
                // If `inner-type` does not have dedicated blocking exemption steps and is not exempted from blocking, run the
                // _Identity Blocked Steps_ for `sender-identity`. If the result indicates that `sender-identity` is blocked, _Acknowledge_
                // and discard the message and abort these steps.
                shouldBlockMessageByDefaultBlockedSteps(message)
            }
            ExemptFromBlocking.YES -> false
        }

    /**
     * Runs the [IdentityBlockedSteps] for the sender identity of the message.
     *
     * @return `true` if [message] should be blocked
     */
    private fun shouldBlockMessageByDefaultBlockedSteps(message: AbstractMessage): Boolean =
        when (identityBlockedSteps.run(message.fromIdentity)) {
            BlockState.EXPLICITLY_BLOCKED, BlockState.IMPLICITLY_BLOCKED -> true
            BlockState.NOT_BLOCKED -> false
        }

    /**
     * Runs dedicated blocking exemption steps for message types that have [ExemptFromBlocking.DEDICATED_STEPS] defined.
     *
     * @return `true` if [message] should be blocked
     */
    private fun shouldBlockMessageByDedicatedBlockedSteps(message: AbstractMessage): Boolean =
        when (message) {
            is GroupSetupMessage ->
                when (dedicatedGroupSetupBlockingExemptionSteps.run(message)) {
                    DedicatedGroupSetupBlockingExemptionSteps.Result.PASS -> false
                    DedicatedGroupSetupBlockingExemptionSteps.Result.BLOCK -> true
                }

            else -> {
                logger.logAndReportError("Missing dedicated-blocking-exemption-steps for message type {}", message.type)
                true
            }
        }
}
