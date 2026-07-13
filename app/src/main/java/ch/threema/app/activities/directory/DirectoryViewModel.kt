package ch.threema.app.activities.directory

import ch.threema.app.asynctasks.AddOrUpdateWorkContactBackgroundTask
import ch.threema.app.framework.BaseViewModel
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.IdentityProvider
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.api.work.WorkDirectoryContact
import ch.threema.domain.types.Identity
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("DirectoryViewModel")

class DirectoryViewModel(
    private val dispatcherProvider: DispatcherProvider,
    private val identityProvider: IdentityProvider,
    private val contactModelRepository: ContactModelRepository,
) : BaseViewModel<Unit, DirectoryScreenEvent>() {

    override suspend fun initialize() = Unit

    fun addContact(
        workDirectoryContact: WorkDirectoryContact,
        changedAdapterPosition: Int,
        openOnSuccess: Boolean,
    ) = runAction {
        logger.info("Add new work contact")
        val myIdentity = identityProvider.getIdentity()
            ?: run {
                logger.error("Can not add new work contact, as the user's identity is missing")
                emitEvent(DirectoryScreenEvent.Error)
                return@runAction
            }
        val contactAdded: Boolean = addContact(myIdentity, workDirectoryContact)
        emitEvent(
            if (contactAdded) {
                DirectoryScreenEvent.WorkContactAdded(
                    workDirectoryContact = workDirectoryContact,
                    changedAdapterPosition = changedAdapterPosition,
                    openOnSuccess = openOnSuccess,
                )
            } else {
                DirectoryScreenEvent.Error
            },
        )
    }

    private suspend fun addContact(myIdentity: Identity, workDirectoryContact: WorkDirectoryContact): Boolean {
        val createContactTask = AddOrUpdateWorkContactBackgroundTask(
            workContact = workDirectoryContact,
            myIdentity = myIdentity.value,
            contactModelRepository = contactModelRepository,
            pendingUpdateContactWorkLastFullSyncAt = { _, _ ->
                // Nothing to do, as this contact is not updated, but created
            },
            pendingUpdateContactAvailabilityStatus = { _, _ ->
                // Nothing to do, as this contact is not updated, but created
            },
        )
        val contactModel = withContext(dispatcherProvider.io) {
            createContactTask.runSynchronously()
        }
        return contactModel != null
    }
}
