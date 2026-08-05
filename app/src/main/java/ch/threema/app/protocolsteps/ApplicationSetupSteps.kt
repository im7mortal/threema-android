package ch.threema.app.protocolsteps

import androidx.annotation.WorkerThread
import ch.threema.app.BuildConfig
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.app.tasks.FSRefreshStepsTask
import ch.threema.app.tasks.OutgoingContactRequestProfilePictureTask
import ch.threema.app.workers.ContactUpdateWorker
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.IdentityState
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.taskmanager.TaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("ApplicationSetupSteps")

class ApplicationSetupSteps(
    private val contactModelRepository: ContactModelRepository,
    private val groupModelRepository: GroupModelRepository,
    private val contactService: ContactService,
    private val groupService: GroupService,
    private val apiConnector: APIConnector,
    private val userService: UserService,
    private val preferenceService: PreferenceService,
    private val groupFlowDispatcher: GroupFlowDispatcher,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskManager: TaskManager,
    private val updateUserAvailabilityStatus: suspend (AvailabilityStatus) -> Unit,
    dispatcherProvider: DispatcherProvider,
) {
    private val coroutineScope = CoroutineScope(dispatcherProvider.io)

    /**
     * Run the _Application Setup Steps_ as defined in the protocol.
     */
    @WorkerThread
    fun run(): Boolean {
        logger.info("Running application setup steps")
        // Send the feature mask to the server and update the contacts. It is important that the feature
        // masks of the contacts are updated to check whether the contacts support FS or not.
        if (
            !ContactUpdateWorker.sendFeatureMaskAndUpdateContacts(
                contactModelRepository = contactModelRepository,
                contactService = contactService,
                apiConnector = apiConnector,
                userService = userService,
                preferenceService = preferenceService,
                pollingHelper = null,
            )
        ) {
            logger.warn("Aborting application setup steps as identity state update did not work")
            return false
        }

        // TODO(ANDR-5095): Make the application setup steps function suspend instead of launching a new coroutine here without awaiting it.
        if (BuildConfig.AVAILABILITY_STATUS_ENABLED) {
            coroutineScope.launch {
                updateUserAvailabilityStatus(AvailabilityStatus.None)
            }
        }

        // TODO(ANDR-3583) Logic for FS Refresh Steps is duplicated
        // Get all groups where the user is a member (or creator). Only include groups that are not
        // deleted.
        val groupModels = groupModelRepository.getAll().filter { groupModel -> groupModel.isMember() }

        // Find group contacts of groups that are not left
        val groupMemberIdentities = groupModels
            .flatMap { groupModel ->
                groupModel.data?.otherMembersAndCreator ?: emptySet()
            }
        val groupContacts = contactModelRepository
            .getByIdentities(groupMemberIdentities.toSet())

        // Find valid contacts with defined last-update flag
        val contactsWithConversation = contactModelRepository.getAll()
            .filter { contactModel -> contactModel.data?.lastUpdateAt != null }
            .toSet()

        // Determine the solicited contacts defined by group contacts and conversation contacts and
        // remove invalid contacts
        val solicitedContactIdentities = (groupContacts + contactsWithConversation)
            .mapNotNull { contactModel -> contactModel.data }
            .filter { contactModelData -> contactModelData.activityState != IdentityState.INVALID }
            .map(ContactModelData::identity)
            .toSet()

        // If forward security is supported, run the FS Refresh Steps
        // TODO(ANDR-2519): Remove the check when md allows fs (but keep running the FS Refresh Steps)
        if (multiDeviceManager.isMdDisabledOrSupportsFs) {
            taskManager.schedule(FSRefreshStepsTask(solicitedContactIdentities))
        }

        // Send a contact-request-profile-picture message to each solicited contact
        solicitedContactIdentities.forEach { identity ->
            taskManager.schedule(
                OutgoingContactRequestProfilePictureTask(
                    toIdentity = identity,
                ),
            )
        }

        // Send a group sync or group sync request for groups where the user is the creator or a member
        groupModels.forEach { groupModel ->
            if (groupModel.isCreator()) {
                groupFlowDispatcher.runGroupResyncFlow(groupModel)
            } else if (groupModel.isMember()) {
                groupService.scheduleSyncRequest(
                    groupModel.groupIdentity.creatorIdentity,
                    GroupId(groupModel.groupIdentity.groupId),
                )
            }
        }

        logger.info("Application setup steps completed successfully")

        return true
    }
}
