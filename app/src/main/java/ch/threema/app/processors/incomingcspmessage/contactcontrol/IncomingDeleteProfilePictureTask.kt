package ch.threema.app.processors.incomingcspmessage.contactcontrol

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.managers.ServiceManager
import ch.threema.app.processors.incomingcspmessage.IncomingCspMessageSubTask
import ch.threema.app.processors.incomingcspmessage.ReceiveStepsResult
import ch.threema.app.tasks.ReflectContactSyncUpdateImmediateTask.ReflectContactProfilePicture
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.csp.messages.DeleteProfilePictureMessage
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.Identity

private val logger = getThreemaLogger("IncomingDeleteProfilePictureTask")

class IncomingDeleteProfilePictureTask(
    message: DeleteProfilePictureMessage,
    triggerSource: TriggerSource,
    serviceManager: ServiceManager,
    private val globalEventBuses: GlobalEventBuses,
) : IncomingCspMessageSubTask<DeleteProfilePictureMessage>(message, triggerSource, serviceManager) {
    private val fileService by lazy { serviceManager.fileService }
    private val contactModelRepository by lazy { serviceManager.modelRepositories.contacts }
    private val multiDeviceManager by lazy { serviceManager.multiDeviceManager }

    private val identity = message.fromIdentity

    override suspend fun executeMessageStepsFromRemote(handle: ActiveTaskCodec): ReceiveStepsResult {
        val contactModel = contactModelRepository.getByIdentity(identity) ?: run {
            logger.warn("Delete profile picture message received from unknown contact")
            return ReceiveStepsResult.DISCARD
        }

        reflectProfilePictureRemoved(handle)

        fileService.removeContactDefinedProfilePicture(identity)
        globalEventBuses.contacts.emit(ContactEvent.ContactProfilePictureUpdated(Identity(identity)))

        contactModel.setIsRestored(false)

        return ReceiveStepsResult.SUCCESS
    }

    override suspend fun executeMessageStepsFromSync(): ReceiveStepsResult {
        val contactModel = contactModelRepository.getByIdentity(identity) ?: run {
            logger.error("Reflected delete profile picture message received from unknown contact")
            return ReceiveStepsResult.DISCARD
        }
        contactModel.setIsRestored(false)
        return ReceiveStepsResult.SUCCESS
    }

    private suspend fun reflectProfilePictureRemoved(handle: ActiveTaskCodec) {
        if (multiDeviceManager.isMultiDeviceActive) {
            ReflectContactProfilePicture(
                contactIdentity = identity,
                profilePictureUpdate = ReflectContactProfilePicture.RemovedProfilePicture,
            ).reflect(handle)
        }
    }
}
