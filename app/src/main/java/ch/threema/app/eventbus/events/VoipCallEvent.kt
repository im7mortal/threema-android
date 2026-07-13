package ch.threema.app.eventbus.events

import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class VoipCallEvent {
    data class Ringing(val peerIdentity: Identity) : VoipCallEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(peerIdentity: IdentityString) =
                Ringing(Identity(peerIdentity))
        }
    }

    /**
     * A call was successfully started (meaning that it was accepted and that the connection has
     * been established successfully).
     *
     * @param peerIdentity The identity of the peer.
     * @param outgoing     Whether this is an outgoing call (initiated by us).
     */
    data class Started(val peerIdentity: Identity, val outgoing: Boolean) : VoipCallEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(peerIdentity: IdentityString, outgoing: Boolean) =
                Started(Identity(peerIdentity), outgoing)
        }
    }

    /**
     * A call was finished.
     *
     * @param callId       The call id of the finished call (might be 0).
     * @param peerIdentity The identity of the peer.
     * @param outgoing     Whether this is an outgoing call (initiated by us).
     * @param duration     The duration of the call.
     */
    data class Finished(val callId: Long, val peerIdentity: Identity, val outgoing: Boolean, val duration: Duration) : VoipCallEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(callId: Long, peerIdentity: IdentityString, outgoing: Boolean, durationInSeconds: Int) =
                Finished(callId, Identity(peerIdentity), outgoing, durationInSeconds.seconds)
        }
    }

    /**
     * A call was rejected.
     *
     * @param callId       The call id of the rejected call (might be 0).
     * @param peerIdentity The identity of the peer.
     * @param outgoing     Whether the rejected call was an outgoing call (initiated by us).
     * @param reason       The reject reason. The meaning can be determined using the
     * [ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData.RejectReason] class.
     */
    data class Rejected(val callId: Long, val peerIdentity: Identity, val outgoing: Boolean, val reason: Byte?) : VoipCallEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(callId: Long, peerIdentity: IdentityString, outgoing: Boolean, reason: Byte?) =
                Rejected(callId, Identity(peerIdentity), outgoing, reason)
        }
    }

    /**
     * An incoming call was missed or failed to be established.
     *
     * @param callId       The call id of the missed call (might be 0).
     * @param peerIdentity The identity of the peer.
     * @param accepted     Whether the call was accepted by the user or not.
     * @param createdAt    The created-at time of the hangup message, or `null` if the current date should be used
     */
    data class Missed(val callId: Long, val peerIdentity: Identity, val accepted: Boolean, val createdAt: Instant?) : VoipCallEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(callId: Long, peerIdentity: IdentityString, accepted: Boolean, createdAt: Instant?) =
                Missed(callId, Identity(peerIdentity), accepted, createdAt)
        }
    }

    /**
     * An outgoing call was aborted or failed to be established.
     *
     * @param callId       The call id of the aborted call (might be 0).
     * @param peerIdentity The identity of the peer.
     */
    data class Aborted(val callId: Long, val peerIdentity: Identity) : VoipCallEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(callId: Long, peerIdentity: IdentityString) =
                Aborted(callId, Identity(peerIdentity))
        }
    }
}
