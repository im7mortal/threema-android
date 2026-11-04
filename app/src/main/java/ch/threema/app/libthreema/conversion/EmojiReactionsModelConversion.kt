package ch.threema.app.libthreema.conversion

import ch.threema.data.models.EmojiReactionsModel
import ch.threema.libthreema.MessageReaction as LibthreemaMessageReaction

fun EmojiReactionsModel?.convertToLibthreemaReactions(): List<LibthreemaMessageReaction> = this?.data?.map { emojiReactionData ->
    LibthreemaMessageReaction(
        createdAt = emojiReactionData.reactedAt.toEpochMilli().toULong(),
        creatorIdentity = emojiReactionData.senderIdentity,
        emoji = emojiReactionData.emojiSequence.convertToLibthreemaEmoji(),
    )
} ?: emptyList()
