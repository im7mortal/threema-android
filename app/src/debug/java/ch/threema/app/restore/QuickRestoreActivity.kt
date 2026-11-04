package ch.threema.app.restore

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ch.threema.android.context
import ch.threema.android.showToast
import ch.threema.app.backuprestore.csv.RestoreService
import ch.threema.app.di.awaitAppFullyReady
import ch.threema.common.DispatcherProvider
import ch.threema.common.deleteOrThrow
import ch.threema.data.IdentityProvider
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

class QuickRestoreActivity : AppCompatActivity() {

    private val identityProvider: IdentityProvider by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val externalBackupFile = File(getExternalFilesDir(null), "dev-backup.zip")
        if (!externalBackupFile.exists()) {
            showToast("Backup file not found")
            finish()
            return
        }

        if (identityProvider.hasIdentity()) {
            externalBackupFile.deleteOrThrow()
            showToast("Already set up")
            finish()
            return
        }

        lifecycleScope.launch {
            val backupFile = File(context.cacheDir, "dev-backup.zip")
            withContext(dispatcherProvider.io) {
                context.cacheDir.mkdirs()
                externalBackupFile.copyTo(backupFile, overwrite = true)
                externalBackupFile.deleteOrThrow()
            }

            val backupPassword = intent.getStringExtra("password")!!

            awaitAppFullyReady()
            startRestore(backupFile, backupPassword)
            finish()
        }
    }

    private fun startRestore(backupFile: File, password: String) {
        val intent = RestoreService.createIntent(this, backupFile, password)
        ContextCompat.startForegroundService(this, intent)
        showToast("Starting restore")
    }
}
