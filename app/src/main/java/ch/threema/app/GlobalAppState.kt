package ch.threema.app

import ch.threema.app.backuprestore.csv.RestoreService
import java.time.Instant

class GlobalAppState {
    var lastLoggedIn: Instant? = null

    var isDeviceIdle: Boolean = false

    var isAppResumed: Boolean = false

    val isRestoreRunning: Boolean
        get() = RestoreService.isRunning()
}
