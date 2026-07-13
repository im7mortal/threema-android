package ch.threema.domain.protocol.csp.messages.poll

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.IncomingMessage
import ch.threema.protobuf.d2d.OutgoingMessage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private val logger = getThreemaLogger("PollSetupMessage")

/**
 * A poll creation message.
 */
open class PollSetupMessage : AbstractMessage(), PollSetupInterface {
    override var pollId: PollId? = null
    override var pollCreatorIdentity: IdentityString? = null
    override var pollData: PollData? = null

    override fun flagSendPush(): Boolean = true

    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_0

    override fun allowUserProfileDistribution(): Boolean = true

    override fun exemptFromBlocking(): Boolean = false

    override fun createImplicitlyDirectContact(): Boolean = true

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = true

    override fun reflectSentUpdate(): Boolean = true

    override fun sendAutomaticDeliveryReceipt(): Boolean = true

    override fun bumpLastUpdate(): Boolean = true

    override fun getBody(): ByteArray? {
        if (pollId == null || pollData == null) {
            return null
        }
        try {
            val bos = ByteArrayOutputStream()
            bos.write(pollId!!.pollId)
            pollData!!.write(bos)
            return bos.toByteArray()
        } catch (exception: Exception) {
            logger.error(exception.message)
            return null
        }
    }

    override fun getType(): Int = ProtocolDefines.MSGTYPE_POLL_CREATE

    override fun toString(): String =
        buildString {
            append(super.toString())
            append(": poll setup message")
            pollData?.description?.let { description ->
                append(", description: ")
                append(description)
            }
        }

    companion object {
        @JvmStatic
        fun fromReflected(
            message: IncomingMessage,
            pollCreatorIdentity: IdentityString,
        ): PollSetupMessage = fromByteArray(
            data = message.body.toByteArray(),
            pollCreatorIdentity = pollCreatorIdentity,
        ).apply {
            initializeCommonProperties(message)
        }

        @JvmStatic
        fun fromReflected(
            message: OutgoingMessage,
            pollCreatorIdentity: IdentityString,
        ): PollSetupMessage = fromByteArray(
            data = message.body.toByteArray(),
            pollCreatorIdentity = pollCreatorIdentity,
        ).apply {
            initializeCommonProperties(message)
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, pollCreatorIdentity: IdentityString): PollSetupMessage =
            fromByteArray(
                data = data,
                offset = 0,
                length = data.size,
                pollCreatorIdentity = pollCreatorIdentity,
            )

        /**
         * Get the poll-setup message from the given array.
         *
         * @param data   the data that represents the message
         * @param offset the offset where the data starts
         * @param length the length of the data (needed to ignore the padding)
         * @param pollCreatorIdentity the identity of the sender
         * @return the poll-setup message
         * @throws BadMessageException if the length is invalid
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(
            data: ByteArray,
            offset: Int,
            length: Int,
            pollCreatorIdentity: IdentityString,
        ): PollSetupMessage {
            if (length < 1) {
                throw BadMessageException("Bad length ($length) for poll setup message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for poll setup message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }

            return PollSetupMessage().apply {
                this.pollCreatorIdentity = pollCreatorIdentity

                var positionIndex = offset
                pollId = PollId(data, positionIndex)

                positionIndex += ProtocolDefines.POLL_ID_LEN

                val jsonObjectString = String(
                    data,
                    positionIndex,
                    length + offset - positionIndex,
                    StandardCharsets.UTF_8,
                )
                pollData = PollData.parse(jsonObjectString)
            }
        }
    }
}
