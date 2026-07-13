package ch.threema.app.asynctasks

import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import ch.threema.app.R
import ch.threema.app.protocolsteps.Contact
import ch.threema.app.protocolsteps.Init
import ch.threema.app.protocolsteps.Invalid
import ch.threema.app.protocolsteps.PredefinedContactPublicKeyMismatch
import ch.threema.app.protocolsteps.SpecialContact
import ch.threema.app.protocolsteps.UserContact
import ch.threema.app.protocolsteps.ValidContactsLookupSteps
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.utils.executor.BackgroundTask
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.toContactModelData
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.taskmanager.ProtocolException
import ch.threema.domain.types.IdentityString
import kotlinx.coroutines.runBlocking

private val logger = getThreemaLogger("AddOrUpdateContactBackgroundTask")

/**
 * This background task should be used if a new identity should be added to the contacts. The task
 * will fetch the public key, identity type, activity state, and feature mask from the server.
 *
 * If [expectedPublicKey] is set, this background task verifies that the public key matches before
 * adding the new contact. If the contact already exists, it checks that the public key matches and
 * returns [Failed] if it doesn't match.
 *
 * When adding new contacts, the list of predefined contacts is checked. If a match is found, the
 * public key will be verified, [RemotePublicKeyMismatch] will be returned on mismatch. Additionally,
 * the contact will be marked as fully verified, and the predefined nickname will be used.
 *
 * This task also updates the contact if it already exists. This includes changing the acquaintance
 * level to [acquaintanceLevel] and the verification level to "fully verified" if [expectedPublicKey]
 * is provided and matches.
 *
 * Note that this task can be overridden and the behavior can be adjusted by overwriting [onBefore],
 * [onContactResult], and [onFinished]. For tasks that do not need to perform any additional
 * background work, the [BasicAddOrUpdateContactBackgroundTask] can be used.
 */
abstract class AddOrUpdateContactBackgroundTask<T>(
    private val identity: IdentityString,
    private val acquaintanceLevel: AcquaintanceLevel,
    private val validContactsLookupSteps: ValidContactsLookupSteps,
    private val contactModelRepository: ContactModelRepository,
    private val addContactRestrictionPolicy: AddContactRestrictionPolicy,
    private val appRestrictions: AppRestrictions,
    private val expectedPublicKey: ByteArray? = null,
) : BackgroundTask<T> {
    /**
     * Run this task synchronously on the same thread. Note that this performs network communication
     * and must not be run on the main thread.
     */
    @WorkerThread
    fun runSynchronously(): T {
        runBefore()

        val result = runInBackground()

        runAfter(result)

        return result
    }

    /**
     * Do not call this method directly. This should only be called by the background executor.
     */
    final override fun runBefore() {
        onBefore()
    }

    /**
     * Do not call this method directly. This should only be called by the background executor. If
     * the task should be run on the same thread, use [runSynchronously].
     */
    final override fun runInBackground(): T {
        val result = checkAndAddNewContact()

        return onContactResult(result)
    }

    /**
     * Do not call this method directly. This should only be called by the background executor.
     */
    final override fun runAfter(result: T) {
        onFinished(result)
    }

    /**
     * This will be run before the contact is being fetched from the server.
     */
    open fun onBefore() = Unit

    /**
     * As soon as the contact has been added or an error occurred, this method is run with the
     * provided result. Note that this method is run on the executor's background thread. The result
     * of it will be passed to [onFinished].
     */
    abstract fun onContactResult(result: ContactResult): T

    /**
     * This method is run on the UI thread after [onContactResult] has been executed. Override this
     * method for making UI changes after the contact has been added and processed.
     */
    open fun onFinished(result: T) = Unit

    private fun checkAndAddNewContact(): ContactResult =
        try {
            when (val contactOrInit = validContactsLookupSteps.run(identity)) {
                is Contact -> updateContact(contactOrInit.contactModel)
                is Init -> addNewContact(contactOrInit.contactModelData)
                is Invalid -> InvalidThreemaId
                is SpecialContact -> addNewContact(contactOrInit.cachedContact.toContactModelData())
                is UserContact -> UserIdentity
                is PredefinedContactPublicKeyMismatch -> RemotePublicKeyMismatch
            }
        } catch (e: ProtocolException) {
            logger.error("Could not fetch contact", e)
            ConnectionError
        }

    private fun addNewContact(contactModelData: ContactModelData): ContactResult {
        // Only proceed if adding contacts is allowed
        if (addContactRestrictionPolicy == AddContactRestrictionPolicy.CHECK && appRestrictions.isAddContactDisabled()) {
            return PolicyViolation
        }

        // Determine verification level
        val verificationLevel = if (expectedPublicKey != null) {
            if (expectedPublicKey.contentEquals(contactModelData.publicKey)) {
                VerificationLevel.FULLY_VERIFIED
            } else {
                logger.error("Warning: Fetched contact public key for {} does not match expected public key", identity)
                return RemotePublicKeyMismatch
            }
        } else {
            contactModelData.verificationLevel
        }

        return runBlocking {
            try {
                val contactModel = contactModelRepository.createFromLocal(
                    contactModelData.copy(
                        verificationLevel = verificationLevel,
                        acquaintanceLevel = acquaintanceLevel,
                    ),
                )
                ContactCreated(contactModel)
            } catch (e: ContactCreateException) {
                logger.error("Could not insert new contact", e)

                val existingContact = contactModelRepository.getByIdentity(identity)
                if (existingContact != null) {
                    ContactExists(existingContact)
                } else {
                    GenericFailure
                }
            }
        }
    }

    private fun updateContact(contactModel: ContactModel): ContactResult {
        val currentContactModelData = contactModel.data ?: run {
            logger.error("Contact model data is null while updating contact")
            return GenericFailure
        }

        var verificationLevelChanged = false
        var contactVerifiedAgain = false
        var acquaintanceLevelChanged = false

        if (expectedPublicKey != null) {
            if (expectedPublicKey.contentEquals(currentContactModelData.publicKey)) {
                if (currentContactModelData.verificationLevel != VerificationLevel.FULLY_VERIFIED) {
                    contactModel.setVerificationLevelFromLocal(VerificationLevel.FULLY_VERIFIED)
                    verificationLevelChanged = true
                } else {
                    contactVerifiedAgain = true
                }
            } else {
                return LocalPublicKeyMismatch(contactModel)
            }
        }

        if (currentContactModelData.acquaintanceLevel != acquaintanceLevel) {
            contactModel.setAcquaintanceLevelFromLocal(acquaintanceLevel)
            acquaintanceLevelChanged = true
        }

        return when {
            acquaintanceLevelChanged || verificationLevelChanged -> ContactModified(
                contactModel,
                acquaintanceLevelChanged,
                verificationLevelChanged,
            )

            contactVerifiedAgain -> AlreadyVerified(contactModel)
            else -> ContactExists(contactModel)
        }
    }
}

/**
 * Use this task for creating a new contact when no additional background work is required after the
 * contact has been created. The [ContactResult] is directly passed to [onFinished]. See
 * [AddOrUpdateContactBackgroundTask] for more information about contact creation.
 */
open class BasicAddOrUpdateContactBackgroundTask(
    identity: IdentityString,
    acquaintanceLevel: AcquaintanceLevel,
    validContactsLookupSteps: ValidContactsLookupSteps,
    contactModelRepository: ContactModelRepository,
    addContactRestrictionPolicy: AddContactRestrictionPolicy,
    appRestrictions: AppRestrictions,
    expectedPublicKey: ByteArray? = null,
) : AddOrUpdateContactBackgroundTask<ContactResult>(
    identity = identity,
    acquaintanceLevel = acquaintanceLevel,
    validContactsLookupSteps = validContactsLookupSteps,
    contactModelRepository = contactModelRepository,
    addContactRestrictionPolicy = addContactRestrictionPolicy,
    appRestrictions = appRestrictions,
    expectedPublicKey = expectedPublicKey,
) {
    final override fun onContactResult(result: ContactResult): ContactResult = result
}

/**
 * This is used to define whether the contact add restriction should be respected or if a contact
 * should be added anyways.
 */
enum class AddContactRestrictionPolicy {
    /**
     * The add contact restriction must be followed and a contact won't be added if this is
     * prohibited. In this case the result will be of the type [PolicyViolation].
     */
    CHECK,

    /**
     * The add contact restriction won't be respected and the contact will be added anyways. Note
     * that this must only be used in cases where adding the contact is not triggered by the user.
     */
    IGNORE,
}

/**
 * The result type of adding or updating a contact. The result is either [ContactAvailable] or
 * [Failed] or both.
 */
sealed interface ContactResult

/**
 * The contact is now available. This is the case when the contact has successfully been added or if
 * the contact already existed.
 */
sealed interface ContactAvailable : ContactResult {
    val contactModel: ContactModel
}

/**
 * Adding or updating the contact failed. Note that this does not necessarily mean that the contact
 * does not exist. E.g., this result can indicate that the provided public key does not match.
 */
sealed class Failed(@StringRes @JvmField val message: Int) : ContactResult

/**
 * The contact has been newly created. The new contact is provided.
 */
data class ContactCreated(override val contactModel: ContactModel) : ContactAvailable

/**
 * The contact already existed and has now been updated.
 */
data class ContactModified(
    /**
     * The updated contact model.
     */
    override val contactModel: ContactModel,
    /**
     * If true, the acquaintance level changed.
     */
    val acquaintanceLevelChanged: Boolean,
    /**
     * If true, the verification level has changed to [VerificationLevel.FULLY_VERIFIED].
     */
    val verificationLevelChanged: Boolean,
) : ContactAvailable

/**
 * The contact already exists. This is only returned, if no expected public key is given and the
 * contact already exists. This means, that neither the verification level nor the acquaintance
 * level did change.
 */
data class ContactExists(override val contactModel: ContactModel) : ContactAvailable

/**
 * The contact already exists and has been fully verified before.
 */
data class AlreadyVerified(override val contactModel: ContactModel) : ContactAvailable

/**
 * The locally stored public key of the contact does not match the provided public key.
 */
class LocalPublicKeyMismatch(override val contactModel: ContactModel) : Failed(R.string.id_mismatch), ContactAvailable

/**
 * The contact did not exist locally and the fetched public key from the threema server does not
 * match the provided public key. This also means, that the contact is not available locally.
 */
data object RemotePublicKeyMismatch : Failed(R.string.id_mismatch)

/**
 * The provided identity is invalid and the contact could not be added.
 */
data object InvalidThreemaId : Failed(R.string.invalid_threema_id)

/**
 * The provided identity is the same as the user's identity and therefore the contact could not be
 * added.
 */
data object UserIdentity : Failed(R.string.add_contact_failed)

/**
 * The contact could not be added due to a connection error.
 */
data object ConnectionError : Failed(R.string.connection_error)

/**
 * A general error occurred while adding the contact.
 */
data object GenericFailure : Failed(R.string.add_contact_failed)

/**
 * The contact could not be added since adding contacts is restricted.
 */
data object PolicyViolation : Failed(R.string.disabled_by_policy_short)
