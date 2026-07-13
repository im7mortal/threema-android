package ch.threema.app.webclient.services.instance.message.updater

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ProfileEvent
import ch.threema.app.services.ContactService
import ch.threema.app.services.UserService
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.app.webclient.Protocol
import ch.threema.app.webclient.converter.Profile
import ch.threema.app.webclient.services.instance.MessageDispatcher
import ch.threema.app.webclient.services.instance.MessageUpdater
import ch.threema.common.DispatcherProvider
import kotlin.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@WorkerThread
class ProfileUpdateHandler @AnyThread constructor(
    private val handler: HandlerExecutor,
    private val updateDispatcher: MessageDispatcher,
    private val userService: UserService,
    private val contactService: ContactService,
) : MessageUpdater(Protocol.SUB_TYPE_PROFILE), KoinComponent {

    private val globalEventFlows: GlobalEventFlows by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private var coroutineScope: CoroutineScope? = null

    override fun register() {
        coroutineScope?.cancel()
        coroutineScope = CoroutineScope(dispatcherProvider.worker)
        coroutineScope?.launch {
            globalEventFlows.profiles.collect { event ->
                when (event) {
                    is ProfileEvent.NicknameUpdated -> onNicknameChanged(event.newNickname)
                    is ProfileEvent.ProfilePictureUpdated -> onProfilePictureChanged()
                }
            }
        }
    }

    /**
     * This method can be safely called multiple times without any negative side effects
     */
    override fun unregister() {
        coroutineScope?.cancel()
        coroutineScope = null
    }

    private fun onNicknameChanged(newNickname: String) {
        handler.post {
            sendProfile(newNickname, false)
        }
    }

    private fun onProfilePictureChanged() {
        handler.post {
            sendProfile(userService.getPublicNickname(), true)
        }
    }

    /**
     * Send the updated profile to the peer.
     */
    private fun sendProfile(nickname: String?, sendAvatar: Boolean) {
        val data = if (sendAvatar) {
            val avatar = contactService.getAvatar(userService.getIdentity(), true)
                ?.let { bitmap ->
                    BitmapUtil.bitmapToByteArray(bitmap, Protocol.FORMAT_AVATAR, Protocol.QUALITY_AVATAR_HIRES)
                }
            Profile.convert(nickname, avatar)
        } else {
            Profile.convert(nickname)
        }

        send(updateDispatcher, data, null)
    }
}
