package ch.threema.app.files

import android.content.Context
import ch.threema.common.deleteSecurely
import java.io.File

/**
 * The file is first moved to a different directory, such that the delete operation appears atomic to the caller, meaning that the
 * file will no longer seem to exist even if the secure-deletion fails partially. After the move, the file is overwritten with zeroes
 * and then deleted. The target directory into which the file is moved is guaranteed to eventually be cleaned up by [TempFilesCleanupWorker],
 * so even if the process partially fails, deletion will be attempted again at a later point.
 *
 * @see ch.threema.common.deleteSecurely
 */
fun File.deleteSecurely(context: Context) {
    deleteSecurely(File(context.cacheDir, "securely-deleted"))
}
