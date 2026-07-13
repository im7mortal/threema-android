package ch.threema.app.asynctasks

import ch.threema.app.protocolsteps.ValidContactsLookupSteps
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.data.datatypes.PredefinedContact
import ch.threema.data.datatypes.PredefinedContact.Companion.THREEMA_SUPPORT_IDENTITY
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.AcquaintanceLevel

/**
 * This class can be used to add the special `*SUPPORT` contact.
 * If this contact is already added, it will be updated.
 *
 * @see SendToSupportBackgroundTask
 */
class AddOrUpdateSupportContactBackgroundTask(
    validContactsLookupSteps: ValidContactsLookupSteps,
    contactModelRepository: ContactModelRepository,
    appRestrictions: AppRestrictions,
) : AddOrUpdateContactBackgroundTask<ContactResult>(
    identity = THREEMA_SUPPORT_IDENTITY,
    acquaintanceLevel = AcquaintanceLevel.DIRECT,
    validContactsLookupSteps = validContactsLookupSteps,
    contactModelRepository = contactModelRepository,
    addContactRestrictionPolicy = AddContactRestrictionPolicy.IGNORE,
    appRestrictions = appRestrictions,
    expectedPublicKey = PredefinedContact.supportContact?.publicKey,
) {
    override fun onContactResult(result: ContactResult): ContactResult = result
}
