package ch.threema.data.datatypes

import ch.threema.domain.models.MessageId
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.IdentityString

sealed interface MessageIdentifier {
    val messageId: MessageId
}

data class ContactMessageIdentifier(
    override val messageId: MessageId,
    val identity: IdentityString,
) : MessageIdentifier

data class GroupMessageIdentifier(
    override val messageId: MessageId,
    val groupDatabaseId: GroupDatabaseId,
) : MessageIdentifier

data class DistributionListMessageIdentifier(
    override val messageId: MessageId,
    val distributionListId: Long,
) : MessageIdentifier
