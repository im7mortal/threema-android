package ch.threema.app.androidcontactsync.read

import ch.threema.app.androidcontactsync.types.ThreemaRawContact
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import kotlin.jvm.Throws
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ThreemaRawContactReader")

class ThreemaRawContactReader(
    private val dispatcherProvider: DispatcherProvider,
    private val rawContactCursorProvider: RawContactCursorProvider,
) {

    /**
     * Read all threema raw contacts. If there is no account and therefore also no threema raw contacts, an empty set is returned.
     *
     * @throws ThreemaRawContactReadException if reading the raw contacts fails
     */
    @Throws(ThreemaRawContactReadException::class)
    suspend fun readAllThreemaRawContacts(): Set<ThreemaRawContact> = withContext(dispatcherProvider.io) {
        try {
            val threemaRawContacts = mutableSetOf<ThreemaRawContact>()
            rawContactCursorProvider.getThreemaRawContactCursor()?.use { cursor ->
                while (cursor.moveToNext()) {
                    threemaRawContacts.add(cursor.getThreemaRawContact())
                }
            }
            logger.info("Read {} threema raw contacts", threemaRawContacts.size)
            threemaRawContacts
        } catch (e: Exception) {
            throw ThreemaRawContactReadException(cause = e)
        }
    }

    class ThreemaRawContactReadException(cause: Throwable?) : Throwable(cause)
}
