package ch.threema.app.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import ch.threema.android.checkGrantedPermission
import ch.threema.android.firstOrNull
import ch.threema.android.isContentUri
import ch.threema.app.R
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.takeUnlessEmpty
import ch.threema.domain.types.IdentityString
import ch.threema.logging.logAndReportError

private val logger = getThreemaLogger("ContactLookupUtil")

object ContactLookupUtil {
    @JvmStatic
    fun getLookupKeyFromPhoneNumber(context: Context, phoneNumber: String?): String? =
        context.contentResolver.query(
            /* uri = */
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber)),
            /* projection = */
            arrayOf(ContactsContract.PhoneLookup.LOOKUP_KEY),
            /* selection = */
            null,
            /* selectionArgs = */
            null,
            /* sortOrder = */
            null,
        )
            ?.firstOrNull { cursor ->
                cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                    .takeUnless { it == -1 }
                    ?.let { columnIndex ->
                        cursor.getString(columnIndex)
                    }
            }
            ?.takeUnlessEmpty()

    @JvmStatic
    fun getIdentityFromContactViewIntent(context: Context, intent: Intent): IdentityString? {
        if (!intent.isContactViewIntent(context) || !context.checkGrantedPermission(Manifest.permission.READ_CONTACTS)) {
            return null
        }
        return try {
            context.contentResolver.query(
                /* uri = */
                intent.data ?: return null,
                /* projection = */
                arrayOf(ContactsContract.Data.DATA1),
                /* selection = */
                null,
                /* selectionArgs = */
                null,
                /* sortOrder = */
                null,
            )
                ?.firstOrNull { cursor ->
                    cursor.getColumnIndex(ContactsContract.Data.DATA1)
                        .takeUnless { it == -1 }
                        ?.let { columnIndex ->
                            cursor.getString(columnIndex)
                        }
                }
        } catch (e: Exception) {
            logger.logAndReportError("Failed to get contact identity from view intent", e)
            null
        }
    }

    private fun Intent.isContactViewIntent(context: Context): Boolean =
        action == Intent.ACTION_VIEW &&
            type == context.getString(R.string.contacts_mime_type) &&
            data?.isContentUri() == true
}
