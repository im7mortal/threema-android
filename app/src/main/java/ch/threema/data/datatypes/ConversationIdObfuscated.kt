package ch.threema.data.datatypes

import ch.threema.common.Base32
import ch.threema.common.sha256
import java.util.concurrent.ConcurrentHashMap

/**
 *  A [ConversationIdObfuscated] is a hashed and base-32 encoded variant of a [ConversationId].
 *
 *  It can be used as an alternative to [ConversationId] in places that are not well protected. Since an instance of [ContactConversationId] for
 *  example will contain contact identities in cleartext form.
 *
 *  @property value hashed conversation-id string (base-32)
 *
 *  @see ch.threema.app.services.WallpaperService
 *  @see ch.threema.app.drafts.DraftManager
 *  @see ch.threema.app.services.RingtoneService
 */
data class ConversationIdObfuscated(
    @JvmField val value: String,
) {
    companion object {

        private val cache = ConcurrentHashMap<String, String>()

        /**
         *  Note: The type [ContactConversationId] uses the prefix `i-`, whereas this type ([ConversationIdObfuscated]) uses the prefix `c-` for
         *  contacts conversations. This difference exists due to historical reasons. It cannot be changed without a data migration for the features
         *  that rely on the [value] String.
         */
        @JvmStatic
        fun forContact(contactConversationId: ContactConversationId): ConversationIdObfuscated {
            val value = calculate(input = "c-${contactConversationId.identity}")
            return ConversationIdObfuscated(value)
        }

        @JvmStatic
        fun forGroup(groupConversationId: GroupConversationId): ConversationIdObfuscated {
            val value = calculate(input = "g-${groupConversationId.groupDatabaseId}")
            return ConversationIdObfuscated(value)
        }

        @JvmStatic
        fun forDistributionList(distributionListConversationId: DistributionListConversationId): ConversationIdObfuscated {
            val value = calculate(input = "d-${distributionListConversationId.distributionListId}")
            return ConversationIdObfuscated(value)
        }

        private fun calculate(input: String): String =
            cache.getOrPut(input) {
                Base32.encode(sha256(input))
            }
    }
}
