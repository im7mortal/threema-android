package ch.threema.app.backupcenter

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
sealed interface CreateLocalDataBackupViewModelEvent {
    @Immutable
    data object PromptForBatteryOptimizationsDisabling : CreateLocalDataBackupViewModelEvent

    @Immutable
    data class PickDirectory(
        val previousUri: Uri?,
    ) : CreateLocalDataBackupViewModelEvent

    @Immutable
    data object Finish : CreateLocalDataBackupViewModelEvent
}
