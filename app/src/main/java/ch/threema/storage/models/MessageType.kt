package ch.threema.storage.models

/**
 * Former types:
 * - IMAGE (1)
 * - VIDEO (2)
 * - VOICEMESSAGE (3)
 * - CONTACT (5)
 *
 * @param serializedValue A numerical value that represents the enum value. It can be used for persisting a message type, and therefore must
 * be unique, and never be changed for existing values.
 * @param isStatus Whether messages of this type are considered to be 'status' messages, i.e. messages that are typically
 * never sent or received but instead indicate some meta information to the user
 * @param canBeEdited Whether the given message type allows editing in general.
 * To check whether the user should be able to edit a particular message, [ch.threema.storage.models.canBeEdited] should be used.
 * @param supportsEmojiReactions Whether the given message type supports emoji reactions
 * @param requiresMessageId Whether messages of this type are required to have a non-null [MessageModel.messageId] when being stored
 * into the database
 */
enum class MessageType(
    @JvmField
    val serializedValue: Int,
    val isStatus: Boolean = false,
    val canBeEdited: Boolean = false,
    val supportsEmojiReactions: Boolean = false,
    val requiresMessageId: Boolean = false,
) {
    TEXT(serializedValue = 0, canBeEdited = true, supportsEmojiReactions = true, requiresMessageId = true),
    LOCATION(serializedValue = 4, supportsEmojiReactions = true, requiresMessageId = true),
    STATUS(serializedValue = 6, isStatus = true),
    POLL(serializedValue = 7, supportsEmojiReactions = true, requiresMessageId = true),
    FILE(serializedValue = 8, supportsEmojiReactions = true, canBeEdited = true, requiresMessageId = true),
    VOIP_STATUS(serializedValue = 9, isStatus = true),

    @Deprecated("TODO(ANDR-5144): This type should be removed eventually")
    VIRTUAL(serializedValue = 10, isStatus = true),
    GROUP_CALL_STATUS(serializedValue = 11, isStatus = true),
    FORWARD_SECURITY_STATUS(serializedValue = 12, isStatus = true),
    GROUP_STATUS(serializedValue = 13, isStatus = true),
    ;

    companion object {
        @JvmStatic
        fun deserialize(serializedValue: Int): MessageType? =
            entries.find { it.serializedValue == serializedValue }
    }
}
