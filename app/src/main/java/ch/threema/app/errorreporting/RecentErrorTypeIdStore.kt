package ch.threema.app.errorreporting

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import ch.threema.android.writeAtomically
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.common.TimeProvider
import ch.threema.common.deleteOrThrow
import ch.threema.common.minus
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.days

/**
 * Keeps track of which errors have been reported to Sentry recently, based on their [ErrorTypeId].
 * With this we can avoid asking the user too frequently for permission to send an error report to Sentry if one type of error
 * occurs multiple times.
 */
class RecentErrorTypeIdStore(
    appDirectoryProvider: AppDirectoryProvider,
    private val timeProvider: TimeProvider,
) {
    private val storeFile: File = File(appDirectoryProvider.appDataDirectory, FILE_NAME)

    @WorkerThread
    fun getRecentErrorTypeIds(): Set<ErrorTypeId> =
        getRecentErrorTypeIdsWithTimes().keys

    @WorkerThread
    private fun getRecentErrorTypeIdsWithTimes(): Map<ErrorTypeId, Instant> = try {
        buildMap {
            if (!storeFile.exists()) {
                return@buildMap
            }
            val threshold = timeProvider.get() - MAX_AGE
            storeFile.useLines { lines ->
                lines.forEach { line ->
                    val parts = line.trim().split(SEPARATOR)
                    val time = parts[1].trim().toLongOrNull()?.let { Instant.ofEpochMilli(it) }
                    if (time != null && time > threshold) {
                        put(ErrorTypeId(parts[0]), time)
                    }
                }
            }
        }
    } catch (_: Exception) {
        // If anything goes wrong while reading, we just drop the whole file
        storeFile.delete()
        emptyMap()
    }

    @WorkerThread
    fun recordAsRecent(errorTypeIds: Set<ErrorTypeId>) = synchronized(Companion) {
        val now = timeProvider.get()
        val newRecents = getRecentErrorTypeIdsWithTimes() + errorTypeIds.associateWith { now }
        storeFile.writeAtomically { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                newRecents.forEach { (errorTypeId, time) ->
                    writer.appendLine(serialize(errorTypeId, time))
                }
                writer.flush()
            }
        }
    }

    @WorkerThread
    fun clear() {
        storeFile.deleteOrThrow()
    }

    private fun serialize(errorTypeId: ErrorTypeId, time: Instant) =
        "$errorTypeId$SEPARATOR${time.toEpochMilli()}"

    companion object {
        private const val FILE_NAME = "recent_error_type_ids"
        private const val SEPARATOR = ","

        /**
         * Whenever the user is asked whether they want to send an error report, they will afterward not be asked again about that same
         * type of error for at least [MAX_AGE].
         */
        @VisibleForTesting
        val MAX_AGE = 7.days
    }
}
