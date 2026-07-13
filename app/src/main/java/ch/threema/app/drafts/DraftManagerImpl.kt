package ch.threema.app.drafts

import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.ConversationIdObfuscated
import ch.threema.domain.models.MessageId
import ch.threema.domain.types.MessageIdString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("DraftManagerImpl")

@OptIn(FlowPreview::class)
class DraftManagerImpl(
    private val preferenceService: PreferenceService,
    dispatcherProvider: DispatcherProvider,
) : DraftManager {
    private val messageDraftsFlow = MutableStateFlow(mapOf<ConversationIdObfuscated, MessageDraft>())
    private val coroutineScope = CoroutineScope(dispatcherProvider.worker)

    override val drafts: StateFlow<Map<ConversationIdObfuscated, MessageDraft>> = messageDraftsFlow

    fun init() {
        try {
            val messages: Map<ConversationIdObfuscated, String?> = preferenceService.getMessageDrafts() ?: emptyMap()
            val quotes: Map<ConversationIdObfuscated, String?> = preferenceService.getQuoteDrafts() ?: emptyMap()

            messageDraftsFlow.value = messages
                .mapValues { (conversationIdObfuscated: ConversationIdObfuscated, text: String?) ->
                    MessageDraft(
                        text = text ?: "",
                        quotedMessageId = quotes[conversationIdObfuscated]
                            ?.let(MessageId::fromString),
                    )
                }
        } catch (e: Exception) {
            logger.error("Failed to retrieve message drafts from storage", e)
        }

        coroutineScope.launch {
            messageDraftsFlow
                .drop(1)
                .collect {
                    persistDrafts()
                }
        }
    }

    private fun persistDrafts() {
        try {
            val messageDrafts: Map<ConversationIdObfuscated, MessageDraft> = messageDraftsFlow.value
            logger.debug("Persisting {} drafts", messageDrafts.size)
            val texts: Map<ConversationIdObfuscated, String> = messageDrafts
                .mapValues { (_, draft) ->
                    draft.text
                }
            val quotes: Map<ConversationIdObfuscated, MessageIdString?> = messageDrafts
                .mapValues { (_, draft) ->
                    draft.quotedMessageId?.toString()
                }
                .filterValues { quoteApiMessageId ->
                    quoteApiMessageId != null
                }
            preferenceService.setMessageDrafts(texts)
            preferenceService.setQuoteDrafts(quotes)
        } catch (e: Exception) {
            logger.error("Failed to persist drafts", e)
        }
    }

    override fun get(conversationId: ConversationId): MessageDraft? =
        messageDraftsFlow.value[conversationId.obfuscated]

    override fun remove(conversationId: ConversationId) {
        set(conversationId, text = null)
    }

    override fun set(conversationId: ConversationId, text: String?, quotedMessageId: MessageId?) {
        messageDraftsFlow.update { messageDrafts ->
            if (text.isNullOrBlank()) {
                messageDrafts.minus(conversationId.obfuscated)
            } else {
                messageDrafts.plus(
                    conversationId.obfuscated to MessageDraft(
                        text = text,
                        quotedMessageId = quotedMessageId,
                    ),
                )
            }
        }
    }
}
