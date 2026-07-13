package ch.threema.app.backupcenter

import androidx.compose.runtime.Immutable
import ch.threema.common.ByteSize
import java.time.Instant

@Immutable
data class LocalDataBackupViewState(
    val lastBackupData: LastBackupData,
) {
    @Immutable
    data class LastBackupData(
        val time: Instant,
        val size: ByteSize,
        val mediaIncluded: Boolean,
        val location: String,
    )
}
