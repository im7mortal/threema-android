package ch.threema.app.fragments

import androidx.lifecycle.lifecycleScope
import ch.threema.android.dismissDialog
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.usecases.conversations.EmptyOrDeleteConversationsUseCase
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ContactsSectionFragmentKotlin")

abstract class ContactsSectionFragmentKotlin : MainFragment() {

    private val globalEventBuses: GlobalEventBuses by inject()
    private val emptyOrDeleteConversationsUseCase: EmptyOrDeleteConversationsUseCase by inject()

    protected fun deleteConversation(contactIdentity: IdentityString) {
        lifecycleScope.launch {
            GenericProgressDialog.newInstance(R.string.deleting_chat, R.string.emptying_chat_deleting_messages)
                .show(childFragmentManager, DIALOG_TAG_DELETING_CONVERSATION)
            val result = emptyOrDeleteConversationsUseCase.call(
                conversationIds = listOf(ContactConversationId(contactIdentity)),
                mode = EmptyOrDeleteConversationsUseCase.Mode.DELETE,
            )
            childFragmentManager.dismissDialog(DIALOG_TAG_DELETING_CONVERSATION)
            when (result) {
                is EmptyOrDeleteConversationsUseCase.Result.Completed -> {
                    globalEventBuses.conversations.emit(ConversationEvent.AllConversationsUpdated)
                    globalEventBuses.contacts.emit(ContactEvent.ContactUpdated(Identity(contactIdentity)))
                }
                is EmptyOrDeleteConversationsUseCase.Result.UnknownConversation -> {
                    logger.error("Conversation to delete not found")
                    showToast(R.string.an_error_occurred)
                }
            }
        }
    }

    companion object {
        private const val DIALOG_TAG_DELETING_CONVERSATION = "dc"
    }
}
