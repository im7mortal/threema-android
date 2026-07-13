package ch.threema.app.usecases.contacts

import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.listeners.ContactSettingsListener
import ch.threema.app.listeners.SynchronizeContactsListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.routines.SynchronizeContactsRoutine
import ch.threema.app.usecases.conversations.AvatarIteration
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactConversationId
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("WatchContactAvatarIterationUseCase")

/**
 * Why this use-case exists: See [AvatarIteration] definition
 */
class WatchContactAvatarIterationUseCase(
    private val globalEventFlows: GlobalEventFlows,
) {

    /**
     *  Creates a cold [Flow] of an incrementing [AvatarIteration] value for the given [contactConversationId].
     *
     *  ###### Direct emit promise
     *  This flow fulfills the promise to directly emit the current initial iteration value.
     *
     *  ###### Overflow strategy
     *  If a consumer consumes the values slower than they get produced, the old unconsumed value gets **dropped** in favor of the most recent value.
     *
     *  ###### Error strategy
     *  Every exception will flow downstream.
     */
    fun call(contactConversationId: ContactConversationId): Flow<AvatarIteration> = callbackFlow {
        val avatarIterationLock = Any()
        var avatarIteration = AvatarIteration.initial

        // Direct emit promise
        trySend(avatarIteration)
            .onClosed {
                // Collection already ended
                return@callbackFlow
            }

        fun incrementAvatarIterationAndSend() {
            synchronized(avatarIterationLock) {
                avatarIteration = avatarIteration.inc()
                trySend(avatarIteration)
                    .onClosed { throwable ->
                        logger.error("Tried to send a new value after channel was closed", throwable)
                    }
            }
        }

        // Catching both events:
        // - The contact updated its profile picture
        // - The user set a profile picture for the contact in Threema
        launch {
            globalEventFlows
                .contacts
                .filter { contactEvent ->
                    contactEvent.identity.value == contactConversationId.identity
                }
                .filterIsInstance<ContactEvent.ContactProfilePictureUpdated>()
                .collect {
                    incrementAvatarIterationAndSend()
                }
        }

        // Catching event:
        // - The user set a profile picture for his synced contact via Android address book
        val synchronizeContactsListener = object : SynchronizeContactsListener {
            override fun onFinished(finishedRoutine: SynchronizeContactsRoutine?) {
                incrementAvatarIterationAndSend()
            }
        }

        // Catching event:
        // - The user changed the display settings for other contacts avatars
        val contactSettingsListener = object : ContactSettingsListener {
            override fun onIsDefaultContactPictureColoredChanged(isColored: Boolean) {
                incrementAvatarIterationAndSend()
            }

            override fun onShowContactDefinedAvatarsChanged(shouldShow: Boolean) {
                incrementAvatarIterationAndSend()
            }
        }

        ListenerManager.synchronizeContactsListeners.add(synchronizeContactsListener)
        ListenerManager.contactSettingsListeners.add(contactSettingsListener)
        awaitClose {
            ListenerManager.synchronizeContactsListeners.remove(synchronizeContactsListener)
            ListenerManager.contactSettingsListeners.remove(contactSettingsListener)
        }
    }
        .buffer(capacity = CONFLATED)
}
