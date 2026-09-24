package ch.threema.app.androidcontactsync.types

import ch.threema.domain.types.IdentityString

/**
 * A raw contact that was created by threema.
 */
data class ThreemaRawContact(
    val rawContactId: RawContactId,
    val contactId: ContactId,
    val identityString: IdentityString?,
)
