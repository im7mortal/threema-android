package ch.threema.app.systemupdates.updates

import android.content.Context
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.deleteOrThrow
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion129")

/**
 * We changed the target directory for securely deleting files. This system update
 * will try to delete any files left over in the old target directory that still need to be deleted.
 */
class SystemUpdateToVersion129 : SystemUpdate {
    private val appContext: Context by inject()

    override fun run() {
        appContext.filesDir
            .listFiles { file -> file.name.startsWith("___DELETE_") }
            ?.forEach { file ->
                try {
                    file.deleteOrThrow()
                } catch (e: Exception) {
                    logger.error("Failed to delete file {}", file, e)
                }
            }
    }

    override val version = 129
}
