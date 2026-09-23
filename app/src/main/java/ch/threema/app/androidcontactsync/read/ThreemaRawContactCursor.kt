package ch.threema.app.androidcontactsync.read

import android.accounts.Account
import android.content.ContentResolver
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import ch.threema.app.androidcontactsync.types.ContactId
import ch.threema.app.androidcontactsync.types.RawContactId
import ch.threema.app.androidcontactsync.types.ThreemaRawContact
import ch.threema.domain.types.IdentityString

class ThreemaRawContactCursor private constructor(
    private val cursor: Cursor,
    private val rawContactIdIndex: Int,
    private val contactIdIndex: Int,
    private val identityStringIndex: Int,
) : AutoCloseable {
    /**
     * Move the cursor to the next row.
     *
     * @return true if the move succeeded and false if the cursor is already at the last position
     */
    fun moveToNext(): Boolean = cursor.moveToNext()

    /**
     * Get the [ThreemaRawContact] at the current position of the cursor.
     *
     * @throws [CursorException] in case not all required fields can be read
     */
    fun getThreemaRawContact(): ThreemaRawContact =
        ThreemaRawContact(
            rawContactId = getRawContactId(),
            contactId = getContactId(),
            identityString = getIdentityString(),
        )

    private fun getRawContactId(): RawContactId =
        cursor.getLongOrNull(rawContactIdIndex)?.let(RawContactId::fromLong)
            ?: throw CursorException("Could not read raw contact id")

    private fun getContactId(): ContactId =
        cursor.getLongOrNull(contactIdIndex)?.let(ContactId::fromLong)
            ?: throw CursorException("Could not read contact id")

    private fun getIdentityString(): IdentityString? =
        cursor.getStringOrNull(identityStringIndex)

    override fun close() {
        cursor.close()
    }

    companion object {
        private val projection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.RawContacts.SYNC1,
        )

        /**
         * Create a [ThreemaRawContactCursor].
         *
         * @throws [CursorCreateException] if the cursor could not be created
         * @throws [SecurityException] if there is no permission to read the contacts
         */
        @Throws(CursorCreateException::class, SecurityException::class)
        fun createRawContactCursor(contentResolver: ContentResolver, account: Account): ThreemaRawContactCursor {
            val uri = ContactsContract.RawContacts.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                .build()

            val cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                null,
            ) ?: throw CursorCreateException(message = "Created cursor is null")

            return fromCursor(cursor)
        }

        private fun fromCursor(cursor: Cursor): ThreemaRawContactCursor = try {
            with(cursor) {
                ThreemaRawContactCursor(
                    cursor = cursor,
                    rawContactIdIndex = getColumnIndexOrThrow(ContactsContract.RawContacts._ID),
                    contactIdIndex = getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID),
                    identityStringIndex = getColumnIndexOrThrow(ContactsContract.RawContacts.SYNC1),
                )
            }
        } catch (e: IllegalArgumentException) {
            throw CursorCreateException(cause = e)
        }
    }

    class CursorException(message: String) : Exception(message)
}
