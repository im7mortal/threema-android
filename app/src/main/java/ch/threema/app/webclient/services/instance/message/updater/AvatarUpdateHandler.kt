package ch.threema.app.webclient.services.instance.message.updater

import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.app.webclient.Protocol
import ch.threema.app.webclient.converter.MsgpackObjectBuilder
import ch.threema.app.webclient.converter.Utils
import ch.threema.app.webclient.exceptions.ConversionException
import ch.threema.app.webclient.services.instance.MessageDispatcher
import ch.threema.app.webclient.services.instance.MessageUpdater
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.group.GroupModelOld
import kotlin.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.msgpack.core.MessagePackException

private val logger = getThreemaLogger("AvatarUpdateHandler")

/**
 * Notify Threema Web about changes to avatars.
 */
@WorkerThread
class AvatarUpdateHandler @AnyThread constructor(
    private val handler: HandlerExecutor,
    private val updateDispatcher: MessageDispatcher,
    private val contactService: ContactService,
    private val groupService: GroupService,
) : MessageUpdater(Protocol.SUB_TYPE_AVATAR), KoinComponent {
    private val globalEventFlows: GlobalEventFlows by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private var coroutineScope: CoroutineScope? = null

    override fun register() {
        coroutineScope?.cancel()
        coroutineScope = CoroutineScope(dispatcherProvider.worker)
        coroutineScope?.launch {
            globalEventFlows.contacts
                .filterIsInstance<ContactEvent.ContactProfilePictureUpdated>()
                .collect { event ->
                    onContactProfilePictureUpdated(event.identity)
                }
        }
        coroutineScope?.launch {
            globalEventFlows.groups
                .filterIsInstance<GroupEvent.GroupProfilePictureUpdated>()
                .collect { event ->
                    onGroupProfilePictureUpdated(event.groupIdentity)
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

    private fun onContactProfilePictureUpdated(identity: Identity) {
        logger.debug("Contact Listener: onAvatarChanged")
        val contactModel = contactService.getByIdentity(identity.value)
        if (contactModel == null) {
            logger.error("Got an avatar update for an unknown contact")
        } else {
            handler.post { update(contactModel) }
        }
    }

    private fun onGroupProfilePictureUpdated(groupIdentity: GroupIdentity) {
        val groupModel = groupService.getByGroupIdentity(groupIdentity)
        if (groupModel == null) {
            logger.error("Group model is null")
            return
        }
        handler.post { update(groupModel) }
    }

    private fun update(contact: ContactModel) {
        update(Utils.ModelWrapper(contact))
    }

    private fun update(group: GroupModelOld) {
        update(Utils.ModelWrapper(group))
    }

    private fun update(model: Utils.ModelWrapper) {
        try {
            // Get avatar
            val data = model.getAvatar(true, Protocol.SIZE_AVATAR_HIRES_MAX_PX)

            // Convert message and prepare arguments
            val args = MsgpackObjectBuilder()
            args.put(Protocol.ARGUMENT_RECEIVER_TYPE, model.type)
            args.put(Protocol.ARGUMENT_RECEIVER_ID, model.id)

            // Send message
            logger.debug("Sending {} avatar update", model.type)
            send(updateDispatcher, data, args)
        } catch (e: ConversionException) {
            logger.error("Exception", e)
        } catch (e: MessagePackException) {
            logger.error("Exception", e)
        }
    }
}
