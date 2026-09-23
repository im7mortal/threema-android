package ch.threema.app.androidcontactsync.read

import android.content.Context
import ch.threema.app.androidcontactsync.types.LookupInfo
import ch.threema.app.services.UserService

class RawContactCursorProvider(
    private val appContext: Context,
    private val userService: UserService,
) {
    /**
     * Create a raw contact cursor that reads all raw contacts.
     *
     * @throws [CursorCreateException] if the cursor could not be created
     * @throws [SecurityException] if there is no permission to read the contacts
     */
    @Throws(CursorCreateException::class, SecurityException::class)
    fun getRawContactCursor(): RawContactCursor =
        RawContactCursor.createRawContactCursor(
            contentResolver = appContext.contentResolver,
        )

    /**
     * Create a raw contact cursor that reads the contact with the given lookup information.
     *
     * @throws [CursorCreateException] if the cursor could not be created
     * @throws [SecurityException] if there is no permission to read the contacts
     */
    @Throws(CursorCreateException::class, SecurityException::class)
    fun getRawContactCursorForLookup(lookupInfo: LookupInfo) =
        RawContactCursor.createRawContactCursorForLookup(
            contentResolver = appContext.contentResolver,
            lookupInfo = lookupInfo,
        )

    /**
     * Create a [ThreemaRawContactCursor] that reads all Threema contacts.
     *
     * @return null in case there is no account
     *
     * @throws [CursorCreateException] if the cursor could not be created
     * @throws [SecurityException] if there is no permission to read the contacts
     */
    @Throws(CursorCreateException::class, SecurityException::class)
    fun getThreemaRawContactCursor(): ThreemaRawContactCursor? =
        ThreemaRawContactCursor.createRawContactCursor(
            contentResolver = appContext.contentResolver,
            account = userService.account ?: return null,
        )
}
