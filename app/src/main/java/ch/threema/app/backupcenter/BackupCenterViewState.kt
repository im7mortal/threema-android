package ch.threema.app.backupcenter

import androidx.compose.runtime.Immutable
import java.time.Instant

@Immutable
data class BackupCenterViewState(
    val infoBannerVisible: Boolean,
    val threemaSafeVisible: Boolean,
    val threemaSafeEnabled: Boolean,
    val lastBackupTime: Instant?,
)
