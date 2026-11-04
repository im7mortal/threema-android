package ch.threema.app.reset

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.work.WorkManager
import ch.threema.app.connection.ServerConnectionManager
import ch.threema.app.files.deleteSecurely
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.push.PushService
import ch.threema.app.services.PassphraseService
import ch.threema.app.services.UserService
import ch.threema.app.stores.EncryptedPreferenceStore
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.utils.ShortcutUtil
import ch.threema.app.webclient.manager.WebClientServiceManager
import ch.threema.app.webclient.services.instance.DisconnectContext
import ch.threema.base.crypto.NonceStore
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.clearDirectoryRecursively
import ch.threema.domain.stores.DHSessionStore
import ch.threema.localcrypto.MasterKeyManagerImpl
import ch.threema.storage.DatabaseNonceStore
import ch.threema.storage.DatabaseOpenHelper
import ch.threema.storage.DatabaseProviderImpl
import ch.threema.storage.SQLDHSessionStore
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("ResetAppTask")

class ResetAppTask(
    private val appContext: Context,
    private val masterKeyManager: MasterKeyManagerImpl,
    private val userService: UserService,
    private val dhSessionStore: DHSessionStore,
    private val nonceStore: NonceStore,
    private val preferenceService: PreferenceService,
    private val preferenceStore: PreferenceStore,
    private val encryptedPreferenceStore: EncryptedPreferenceStore,
    private val databaseProvider: DatabaseProviderImpl,
    private val webClientServiceManager: WebClientServiceManager,
    private val multiDeviceManager: MultiDeviceManager,
    private val deleteAllContactsUseCase: DeleteAllContactsHelper,
    private val taskCreator: TaskCreator,
    private val serverConnectionManager: ServerConnectionManager,
    private val passphraseServiceStarter: PassphraseService.Starter,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun execute(mode: Mode = Mode.FULL) = withContext(dispatcherProvider.worker) {
        logger.info("Resetting app")

        stopConnection()
        stopServicesAndWorkers()
        PushService.deleteToken(appContext)
        ShortcutUtil.deleteAllShareTargetShortcuts(preferenceService)
        ShortcutUtil.deleteAllPinnedShortcuts()

        // Delete the master key early, so that even if the remaining steps fail, encrypted user data can no longer
        // be decrypted and the app will reset itself the next time it is restarted
        masterKeyManager.deletePersistedKey()

        // Grace period for any lingering tasks or network requests to complete
        delay(2.seconds)

        if (mode == Mode.FULL) {
            // We set a custom exception handler here, as it becomes significantly more likely that a crash might occur
            // somewhere in the app once the identity and contacts are gone and the database is closed.
            // This disables the app's default exception handling, which is ok as we will stop the entire process soon anyway.
            Thread.setDefaultUncaughtExceptionHandler { _, e ->
                logger.error("Exception detected", e)
            }
        }

        deleteContacts()
        deleteIdentity()

        resetDatabases(mode)
        deleteAllFiles(mode)

        if (mode == Mode.FULL) {
            // This should not be reachable, as [ActivityManager.clearApplicationUserData] should already stop the app,
            // but just to be on the safe side, we explicitly stop the app process here
            exitProcess(0)
        }
    }

    private fun stopConnection() {
        try {
            serverConnectionManager.stopConnection()
        } catch (e: Exception) {
            logger.warn("Failed to stop connection", e)
        }
    }

    private suspend fun stopServicesAndWorkers() {
        webClientServiceManager.sessionService.stopAll(
            DisconnectContext.byUs(DisconnectContext.REASON_SESSION_DELETED),
        )
        passphraseServiceStarter.stop()
        if (multiDeviceManager.isMultiDeviceActive) {
            taskCreator.scheduleDeactivateMultiDeviceTask().await()
        }
        WorkManager.getInstance(appContext).cancelAllWork()
    }

    private suspend fun deleteContacts() {
        try {
            deleteAllContactsUseCase.call()
        } catch (e: Exception) {
            // Deletion is done on a best-effort basis, as the database file will be cleared in the end anyway
            logger.warn("Failed to delete contacts", e)
        }
    }

    private fun deleteIdentity() {
        try {
            userService.removeIdentity()
        } catch (e: Exception) {
            logger.warn("Failed to remove identity", e)
        }
    }

    private suspend fun resetDatabases(mode: Mode) {
        when (mode) {
            Mode.FULL -> {
                // All app files will be deleted in the end, but with some files we want to be extra careful,
                // so we first overwrite them with zeroes

                databaseProvider.close()
                DatabaseOpenHelper.getDatabaseFile(appContext).deleteSecurely(appContext)
                DatabaseOpenHelper.getDatabaseBackupFile(appContext).deleteSecurely(appContext)

                dhSessionStore.deleteAll()
                dhSessionStore.close()
                appContext.getDatabasePath(SQLDHSessionStore.DATABASE_NAME).deleteSecurely(appContext)

                nonceStore.deleteAll()
                (nonceStore as? DatabaseNonceStore)?.close()
                DatabaseNonceStore.getDatabaseFile(appContext).deleteSecurely(appContext)
            }
            Mode.PARTIAL -> {
                // All databases must remain open, which is why here they are either cleared without closing,
                // or they are closed, deleted and then reopened to create a fresh instance.

                databaseProvider.close()
                DatabaseOpenHelper.getDatabaseFile(appContext).deleteSecurely(appContext)
                DatabaseOpenHelper.getDatabaseBackupFile(appContext).deleteSecurely(appContext)
                masterKeyManager.masterKeyProvider.getMasterKeyOrNull()?.let { masterKey ->
                    databaseProvider.open(masterKey)
                }

                dhSessionStore.deleteAll()
                nonceStore.deleteAll()
            }
        }
    }

    private fun deleteAllFiles(mode: Mode) {
        when (mode) {
            Mode.FULL -> {
                appContext.getSystemService<ActivityManager>()!!.clearApplicationUserData()
            }
            Mode.PARTIAL -> {
                preferenceStore.clear()
                encryptedPreferenceStore.clear()
                appContext.filesDir.clearDirectoryRecursively()
                appContext.cacheDir.clearDirectoryRecursively()
            }
        }
    }

    enum class Mode {
        /**
         * Performs a full reset of the app, in which all user data, including databases, media files and *all* shared preferences, are deleted.
         * Notifications are canceled and runtime permissions are revoked. The app will be fully closed afterward.
         *
         * See also [ActivityManager.clearApplicationUserData] for details.
         */
        FULL,

        /**
         * Performs a partial reset of the app, in which all user data, including databases, media files and *default* shared preferences,
         * are deleted. Notifications are *not* canceled, runtime permissions are *not* revoked and the app will *not* be closed afterward.
         *
         * This should only be used in rare circumstances (e.g. when restoring a data backup fails), as it may leave the app in an
         * invalid state, which may then lead to crashes.
         *
         * Afterward, the master key will be in an unpersisted state. It is the responsibility of the caller to eventually call
         * [MasterKeyManagerImpl.persistKeyDataIfNeeded]. Failure to do so might mean that the app is reset again the next time it is restarted.
         */
        PARTIAL,
    }
}
