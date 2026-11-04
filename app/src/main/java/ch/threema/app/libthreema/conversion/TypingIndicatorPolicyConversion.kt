package ch.threema.app.libthreema.conversion

import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.libthreema.TypingIndicatorPolicy as LibthreemaTypingIndicatorPolicy

fun TypingIndicatorPolicy.toLibthreemaTypingIndicatorPolicy() = when (this) {
    TypingIndicatorPolicy.SEND -> LibthreemaTypingIndicatorPolicy.SEND_TYPING_INDICATOR
    TypingIndicatorPolicy.DONT_SEND -> LibthreemaTypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR
    TypingIndicatorPolicy.DEFAULT -> null
}

fun LibthreemaTypingIndicatorPolicy?.toTypingIndicatorPolicy() = when (this) {
    LibthreemaTypingIndicatorPolicy.SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.SEND
    LibthreemaTypingIndicatorPolicy.DONT_SEND_TYPING_INDICATOR -> TypingIndicatorPolicy.DONT_SEND
    null -> TypingIndicatorPolicy.DEFAULT
}
