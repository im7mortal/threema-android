package ch.threema.app.support

import androidx.annotation.StringRes
import ch.threema.data.datatypes.ConversationId

sealed interface ContactSupportViewEvent {
    data class OnSupportContactAvailable(val conversationId: ConversationId) : ContactSupportViewEvent
    data class OnSupportContactUnavailable(@StringRes val message: Int) : ContactSupportViewEvent
}
