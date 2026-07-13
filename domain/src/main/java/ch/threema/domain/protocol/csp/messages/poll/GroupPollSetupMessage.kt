package ch.threema.domain.protocol.csp.messages.poll

import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.AbstractGroupMessage
import ch.threema.domain.protocol.csp.messages.BadMessageException
import ch.threema.domain.types.IdentityString
import ch.threema.protobuf.csp.e2e.fs.Version
import ch.threema.protobuf.d2d.IncomingMessage
import ch.threema.protobuf.d2d.OutgoingMessage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private val logger = getThreemaLogger("GroupPollSetupMessage")

/**
 * A group poll creation message.
 */
class GroupPollSetupMessage : AbstractGroupMessage(), PollSetupInterface {
    override var pollId: PollId? = null
    override var pollCreatorIdentity: IdentityString? = null
    override var pollData: PollData? = null

    // this is only used for debugging
    @JvmField
    var rawPollData: String? = null

    override fun flagSendPush(): Boolean = true

    override fun getMinimumRequiredForwardSecurityVersion(): Version = Version.V1_2

    override fun allowUserProfileDistribution(): Boolean = true

    override fun exemptFromBlocking(): Boolean = true

    override fun createImplicitlyDirectContact(): Boolean = false

    override fun protectAgainstReplay(): Boolean = true

    override fun reflectIncoming(): Boolean = true

    override fun reflectOutgoing(): Boolean = true

    override fun reflectSentUpdate(): Boolean = true

    override fun sendAutomaticDeliveryReceipt(): Boolean = false

    override fun bumpLastUpdate(): Boolean = true

    override fun getBody(): ByteArray? {
        if (pollId == null || pollData == null) {
            return null
        }
        try {
            val bos = ByteArrayOutputStream()
            bos.write(groupCreator.toByteArray(StandardCharsets.US_ASCII))
            bos.write(apiGroupId.groupId)
            bos.write(pollId!!.pollId)
            pollData!!.write(bos)
            return bos.toByteArray()
        } catch (exception: Exception) {
            logger.error(exception.message)
            return null
        }
    }

    override fun getType(): Int = ProtocolDefines.MSGTYPE_GROUP_POLL_CREATE

    companion object {
        @JvmStatic
        fun fromReflected(
            message: IncomingMessage,
            fromIdentity: IdentityString,
        ): GroupPollSetupMessage = fromByteArray(
            data = message.body.toByteArray(),
            fromIdentity = fromIdentity,
        ).apply {
            initializeCommonProperties(message)
        }

        @JvmStatic
        fun fromReflected(
            message: OutgoingMessage,
            fromIdentity: IdentityString,
        ): GroupPollSetupMessage = fromByteArray(
            data = message.body.toByteArray(),
            fromIdentity = fromIdentity,
        ).apply {
            initializeCommonProperties(message)
        }

        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(data: ByteArray, fromIdentity: IdentityString): GroupPollSetupMessage =
            fromByteArray(
                data = data,
                offset = 0,
                length = data.size,
                fromIdentity = fromIdentity,
            )

        /**
         * Get the group poll-setup message from the given array.
         *
         * @param data   the data that represents the message
         * @param offset the offset where the data starts
         * @param length the length of the data (needed to ignore the padding)
         * @param fromIdentity the identity of the sender
         * @return the poll-setup message
         * @throws BadMessageException if the length is invalid
         */
        @JvmStatic
        @Throws(BadMessageException::class)
        fun fromByteArray(
            data: ByteArray,
            offset: Int,
            length: Int,
            fromIdentity: IdentityString,
        ): GroupPollSetupMessage {
            if (length < 1) {
                throw BadMessageException("Bad length ($length) for poll setup message")
            } else if (offset < 0) {
                throw BadMessageException("Bad offset ($offset) for poll setup message")
            } else if (data.size < length + offset) {
                throw BadMessageException("Invalid byte array length (${data.size}) for offset $offset and length $length")
            }

            return GroupPollSetupMessage().apply {
                pollCreatorIdentity = fromIdentity

                var positionIndex = offset
                groupCreator = String(
                    data,
                    positionIndex,
                    ProtocolDefines.IDENTITY_LEN,
                    StandardCharsets.US_ASCII,
                )
                positionIndex += ProtocolDefines.IDENTITY_LEN

                apiGroupId = GroupId(data, positionIndex)
                positionIndex += ProtocolDefines.GROUP_ID_LEN

                pollId = PollId(data, positionIndex)
                positionIndex += ProtocolDefines.POLL_ID_LEN

                val jsonObjectString = String(
                    data,
                    positionIndex,
                    length + offset - positionIndex,
                    StandardCharsets.UTF_8,
                )
                pollData = PollData.parse(jsonObjectString)

                // this is only used for debugging
                rawPollData = jsonObjectString
            }
        }
    }
}
