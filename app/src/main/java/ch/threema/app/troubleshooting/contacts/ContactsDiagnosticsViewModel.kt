package ch.threema.app.troubleshooting.contacts

import android.content.Context
import ch.threema.android.showToast
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.crypto.NaCl
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.isAllZeroes
import ch.threema.common.takeUnlessEmpty
import ch.threema.common.toHexString
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.Identity
import ch.threema.domain.types.toIdentityOrNull
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.exists
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.ContactModel as ContactModelOld
import ch.threema.storage.models.DistributionListMemberModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.group.GroupMemberModel
import ch.threema.storage.runTransaction
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ContactsDiagnosticsViewModel")

/**
 * This view model serves a screen which allows troubleshooting uncommon problems with contacts.
 * These problems are rare to the point where we don't expect them to ever occur. In case they do occur, we want to give the user
 * a way to produce a debug log for us, and possibly also a way to fix the problems directly.
 * The strings in this viewmodel were intentionally hardcoded to English, as we expect users to only reach this screen if explicitly
 * instructed to do so by Threema's support.
 */
class ContactsDiagnosticsViewModel(
    private val appContext: Context,
    private val contactModelRepository: ContactModelRepository,
    private val preferenceService: PreferenceService,
    private val databaseProvider: DatabaseProvider,
    private val multiDeviceManager: MultiDeviceManager,
    private val dispatcherProvider: DispatcherProvider,
) : BaseViewModel<ContactsDiagnosticsViewState, Unit>() {
    override suspend fun initialize() = ContactsDiagnosticsViewState(
        contactsWithProblems = getContactsWithProblems(),
    )

    private suspend fun getContactsWithProblems(): List<ContactsDiagnosticsViewState.ContactUiModel> = withContext(dispatcherProvider.io) {
        val allContacts = contactModelRepository.getAll()
        logger.info("Analyzing {} contacts", allContacts.size)

        val publicKeyToIdentities = groupIdentitiesByPublicKey(allContacts)
        val contactsWithProblems = allContacts.mapNotNull { contact ->
            val publicKey = contact.data?.publicKey
            when {
                publicKey == null -> "Public key is null"
                publicKey.size != NaCl.PUBLIC_KEY_BYTES -> {
                    "Public key has wrong size (${publicKey.size})"
                }
                publicKey.isAllZeroes() -> {
                    "Public key is all zeroes"
                }
                else -> {
                    publicKeyToIdentities[PublicKey(publicKey)]
                        ?.minus(Identity(contact.identity))
                        ?.takeUnlessEmpty()
                        ?.joinToString(
                            prefix = "Has same public key as ",
                            postfix = " (${publicKey.toHexString()})",
                        )
                }
            }
                ?.let { problem ->
                    contact.toUiModel(problem)
                }
        }
        if (contactsWithProblems.isNotEmpty()) {
            logger.warn("Found {} contacts with problems", contactsWithProblems.size)
            contactsWithProblems.forEach { contact ->
                logger.info("- ${contact.identity}: {}", contact.problem)
            }
        } else {
            logger.info("No problems found")
        }

        contactsWithProblems
    }

    private data class PublicKey(val hexEncoded: String) {
        constructor(bytes: ByteArray) : this(bytes.toHexString())
    }

    private fun groupIdentitiesByPublicKey(contacts: List<ContactModel>): Map<PublicKey, List<Identity>> =
        contacts.mapNotNull { contact -> contact.data }
            .groupBy { contactData -> PublicKey(contactData.publicKey) }
            .mapValues { (_, contactsData) -> contactsData.mapNotNull { contactData -> contactData.identity.toIdentityOrNull() } }

    private fun ContactModel.toUiModel(problem: String) = ContactsDiagnosticsViewState.ContactUiModel(
        identity = Identity(identity),
        name = data?.getDisplayName(preferenceService.getContactNameFormat()),
        problem = problem,
    )

    fun onClickFixProblems() = runAction {
        try {
            updateViewState { copy(fixInProgress = true) }

            if (multiDeviceManager.isMultiDeviceActive) {
                logger.info("Aborting fix due to MD")
                appContext.showToast("Cannot fix problems while Multi-Device is active")
                return@runAction
            }

            val problematicIdentities = currentViewState.contactsWithProblems.map { it.identity }
            val deletedContacts = tryToDeleteContacts(problematicIdentities)
            if (deletedContacts > 0) {
                appContext.showToast("$deletedContacts problematic contact(s) removed.")
                delay(3.seconds)
                appContext.showToast("The app needs to be restarted now.")
                delay(2.seconds)
                exitProcess(0)
            } else {
                appContext.showToast("Problems could not be fixed")
            }
        } finally {
            val contactsWithProblems = getContactsWithProblems()
            updateViewState {
                copy(
                    contactsWithProblems = contactsWithProblems,
                    fixInProgress = false,
                )
            }
        }
    }

    private suspend fun tryToDeleteContacts(problematicIdentities: List<Identity>) = withContext(dispatcherProvider.io) {
        var deletedContacts = 0
        problematicIdentities.forEach { identity ->
            val success = deleteIdentity(identity)
            if (success) {
                deletedContacts++
            }
        }
        deletedContacts
    }

    // The following code is dangerous in the sense that it makes changes directly to the database without going through a repository
    // or model factory. This hack is done deliberately as this code should not be called from anywhere else in the app, and we hope
    // that we can eventually remove it again entirely.
    private fun deleteIdentity(identity: Identity): Boolean =
        databaseProvider.writableDatabase.runTransaction {
            if (exists(MessageModel.TABLE, "${AbstractMessageModel.COLUMN_IDENTITY} = ?", arrayOf(identity.value))) {
                logger.info("Cannot delete contact {}, still has 1:1 messages", identity)
                return false
            }

            // Remove the contact from any groups.
            val numberOfGroups = delete(
                table = GroupMemberModel.TABLE,
                whereClause = "${GroupMemberModel.COLUMN_IDENTITY} = ?",
                whereArgs = arrayOf(identity.value),
            )
            logger.info("Removed contact {} from {} groups", identity, numberOfGroups)

            // Remove it also from distribution lists
            val numberOfDistributionLists = delete(
                table = DistributionListMemberModel.TABLE,
                whereClause = "${DistributionListMemberModel.COLUMN_IDENTITY} = ?",
                whereArgs = arrayOf(identity.value),
            )
            logger.info("Removed contact {} from {} distribution lists", identity, numberOfDistributionLists)

            // Remove the contact itself
            delete(
                table = ContactModelOld.TABLE,
                whereClause = "${ContactModelOld.COLUMN_IDENTITY} = ?",
                whereArgs = arrayOf(identity.value),
            )
            logger.info("Removed contact {}", identity)

            true
        }
}
