package ch.threema.app.eventbus.events

import ch.threema.domain.taskmanager.TriggerSource

sealed class ProfileEvent {
    data class ProfilePictureUpdated(val triggerSource: TriggerSource) : ProfileEvent()

    data class NicknameUpdated(val newNickname: String) : ProfileEvent()
}
