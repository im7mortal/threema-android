package ch.threema.app.backupcenter

import ch.threema.app.framework.BaseViewModel
import ch.threema.common.DispatcherProvider
import ch.threema.common.kiloBytes
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class LocalDataBackupViewModel(
    private val dispatcherProvider: DispatcherProvider,
) : BaseViewModel<LocalDataBackupViewState, Unit>() {
    override suspend fun initialize(): LocalDataBackupViewState {
        // TODO(ANDR-4880): Mocked data for now, replace with actual values once they are available
        delay(500.milliseconds)
        return withContext(dispatcherProvider.io) {
            LocalDataBackupViewState(
                lastBackupData = LocalDataBackupViewState.LastBackupData(
                    time = Instant.ofEpochMilli(1_778_663_772_000L),
                    size = Random.nextInt(250, 2_000_000).kiloBytes,
                    mediaIncluded = false,
                    location = "Threema Backups/BackupFile.dat",
                ),
            )
        }
    }
}
