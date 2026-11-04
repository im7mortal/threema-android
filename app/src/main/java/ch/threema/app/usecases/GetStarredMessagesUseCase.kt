package ch.threema.app.usecases

import ch.threema.android.ResolvableString
import ch.threema.app.compose.common.text.conversation.ConversationTextAnalyzer
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.MessageService
import ch.threema.app.services.MessageService.FILTER_CHATS
import ch.threema.app.services.MessageService.FILTER_GROUPS
import ch.threema.app.services.MessageService.FILTER_INCLUDE_ARCHIVED
import ch.threema.app.services.MessageService.FILTER_STARRED_ONLY
import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.common.DispatcherProvider
import ch.threema.common.takeUnlessBlank
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.MentionNameData
import ch.threema.domain.types.Mention
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class GetStarredMessagesUseCase(
    private val messageService: MessageService,
    private val preferenceService: PreferenceService,
    private val watchAllMentionNamesUseCase: WatchAllMentionNamesUseCase,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun call(
        query: String?,
        @PreferenceService.StarredMessagesSortOrder order: Int,
    ): List<StarredMessageWithMentionInfo> {
        return withContext(dispatcherProvider.io) {
            val messageModels = getFilteredAndOrderedMessageModels(
                query = query?.takeUnlessBlank(),
                order = order,
            )

            // This watch-use-case can be used to read the data once because it fulfills the direct-emit-promise
            val mentionNameData: List<MentionNameData> = watchAllMentionNamesUseCase.call().first()

            val contactNameFormat: ContactNameFormat = preferenceService.getContactNameFormat()

            messageModels.map { messageModel ->
                mapToStarredMessageWithMentionInfo(
                    messageModel = messageModel,
                    mentionNameData = mentionNameData,
                    contactNameFormat = contactNameFormat,
                )
            }
        }
    }

    private fun getFilteredAndOrderedMessageModels(
        query: String?,
        @PreferenceService.StarredMessagesSortOrder order: Int,
    ): List<MessageModel> =
        messageService.getMessagesForText(
            /* queryString = */
            query,
            /* filterFlags = */
            FILTER_STARRED_ONLY or FILTER_GROUPS or FILTER_CHATS or FILTER_INCLUDE_ARCHIVED,
            /* sortAscending = */
            order == PreferenceService.STARRED_MESSAGES_SORT_ORDER_DATE_ASCENDING,
        )

    private fun mapToStarredMessageWithMentionInfo(
        messageModel: MessageModel,
        mentionNameData: List<MentionNameData>,
        contactNameFormat: ContactNameFormat,
    ): StarredMessageWithMentionInfo {
        val messageContentThatCouldContainMentions = getMessageContentThatCouldContainMentions(
            messageModel = messageModel,
        )
        val mentionNames: Map<Mention, ResolvableString> = ConversationTextAnalyzer.findResolvableMentionNames(
            input = messageContentThatCouldContainMentions,
            mentionNameData = mentionNameData,
            contactNameFormat = contactNameFormat,
        )
        return StarredMessageWithMentionInfo(
            messageModel = messageModel,
            mentionedNames = mentionNames,
        )
    }

    private fun getMessageContentThatCouldContainMentions(
        messageModel: MessageModel,
    ): String =
        when (messageModel.type) {
            MessageType.TEXT -> messageModel.body
            MessageType.FILE -> messageModel.caption
            else -> null
        } ?: ""
}

data class StarredMessageWithMentionInfo(
    val messageModel: MessageModel,
    val mentionedNames: Map<Mention, ResolvableString>,
)
