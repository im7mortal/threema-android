package ch.threema.data.datatypes

import android.net.Uri
import android.provider.ContactsContract

/**
 * This contains the required information to look up a contact in the android contacts.
 */
data class AndroidContactLookupInfo(
    /**
     * The lookup key used to look up a contact.
     */
    val lookupKey: String,

    /**
     * The contact id is only used to optimize the performance when querying the contact.
     */
    val contactId: Long?,
) {
    /**
     * Get the contact uri that can be used to access the android contact.
     *
     * TODO(ANDR-4984): Evaluate if omitting the contact id has any effect on the contact confusion.
     * Note: Currently, we do not make use of the contact id. This is an experiment whether it helps for contact confusion.
     */
    fun getContactUri(): Uri = Uri.withAppendedPath(
        ContactsContract.Contacts.CONTENT_LOOKUP_URI,
        lookupKey,
    )
}
