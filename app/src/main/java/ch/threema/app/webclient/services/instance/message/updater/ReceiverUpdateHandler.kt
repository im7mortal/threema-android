package ch.threema.app.webclient.services.instance.message.updater

import androidx.annotation.AnyThread
import androidx.annotation.StringDef
import androidx.annotation.WorkerThread
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.DistributionListEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.GroupService
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.app.utils.executor.HandlerExecutor
import ch.threema.app.webclient.Protocol
import ch.threema.app.webclient.converter.Contact
import ch.threema.app.webclient.converter.DistributionList
import ch.threema.app.webclient.converter.Group
import ch.threema.app.webclient.converter.MsgpackObjectBuilder
import ch.threema.app.webclient.converter.Receiver
import ch.threema.app.webclient.converter.Utils
import ch.threema.app.webclient.exceptions.ConversionException
import ch.threema.app.webclient.services.instance.MessageDispatcher
import ch.threema.app.webclient.services.instance.MessageUpdater
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.domain.types.Identity
import ch.threema.storage.factories.ContactModelFactory
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.group.GroupModelOld
import kotlin.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.msgpack.core.MessagePackException

private val logger = getThreemaLogger("ReceiverUpdateHandler")

/**
 * Notify Threema Web about changes to receivers (contacts, groups, distribution lists).
 */
@WorkerThread
class ReceiverUpdateHandler @AnyThread constructor(
    private val handler: HandlerExecutor,
    private val dispatcher: MessageDispatcher,
    private val contactModelFactory: ContactModelFactory,
    private val synchronizeContactsService: SynchronizeContactsService,
    private val groupService: GroupService,
    private val preferenceService: PreferenceService,
) : MessageUpdater(Protocol.SUB_TYPE_RECEIVER), KoinComponent {
    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        Protocol.ARGUMENT_MODE_NEW,
        Protocol.ARGUMENT_MODE_MODIFIED,
        Protocol.ARGUMENT_MODE_REMOVED,
    )
    private annotation class UpdateMode

    private val globalEventFlows: GlobalEventFlows by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    private var coroutineScope: CoroutineScope? = null

    override fun register() {
        coroutineScope?.cancel()
        coroutineScope = CoroutineScope(dispatcherProvider.worker)
        coroutineScope?.launch {
            globalEventFlows.contacts.collect { event ->
                when (event) {
                    is ContactEvent.NewContact -> onNewContact(event.identity)
                    is ContactEvent.ContactUpdated -> onContactModified(event.identity)
                    is ContactEvent.ContactRemoved -> onContactRemoved(event.identity)
                    is ContactEvent.ContactProfilePictureUpdated -> Unit
                }
            }
        }
        coroutineScope?.launch {
            globalEventFlows.groups.collect { event ->
                when (event) {
                    is GroupEvent.NewGroup -> onGroupCreated(event.groupIdentity)
                    is GroupEvent.GroupProfilePictureUpdated -> onGroupModified(event.groupIdentity)
                    is GroupEvent.GroupRenamed -> onGroupModified(event.groupIdentity)
                    is GroupEvent.GroupUpdated -> onGroupModified(event.groupIdentity)
                    is GroupEvent.MemberKicked -> onGroupModified(event.groupIdentity)
                    is GroupEvent.MemberLeft -> onGroupModified(event.groupIdentity)
                    is GroupEvent.NewMember -> onGroupModified(event.groupIdentity)
                    is GroupEvent.UserLeftGroup -> onGroupModified(event.groupIdentity)
                    is GroupEvent.GroupRemoved -> removeGroup(event.groupDbId)
                    is GroupEvent.GroupStateChanged -> Unit
                }
            }
        }
        coroutineScope?.launch {
            globalEventFlows.distributionLists.collect { event ->
                when (event) {
                    is DistributionListEvent.NewDistributionList -> {
                        logger.debug("Distribution List Listener: onCreate")
                        updateDistributionList(event.distributionList, Protocol.ARGUMENT_MODE_NEW)
                    }
                    is DistributionListEvent.DistributionListUpdated -> {
                        logger.debug("Distribution List Listener: onModify")
                        updateDistributionList(event.distributionList, Protocol.ARGUMENT_MODE_MODIFIED)
                    }
                    is DistributionListEvent.DistributionListRemoved -> {
                        logger.debug("Distribution List Listener: onRemove")
                        updateDistributionList(event.distributionList, Protocol.ARGUMENT_MODE_REMOVED)
                    }
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

    private fun onGroupCreated(groupIdentity: GroupIdentity) {
        getGroupModel(groupIdentity)
            ?.let { group ->
                updateGroup(group, Protocol.ARGUMENT_MODE_NEW)
            }
    }

    private fun onGroupModified(groupIdentity: GroupIdentity) {
        getGroupModel(groupIdentity)
            ?.let { group ->
                updateGroup(group, Protocol.ARGUMENT_MODE_MODIFIED)
            }
    }

    private fun getGroupModel(groupIdentity: GroupIdentity): GroupModelOld? {
        val groupModel = groupService.getByGroupIdentity(groupIdentity)
        if (groupModel == null) {
            logger.error("Group model is null")
        }

        return groupModel
    }

    @AnyThread
    private fun updateContact(contact: ContactModel, @UpdateMode mode: String) {
        handler.post {
            try {
                // Convert contact and dispatch
                val data = Contact.convert(contact, preferenceService.getContactNameFormat())
                update(Utils.ModelWrapper(contact), data, mode)
            } catch (e: ConversionException) {
                logger.error("Exception", e)
            }
        }
    }

    @AnyThread
    private fun updateGroup(group: GroupModelOld, @UpdateMode mode: String) {
        handler.post {
            try {
                // Convert contact and dispatch
                val data = Group.convert(group)
                update(Utils.ModelWrapper(group), data, mode)
            } catch (e: ConversionException) {
                logger.error("Exception", e)
            }
        }
    }

    @AnyThread
    private fun removeGroup(groupDbId: Long) {
        handler.post {
            try {
                // Convert contact and dispatch
                val builder = MsgpackObjectBuilder()
                try {
                    builder.put(Receiver.ID, groupDbId.toString())
                } catch (e: NullPointerException) {
                    throw ConversionException(e.toString())
                }
                update(Utils.ModelWrapper(groupDbId), builder, Protocol.ARGUMENT_MODE_REMOVED)
            } catch (e: ConversionException) {
                logger.error("Exception", e)
            }
        }
    }

    @AnyThread
    private fun updateDistributionList(distributionList: DistributionListModel, @UpdateMode mode: String) {
        handler.post {
            try {
                // Convert contact and dispatch
                val data = DistributionList.convert(distributionList)
                update(Utils.ModelWrapper(distributionList), data, mode)
            } catch (e: ConversionException) {
                logger.error("Exception", e)
            }
        }
    }

    private fun update(model: Utils.ModelWrapper, data: MsgpackObjectBuilder, @UpdateMode mode: String) {
        try {
            val args = Receiver.getArguments(model)
            args.put(Protocol.ARGUMENT_MODE, mode)

            logger.debug("Sending receiver update")
            send(dispatcher, data, args)
        } catch (e: ConversionException) {
            logger.error("Exception", e)
        } catch (e: MessagePackException) {
            logger.error("Exception", e)
        }
    }

    private fun onContactModified(identity: Identity) {
        if (synchronizeContactsService.isFullSyncInProgress()) {
            // A sync is currently in progress. This causes a *lot* of onModified
            // listeners to be called.
            // To avoid flooding the webclient with updates, we simply ignore the
            // updates and send the entire receivers list as soon as the sync is done.
            logger.debug("Ignoring onModified (contact sync in progress)")
            return
        }

        val modifiedContactModel = getContactModelByIdentity(identity)
        if (modifiedContactModel != null) {
            updateContact(modifiedContactModel, Protocol.ARGUMENT_MODE_MODIFIED)
        }
    }

    private fun onNewContact(identity: Identity) {
        val newContactModel = getContactModelByIdentity(identity)
        if (newContactModel != null) {
            updateContact(newContactModel, Protocol.ARGUMENT_MODE_NEW)
        }
    }

    private fun onContactRemoved(identity: Identity) {
        handler.post {
            try {
                val builder = MsgpackObjectBuilder()
                builder.put(Receiver.ID, identity.value)
                update(
                    Utils.ModelWrapper(Receiver.Type.CONTACT, identity.value),
                    builder,
                    Protocol.ARGUMENT_MODE_REMOVED,
                )
            } catch (e: ConversionException) {
                logger.error("Exception", e)
            }
        }
    }

    private fun getContactModelByIdentity(identity: Identity): ContactModel? =
        contactModelFactory.getByIdentity(identity.value)
}
