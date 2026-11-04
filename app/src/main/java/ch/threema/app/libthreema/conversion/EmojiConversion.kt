package ch.threema.app.libthreema.conversion

import ch.threema.libthreema.EmojiReaction as LibthreemaEmojiReaction

fun String.convertToLibthreemaEmoji() = LibthreemaEmojiReaction(
    codepoint = this,
)
