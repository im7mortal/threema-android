package ch.threema.data.models

import androidx.compose.runtime.Immutable
import ch.threema.data.storage.DbEmojiReaction
import ch.threema.domain.types.IdentityString
import java.time.Instant

@Immutable
data class EmojiReactionData(
    /** The id of the message this reaction refers to - see [ch.threema.storage.models.MessageModel.COLUMN_ID] */
    @JvmField val messageId: Int,
    /** The identity of the person who reacted. This may differ from the sender of the message **/
    @JvmField val senderIdentity: IdentityString,
    /** The emoji codepoint sequence of the reaction. This can never be empty */
    @JvmField val emojiSequence: String,
    /** Timestamp when the reaction was locally created. */
    @JvmField val reactedAt: Instant,
    /** Timestamp when the reaction was marked as read by the user. `null` if the reaction is still unread. */
    @JvmField val readAt: Instant? = null,
) {
    /**
     * Fields [reactedAt] and [readAt] are not considered.
     */
    override fun equals(other: Any?): Boolean {
        return other is EmojiReactionData &&
            this.messageId == other.messageId &&
            this.senderIdentity == other.senderIdentity &&
            this.emojiSequence == other.emojiSequence
    }

    /**
     * Fields [reactedAt] and [readAt] are not considered.
     */
    override fun hashCode(): Int {
        var result = messageId
        result = 31 * result + senderIdentity.hashCode()
        result = 31 * result + emojiSequence.hashCode()
        return result
    }
}

fun DbEmojiReaction.toDataType() = EmojiReactionData(
    messageId = this.messageId,
    senderIdentity = this.senderIdentity,
    emojiSequence = this.emojiSequence,
    reactedAt = this.reactedAt,
    readAt = this.readAt,
)
