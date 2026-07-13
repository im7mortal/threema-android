package ch.threema.app.listeners

import androidx.annotation.AnyThread
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.ConversationId

interface ContactSettingsListener {
    @AnyThread
    fun onSortingChanged() {
    }

    @AnyThread
    fun onNameFormatChanged(nameFormat: ContactNameFormat) {
    }

    @AnyThread
    fun onIsDefaultContactPictureColoredChanged(isColored: Boolean) {
    }

    @AnyThread
    fun onShowContactDefinedAvatarsChanged(shouldShow: Boolean) {
    }

    @AnyThread
    fun onInactiveContactsSettingChanged() {
    }

    @AnyThread
    fun onNotificationSettingChanged(conversationId: ConversationId) {
    }
}
