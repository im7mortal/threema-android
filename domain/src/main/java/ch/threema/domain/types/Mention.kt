package ch.threema.domain.types

import androidx.compose.runtime.Immutable
import ch.threema.domain.protocol.csp.ProtocolDefines

/**
 * A [Mention] represents a mention in the text of a message.
 * A mention is encoded as `@[<payload>]`, where `payload` must be either
 * an identity string or the constant '@@@@@@@@'.
 */
@Immutable
sealed class Mention {
    /**
     * Represents a mention of a specific identity
     */
    @Immutable
    data class Identity(val identity: ch.threema.domain.types.Identity) : Mention() {
        override fun toString() = encode(payload)

        override val payload
            get() = identityString

        val identityString: String
            get() = identity.value

        override fun includes(identity: ch.threema.domain.types.Identity) =
            this.identity == identity
    }

    /**
     * Represents a mention of all users in a group
     */
    @Immutable
    data object AllUsers : Mention() {
        override val payload: String
            get() = ALL_USERS_PAYLOAD

        override fun toString() = encode(payload)

        override fun includes(identity: ch.threema.domain.types.Identity) =
            true
    }

    abstract fun includes(identity: ch.threema.domain.types.Identity): Boolean

    protected abstract val payload: String

    data class Match(
        val mention: Mention,
        val startIndex: Int,
    ) {
        val endIndex: Int
            get() = startIndex + ENCODED_LENGTH
    }

    companion object {
        private const val ALL_USERS_PAYLOAD = "@@@@@@@@"
        private const val MENTION_PREFIX = "@["
        private const val MENTION_SUFFIX = "]"
        private const val IDENTITY_REGEX = "[0-9A-Z*][0-9A-Z]{${ProtocolDefines.IDENTITY_LEN - 1}}"
        private val MENTION_SEARCH_REGEX = (
            Regex.escape(MENTION_PREFIX) +
                "($IDENTITY_REGEX|$ALL_USERS_PAYLOAD)" +
                Regex.escape(MENTION_SUFFIX)
            ).toRegex()

        const val ENCODED_LENGTH = ProtocolDefines.IDENTITY_LEN + MENTION_PREFIX.length + MENTION_SUFFIX.length

        private fun encode(payload: String): String =
            MENTION_PREFIX + payload + MENTION_SUFFIX

        @JvmStatic
        fun parse(encodedMention: String): Mention? {
            if (
                encodedMention.length != ENCODED_LENGTH ||
                !encodedMention.startsWith(MENTION_PREFIX) ||
                !encodedMention.endsWith(MENTION_SUFFIX)
            ) {
                return null
            }
            return parsePayload(encodedMention.substring(MENTION_PREFIX.length, MENTION_PREFIX.length + ProtocolDefines.IDENTITY_LEN))
        }

        private fun parsePayload(payload: String): Mention? =
            if (payload == ALL_USERS_PAYLOAD) {
                AllUsers
            } else {
                payload.toIdentityOrNull()
                    ?.let { identity ->
                        Identity(identity)
                    }
            }

        @JvmStatic
        fun findMatches(text: String): Sequence<Match> =
            MENTION_SEARCH_REGEX
                .findAll(text)
                .mapNotNull { mentionMatchResult ->
                    val startIndex = mentionMatchResult.range.first
                    val mention = mentionMatchResult.groups[1]?.value
                        ?.let(Mention::parsePayload)
                        ?: return@mapNotNull null
                    Match(mention, startIndex)
                }

        @JvmStatic
        fun stripMentions(text: String): String =
            MENTION_SEARCH_REGEX.replace(text, "")
    }
}
