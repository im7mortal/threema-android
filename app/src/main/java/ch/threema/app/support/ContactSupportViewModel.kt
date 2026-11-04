package ch.threema.app.support

import ch.threema.app.asynctasks.AddOrUpdateSupportContactBackgroundTask
import ch.threema.app.asynctasks.ContactAvailable
import ch.threema.app.asynctasks.ContactResult
import ch.threema.app.asynctasks.Failed
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.protocolsteps.ValidContactsLookupSteps
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.repositories.ContactModelRepository
import kotlinx.coroutines.withContext

class ContactSupportViewModel(
    private val validContactsLookupSteps: ValidContactsLookupSteps,
    private val appRestrictions: AppRestrictions,
    private val contactModelRepository: ContactModelRepository,
    private val dispatcherProvider: DispatcherProvider,
) : BaseViewModel<Unit, ContactSupportViewEvent>() {
    override suspend fun initialize() {
        // Failed case needs to be checked first, as the result can be both Failed and ContactAvailable at the same time
        // See the definition of `LocalPublicKeyMismatch`
        val event = when (val contactResult: ContactResult = addOrUpdateSupportContact()) {
            is Failed -> ContactSupportViewEvent.OnSupportContactUnavailable(contactResult.message)
            is ContactAvailable -> ContactSupportViewEvent.OnSupportContactAvailable(
                conversationId = ContactConversationId(
                    identity = contactResult.contactModel.identity,
                ),
            )
        }
        emitEvent(event)
    }

    private suspend fun addOrUpdateSupportContact(): ContactResult = withContext(dispatcherProvider.worker) {
        AddOrUpdateSupportContactBackgroundTask(
            validContactsLookupSteps = validContactsLookupSteps,
            contactModelRepository = contactModelRepository,
            appRestrictions = appRestrictions,
        ).runSynchronously()
    }
}
