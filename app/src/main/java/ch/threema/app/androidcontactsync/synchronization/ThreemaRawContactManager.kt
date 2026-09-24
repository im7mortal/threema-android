package ch.threema.app.androidcontactsync.synchronization

import android.Manifest
import android.content.ContentProviderOperation
import android.provider.ContactsContract
import androidx.annotation.RequiresPermission
import ch.threema.app.androidcontactsync.read.ThreemaRawContactReader
import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.androidcontactsync.types.ThreemaRawContact
import ch.threema.app.androidcontactsync.write.ThreemaRawContactWriter
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.utils.AndroidContactUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.ContactUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.logging.logAndReportError

private val logger = getThreemaLogger("ThreemaRawContactManager")

class ThreemaRawContactManager(
    private val blockedIdentitiesService: BlockedIdentitiesService,
    private val threemaRawContactReader: ThreemaRawContactReader,
    private val threemaRawContactWriter: ThreemaRawContactWriter,
) {
    @RequiresPermission(allOf = [Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS])
    suspend fun updateThreemaRawContacts(androidContactMatches: Map<AndroidContact, AndroidContactMatcher.MatchedIdentity>) {
        // Note that we will remove all valid existing raw contacts from this map so that only invalid and stray raw contacts are left in the end.
        val threemaRawContactsToDelete = readAllThreemaRawContacts()?.toMutableSet()
        if (threemaRawContactsToDelete == null) {
            logger.error("Could not get raw contacts")
            return
        }
        logger.info("Read {} existing threema raw contacts", threemaRawContactsToDelete.size)

        val threemaRawContactsToCreate = mutableMapOf<AndroidContact, AndroidContactMatcher.MatchedIdentity>()

        androidContactMatches.forEach { (androidContact, matchedIdentity) ->
            val identity = matchedIdentity.identity
            val threemaRawContacts = threemaRawContactsToDelete.filter { threemaRawContact ->
                threemaRawContact.identityString == identity.value
            }.toSet()
            if (threemaRawContacts.isNotEmpty() && threemaRawContacts.allBelongToContactId(androidContact.lookupInfo.contactId)) {
                logger.info("Threema raw contact(s) for identity {} are up to date", identity)
                // Note that we remove the raw contacts from the list, so that in the end only stray raw contacts are left.
                threemaRawContactsToDelete.removeAll(threemaRawContacts)
            } else {
                if (threemaRawContacts.isEmpty()) {
                    logger.info("No threema raw contacts exist yet for identity {}", identity)
                } else {
                    logger.warn(
                        "Not all of the {} existing raw contacts for identity {} belong to the correct contact id",
                        threemaRawContacts.size,
                        identity,
                    )
                }

                threemaRawContactsToCreate[androidContact] = matchedIdentity
            }
        }

        createThreemaRawContacts(threemaRawContactsToCreate)
        deleteThreemaRawContacts(threemaRawContactsToDelete)
    }

    private suspend fun readAllThreemaRawContacts(): Set<ThreemaRawContact>? = try {
        threemaRawContactReader.readAllThreemaRawContacts()
    } catch (e: ThreemaRawContactReader.ThreemaRawContactReadException) {
        logger.error("Could not read threema raw contacts", e)
        null
    }

    @RequiresPermission(allOf = [Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS])
    private fun createThreemaRawContacts(threemaRawContactsToCreate: Map<AndroidContact, AndroidContactMatcher.MatchedIdentity>) {
        val contentProviderOperations = ArrayList<ContentProviderOperation>()

        threemaRawContactsToCreate.forEach { (androidContact, matchedIdentity) ->
            val identity = matchedIdentity.identity
            val supportsVoiceCalls = ContactUtil.canReceiveVoipMessages(identity.value, this.blockedIdentitiesService) && ConfigUtils.isCallsEnabled()
            val rawContactId = androidContact.findMatchedRawContact(matchedIdentity.matchSource).rawContactId

            AndroidContactUtil.getInstance().createThreemaRawContact(
                contentProviderOperations,
                rawContactId.id.toLong(),
                identity.value,
                supportsVoiceCalls,
            )
        }

        if (!contentProviderOperations.isEmpty()) {
            try {
                ConfigUtils.applyToContentResolverInBatches(
                    ContactsContract.AUTHORITY,
                    contentProviderOperations,
                )
            } catch (e: Exception) {
                logger.logAndReportError("Error during raw contact creation", e)
            }
        }
    }

    private fun deleteThreemaRawContacts(threemaRawContactsToDelete: Set<ThreemaRawContact>) {
        if (threemaRawContactsToDelete.isNotEmpty()) {
            logger.info("Deleting {} raw contacts", threemaRawContactsToDelete.size)
            threemaRawContactWriter.deleteThreemaRawContacts(threemaRawContactsToDelete)
        }
    }

    private fun Collection<ThreemaRawContact>.allBelongToContactId(contactId: ContactId): Boolean =
        all { threemaRawContact -> threemaRawContact.contactId == contactId }

    /**
     * Finds the raw contact holding the phone number or email address that provided the match. If several raw contacts hold the exact same value
     * (e.g. a duplicated phone number), the one with the lowest raw contact id is used, for determinism.
     */
    private fun AndroidContact.findMatchedRawContact(matchSource: AndroidContactMatcher.MatchSource): RawContact {
        val candidates = when (matchSource) {
            is AndroidContactMatcher.MatchSource.Phone -> rawContacts.filter { matchSource.phoneNumber in it.phoneNumbers }
            is AndroidContactMatcher.MatchSource.Email -> rawContacts.filter { matchSource.emailAddress in it.emailAddresses }
        }
        return candidates.minByOrNull(RawContact::rawContactId)
            ?: error("No raw contact holds the matched value from $matchSource")
    }
}
