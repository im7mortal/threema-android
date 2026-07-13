package ch.threema.data.datatypes

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.IdentityString
import ch.threema.domain.types.toIdentityOrNull
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

private const val PREFIX_DATABASE_VALUE_CONTACT = "i-"
private const val PREFIX_DATABASE_VALUE_GROUP = "g-"
private const val PREFIX_DATABASE_VALUE_DISTRIBUTION_LIST = "d-"

/**
 *  A [ConversationId] can be used to uniquely identify a conversation with either a contact, a group, or a distribution-list.
 *
 *  Since there is no persistent conversation model, this ID is *mostly* just an object created on the fly. One small exception to this is the
 *  [ch.threema.app.services.ConversationTagService], that uses the value returned by [toDatabaseValue].
 *
 *  @see ConversationIdObfuscated
 */
@Immutable
@Parcelize
sealed interface ConversationId : Parcelable {

    /**
     *  Returns the identifier value prefixed with a letter, specifying the conversation type.
     *
     *  Since this is cleartext, do not use this value in other storage spaces, such as the file system or even the shared preferences. In these
     *  cases, the [ConversationIdObfuscated] should be used.
     */
    fun toDatabaseValue(): String

    val obfuscated: ConversationIdObfuscated

    companion object {

        @JvmStatic
        fun fromDatabaseValue(value: String): ConversationId? =
            when {
                value.startsWith(PREFIX_DATABASE_VALUE_CONTACT) -> fromDatabaseValueContact(value)
                value.startsWith(PREFIX_DATABASE_VALUE_GROUP) -> fromDatabaseValueGroup(value)
                value.startsWith(PREFIX_DATABASE_VALUE_DISTRIBUTION_LIST) -> fromDatabaseValueDistributionList(value)
                else -> null
            }

        /**
         *  Parses a [ContactConversationId] from a [value] in this form: `i-01234567`
         *
         *  Returns `null` if [value] does not contain a valid identity.
         *
         *  @see toIdentityOrNull
         */
        private fun fromDatabaseValueContact(value: String): ContactConversationId? {
            val identityDbValue = value.removePrefix(PREFIX_DATABASE_VALUE_CONTACT)
            val identity: IdentityString? = identityDbValue.toIdentityOrNull()?.value
            return identity?.let(::ContactConversationId)
        }

        /**
         *  Parses a [GroupConversationId] from a [value] in this form: `g-1`
         *
         *  Returns `null` if [value] does not contain a valid [GroupDatabaseId] value.
         */
        private fun fromDatabaseValueGroup(value: String): GroupConversationId? {
            val groupDatabaseId: Long? = value.removePrefix(PREFIX_DATABASE_VALUE_GROUP).toLongOrNull()
            return groupDatabaseId?.let(::GroupConversationId)
        }

        /**
         *  Parses a [DistributionListConversationId] from a [value] in this form: `d-1`
         *
         *  Returns `null` if [value] does not contain a valid [Long] value.
         */
        private fun fromDatabaseValueDistributionList(value: String): DistributionListConversationId? {
            val distributionListId: Long? = value.removePrefix(PREFIX_DATABASE_VALUE_DISTRIBUTION_LIST).toLongOrNull()
            return distributionListId?.let(::DistributionListConversationId)
        }
    }
}

@Immutable
@Parcelize
data class ContactConversationId(
    @JvmField val identity: IdentityString,
) : ConversationId {

    @IgnoredOnParcel
    override val obfuscated by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ConversationIdObfuscated.forContact(this)
    }

    override fun toDatabaseValue() = PREFIX_DATABASE_VALUE_CONTACT + identity

    override fun toString() = "ContactConversationId(${obfuscated.value})"
}

@Immutable
@Parcelize
data class GroupConversationId(
    @JvmField val groupDatabaseId: GroupDatabaseId,
) : ConversationId {

    @IgnoredOnParcel
    override val obfuscated by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ConversationIdObfuscated.forGroup(this)
    }

    override fun toDatabaseValue() = PREFIX_DATABASE_VALUE_GROUP + groupDatabaseId.toString()

    override fun toString() = "GroupConversationId(${obfuscated.value})"
}

@Immutable
@Parcelize
data class DistributionListConversationId(
    @JvmField val distributionListId: Long,
) : ConversationId {

    @IgnoredOnParcel
    override val obfuscated by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ConversationIdObfuscated.forDistributionList(this)
    }

    override fun toDatabaseValue() = PREFIX_DATABASE_VALUE_DISTRIBUTION_LIST + distributionListId.toString()

    override fun toString() = "DistributionListConversationId(${obfuscated.value})"
}
