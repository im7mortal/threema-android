package ch.threema.app.threemasafe

import androidx.compose.runtime.Immutable
import ch.threema.common.ByteSize
import java.time.Instant

@Immutable
data class ThreemaSafeViewState(
    val backUpButtonEnabled: Boolean,
    val lastBackupData: LastBackupData,
    val configuration: Configuration,
) {
    val changePasswordButtonVisible: Boolean = lastBackupData.status == LastBackupStatus.SUCCESS

    @Immutable
    data class LastBackupData(
        val time: Instant?,
        val size: ByteSize?,
        val status: LastBackupStatus,
    )

    enum class LastBackupStatus {
        SUCCESS,
        CONNECTION_ERROR,
        UPLOAD_FAILED,
        SIZE_LIMIT_EXCEEDED,
        GENERIC_ERROR,
    }

    @Immutable
    data class Configuration(
        val server: String?,
        val maxBackupSize: ByteSize,
        val backupRetentionDays: Int,
    )
}
