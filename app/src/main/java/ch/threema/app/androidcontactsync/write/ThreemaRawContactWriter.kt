package ch.threema.app.androidcontactsync.write

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import ch.threema.app.androidcontactsync.types.ThreemaRawContact
import ch.threema.app.services.UserService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.logging.logAndReportError

private val logger = getThreemaLogger("ThreemaRawContactWriter")

class ThreemaRawContactWriter(
    private val appContext: Context,
    private val userService: UserService,
) {
    /**
     * Delete all provided [ThreemaRawContact]s.
     *
     * @param threemaRawContacts Set of the [ThreemaRawContact]s to delete.
     */
    fun deleteThreemaRawContacts(threemaRawContacts: Set<ThreemaRawContact>) {
        logger.info("Request deletion of {} threema raw contacts", threemaRawContacts.size)
        if (!ConfigUtils.isPermissionGranted(appContext, Manifest.permission.WRITE_CONTACTS)) {
            logger.error("Cannot delete raw contacts if permission is not granted")
            return
        }

        val account = userService.account ?: run {
            logger.error("Cannot delete raw contacts if no account exists")
            return
        }

        if (threemaRawContacts.isEmpty()) {
            return
        }

        val contentProviderOperations = ArrayList<ContentProviderOperation>()

        threemaRawContacts
            .filter { threemaRawContact -> threemaRawContact.rawContactId.id != 0UL }
            .forEach { threemaRawContact ->
                try {
                    val builder = ContentProviderOperation.newDelete(
                        ContactsContract.RawContacts.CONTENT_URI
                            .buildUpon()
                            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                            .appendQueryParameter(ContactsContract.RawContacts.SYNC1, threemaRawContact.identityString)
                            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                            .build(),
                    )
                        .withSelection(ContactsContract.RawContacts._ID + " = ?", arrayOf(threemaRawContact.rawContactId.id.toString()))

                    contentProviderOperations.add(builder.build())
                } catch (e: Exception) {
                    logger.error("Failed to create new delete content provider operation", e)
                }
            }

        if (contentProviderOperations.isNotEmpty()) {
            try {
                ConfigUtils.applyToContentResolverInBatches(ContactsContract.AUTHORITY, contentProviderOperations)
            } catch (e: Exception) {
                logger.logAndReportError("Error during raw contact deletion", e)
            }
        }

        logger.info("Applied {} content provider operations", contentProviderOperations.size)
    }
}
