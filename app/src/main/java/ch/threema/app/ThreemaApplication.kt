package ch.threema.app

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import android.os.Process
import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.lifecycle.ProcessLifecycleOwner
import ch.threema.app.debug.StrictModeMonitor
import ch.threema.app.di.MasterKeyLockStateChangeHandler
import ch.threema.app.di.SessionScopeContainer
import ch.threema.app.di.getOrNull
import ch.threema.app.di.initDependencyInjection
import ch.threema.app.di.injectNonBinding
import ch.threema.app.drafts.DraftManagerImpl
import ch.threema.app.errorreporting.ThreemaUncaughtExceptionHandler
import ch.threema.app.logging.AppVersionLogger
import ch.threema.app.logging.ExitReasonLogger
import ch.threema.app.monitors.MonitorController
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.services.ThreemaPushService
import ch.threema.app.services.avatarcache.AvatarCacheService
import ch.threema.app.services.withConnection
import ch.threema.app.startup.AppProcessLifecycleObserver
import ch.threema.app.startup.AppStartupError
import ch.threema.app.startup.AppStartupMonitorImpl
import ch.threema.app.startup.deleteOrphanedUserData
import ch.threema.app.startup.models.AppSystem
import ch.threema.app.systemupdates.SystemUpdateException
import ch.threema.app.systemupdates.SystemUpdateProvider
import ch.threema.app.systemupdates.SystemUpdater
import ch.threema.app.threemasafe.ThreemaSafeService
import ch.threema.app.ui.DynamicColorsHelper
import ch.threema.app.utils.FileUtil
import ch.threema.app.utils.LinuxSecureRandom
import ch.threema.app.voip.Config
import ch.threema.app.webclient.services.SessionWakeUpService
import ch.threema.app.workers.WorkerStartupScheduler
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.common.DispatcherProvider
import ch.threema.domain.libthreema.LibthreemaAware
import ch.threema.domain.stores.DHSessionStore
import ch.threema.libthreema.LogLevel
import ch.threema.libthreema.initialize as initLibthreema
import ch.threema.localcrypto.MasterKey
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.MasterKeyManagerImpl
import ch.threema.localcrypto.MasterKeyProvider
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import ch.threema.localcrypto.models.MasterKeyReadResult
import ch.threema.logging.LibthreemaLogger
import ch.threema.logging.backend.DebugLogFileBackend
import ch.threema.logging.backend.DebugLogFileManager
import ch.threema.storage.DatabaseDowngradeException
import ch.threema.storage.DatabaseProviderImpl
import ch.threema.storage.DatabaseService
import ch.threema.storage.DatabaseState
import ch.threema.storage.DatabaseUpdateException
import ch.threema.storage.SQLDHSessionStore
import ch.threema.storage.setupDatabaseLogging
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject

private val logger = getThreemaLogger("ThreemaApplication")

@LibthreemaAware
class ThreemaApplication : Application() {

    // TODO(ANDR-4187): Move these dependencies and the logic that uses them to a better place
    private val appStartupMonitor: AppStartupMonitorImpl by inject()
    private val monitorController: MonitorController by inject()

    override fun onCreate() {
        if (!checkAppReplacingState(applicationContext)) {
            return
        }
        instance = this

        // Enable the debug log file initially, such that any potential crashes during app startup are captured
        DebugLogFileBackend.setEnabled(DebugLogFileManager(this), true)

        StrictModeMonitor.enableIfNeeded()

        super.onCreate()

        setUpUnhandledExceptionLogger()

        DynamicColorsHelper.applyDynamicColorsIfEnabled(this)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        logger.info("*** App launched")

        initLibthreema(LogLevel.TRACE, LibthreemaLogger())

        setUpSecureRandom()

        initDependencyInjection(this)

        setUpDayNightMode()

        with(get<AppVersionLogger>()) {
            logAppVersionInfo()
            updateAppVersionHistory()
        }
        coroutineScope.launch {
            get<ExitReasonLogger>().logExitReason()
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(get<AppProcessLifecycleObserver>())

        val masterKeyManager: MasterKeyManagerImpl = try {
            get()
        } catch (e: Exception) {
            logger.error("Failed to create master key manager", e)
            appStartupMonitor.reportUnexpectedAppStartupError("MK-0")
            return
        }

        coroutineScope.launch {
            monitorController.run()
        }

        coroutineScope.launch(dispatcherProvider.main.immediate) {
            readOrGenerateMasterKey(masterKeyManager)
            monitorMasterKey(masterKeyManager.masterKeyProvider)
        }

        GlobalBroadcastReceivers.registerBroadcastReceivers(applicationContext)
    }

    private fun checkAppReplacingState(context: Context): Boolean {
        // workaround https://code.google.com/p/android/issues/detail?id=56296
        if (context.resources == null) {
            logger.warn("App is currently installing. Killing it.")
            Process.killProcess(Process.myPid())
            return false
        }
        return true
    }

    private fun setUpUnhandledExceptionLogger() {
        Thread.setDefaultUncaughtExceptionHandler(ThreemaUncaughtExceptionHandler(this))
    }

    private fun setUpSecureRandom() {
        // We instantiate our own SecureRandom implementation to make sure this gets used everywhere
        LinuxSecureRandom()
    }

    private suspend fun readOrGenerateMasterKey(masterKeyManager: MasterKeyManagerImpl) = withContext(dispatcherProvider.io) {
        try {
            val result = masterKeyManager.readOrGenerateKey()
            if (result == MasterKeyReadResult.NEWLY_GENERATED) {
                launch(dispatcherProvider.io) {
                    deleteOrphanedUserData(applicationContext)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to read or generate master key", e)
            appStartupMonitor.reportUnexpectedAppStartupError("MK-1")
        }
    }

    private suspend fun monitorMasterKey(masterKeyProvider: MasterKeyProvider) = coroutineScope {
        try {
            while (true) {
                val masterKey = masterKeyProvider.awaitUnlocked()
                onMasterKeyUnlocked(masterKey)

                masterKeyProvider.awaitLocked()
                get<MasterKeyLockStateChangeHandler>().onMasterKeyLocked()
            }
        } catch (e: Exception) {
            logger.error("Master key monitoring failed", e)
            exitProcess(2)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        logger.info("*** App is low on memory")

        super.onLowMemory()
        try {
            getOrNull<AvatarCacheService>()?.clear()
        } catch (e: Exception) {
            logger.error("Failed to clear avatar cache", e)
        }
    }

    override fun onTrimMemory(level: Int) {
        logger.info("onTrimMemory (level={})", level)
        super.onTrimMemory(level)
    }

    companion object : KoinComponent {
        @SuppressLint("StaticFieldLeak")
        private lateinit var instance: Context

        private val masterKeyManager: MasterKeyManager by inject()
        private val appStartupMonitor: AppStartupMonitorImpl by inject()
        private val masterKeyLockStateChangeHandler: MasterKeyLockStateChangeHandler by inject()
        private val preferenceService: PreferenceService by inject()
        private val dispatcherProvider: DispatcherProvider by inject()
        private val threemaPushServiceStarter: ThreemaPushService.Starter by injectNonBinding()
        private val coroutineScope by lazy { CoroutineScope(dispatcherProvider.worker) }

        private suspend fun onMasterKeyUnlocked(masterKey: MasterKey) {
            logger.info("*** Master key unlocked")

            val appContext = instance

            val sharedPreferences = get<SharedPreferences>()
            coroutineScope.launch(dispatcherProvider.io) {
                resolveMasterKeyDeactivationRaceCondition(appContext, masterKeyManager, sharedPreferences)
                setDefaultPreferences(appContext, sharedPreferences)
            }
            setUpDayNightMode()

            try {
                setUpSqlCipher()
                val databaseProvider: DatabaseProviderImpl = get()
                coroutineScope.launch {
                    try {
                        withTimeMeasurement(logger, "openDatabase") {
                            databaseProvider.open(masterKey)
                        }
                    } catch (e: DatabaseUpdateException) {
                        appStartupMonitor.reportUnexpectedAppStartupError("DB-${e.failedDatabaseUpdateVersion}")
                    } catch (e: DatabaseDowngradeException) {
                        appStartupMonitor.reportAppStartupError(AppStartupError.DatabaseDowngrade(e.oldDatabaseVersion))
                    }
                }
                val dhSessionStore = createDHSessionStore(appContext, masterKey)

                // Since the DB updates are kicked off on a different thread, we have to wait for them to start before we continue.
                // Otherwise, we might get race-conditions with other threads that might access the DB before the migration thread.
                databaseProvider.databaseState.first { it != DatabaseState.INIT }

                val sessionScopeContainer = SessionScopeContainer(appContext, dhSessionStore)
                val systemUpdater = SystemUpdater(sharedPreferences)
                masterKeyLockStateChangeHandler.onMasterKeyUnlocked(
                    sessionScopeContainer,
                    databaseProvider.databaseState,
                    systemUpdater.systemUpdateState,
                )

                // The system updates must only be started after the service manager is set up,
                // as some system updates may (indirectly) depend on it
                runSystemUpdatesIfNeeded(
                    systemUpdater,
                    databaseProvider,
                    appStartupMonitor,
                )

                startThreemaPushIfNeeded(appContext)

                if (BuildFlavor.IS_WORK) {
                    coroutineScope.launch {
                        get<AppRestrictionService>().reload()
                    }
                }

                coroutineScope.launch {
                    // trigger a connection now, just to be sure we're up-to-date and any broken connection
                    // (e.g. from before a reboot) is preempted.
                    sessionScopeContainer.lifetimeService.withConnection(sourceTag = "resetConnection", linger = 1.minutes)
                }

                coroutineScope.launch {
                    appStartupMonitor.awaitSystem(AppSystem.DATABASE_UPDATES)

                    markUploadingFilesAsFailed(databaseService = get())
                    get<SessionWakeUpService>().processPendingWakeupsAsync()
                    get<ThreemaSafeService>().schedulePeriodicUpload()
                    get<WorkerStartupScheduler>().scheduleWorkers()
                }

                coroutineScope.launch {
                    get<DraftManagerImpl>().init()
                }
            } catch (e: MasterKeyLockedException) {
                logger.error("Master key was unexpectedly locked during onMasterKeyUnlocked", e)
                appStartupMonitor.reportUnexpectedAppStartupError("MK-L")
            } catch (e: SQLiteException) {
                logger.error("Failed to open database", e)
                appStartupMonitor.reportUnexpectedAppStartupError("DB-U0")
            }
        }

        private fun startThreemaPushIfNeeded(context: Context) {
            threemaPushServiceStarter.startIfNeeded(logger)
        }

        private fun runSystemUpdatesIfNeeded(
            systemUpdater: SystemUpdater,
            databaseProvider: DatabaseProviderImpl,
            appStartupMonitor: AppStartupMonitorImpl,
        ) {
            val hasUpdates = systemUpdater.checkForUpdates(
                systemUpdateProvider = SystemUpdateProvider(),
                initialVersion = getInitialSystemUpdateVersion(databaseProvider),
            )
            if (hasUpdates) {
                coroutineScope.launch {
                    try {
                        systemUpdater.runUpdates()
                    } catch (e: SystemUpdateException) {
                        appStartupMonitor.reportUnexpectedAppStartupError("SU-${e.failedSystemUpdateVersion}")
                    }
                }
            }
        }

        private fun getInitialSystemUpdateVersion(databaseProvider: DatabaseProviderImpl): Int? {
            // Until DB version 109, the system updates and database updates were treated as the same thing and as such shared a version number.
            // Now they are split up, with both update types having their own version number which is incremented independently and thus will
            // diverge over time.
            return databaseProvider.oldVersion?.coerceAtMost(109)
        }

        private fun setUpDayNightMode() {
            AppCompatDelegate.setDefaultNightMode(preferenceService.getTheme().mode)
        }

        private fun setUpSqlCipher() {
            System.loadLibrary("sqlcipher")
            setupDatabaseLogging()
        }

        // TODO(ANDR-4800): Move this elsewhere
        private fun resolveMasterKeyDeactivationRaceCondition(
            context: Context,
            masterKeyManager: MasterKeyManager,
            sharedPreferences: SharedPreferences,
        ) {
            // Fix master key preference state if necessary (could be wrong if user kills app
            // while disabling master key passphrase).
            if (
                masterKeyManager.isProtectedWithPassphrase() &&
                !sharedPreferences.getBoolean(context.getString(R.string.preferences__masterkey_switch), false)
            ) {
                logger.debug("Master key is protected, but switch preference is disabled - fixing")
                sharedPreferences.edit {
                    putBoolean(context.getString(R.string.preferences__masterkey_switch), true)
                }
            }
        }

        /**
         * We explicitly set the value of some shared preferences to their default value here. This is needed for preferences that use
         * a dynamically computed default value, as well as those where using a specific default-value while reading is not possible in all places
         * where the preference is read.
         *
         * TODO(ANDR-4800): Move this elsewhere
         */
        @WorkerThread
        private fun setDefaultPreferences(context: Context, sharedPreferences: SharedPreferences) {
            sharedPreferences.edit {
                // If device is in AEC exclusion list and the user did not choose a preference yet, update the shared preference.
                applyIfNotSet(context, sharedPreferences, R.string.preferences__voip_echocancel) { key ->
                    putString(key, if (Config.allowHardwareAec()) "hw" else "sw")
                }

                // TODO(ANDR-5022): These defaults are currently only needed because Synchronized Settings have no mechanism for setting a default
                applyIfNotSet(context, sharedPreferences, R.string.preferences__voip_enable) { key ->
                    putBoolean(key, true)
                }
                applyIfNotSet(context, sharedPreferences, R.string.preferences__voip_video_enable) { key ->
                    putBoolean(key, true)
                }
                applyIfNotSet(context, sharedPreferences, R.string.preferences__group_calls_enable) { key ->
                    putBoolean(key, true)
                }
                applyIfNotSet(context, sharedPreferences, R.string.preferences__read_receipts) { key ->
                    putBoolean(key, true)
                }
                applyIfNotSet(context, sharedPreferences, R.string.preferences__typing_indicator) { key ->
                    putBoolean(key, true)
                }
                applyIfNotSet(context, sharedPreferences, R.string.preferences__notification_preview) { key ->
                    putBoolean(key, true)
                }

                // These defaults can be removed once we drop Android 7
                applyIfNotSet(context, sharedPreferences, R.string.preferences__notification_sound) { key ->
                    putString(key, context.getString(R.string.default_notification_sound))
                }
                applyIfNotSet(context, sharedPreferences, R.string.preferences__group_notification_sound) { key ->
                    putString(key, context.getString(R.string.default_notification_sound))
                }
                applyIfNotSet(context, sharedPreferences, R.string.preferences__group_calls_ringtone) { key ->
                    putString(key, context.getString(R.string.default_ringtone))
                }
            }
        }

        private inline fun SharedPreferences.Editor.applyIfNotSet(
            context: Context,
            sharedPreferences: SharedPreferences,
            @StringRes keyId: Int,
            apply: SharedPreferences.Editor.(key: String) -> Unit,
        ) {
            val key = context.getString(keyId)
            if (!sharedPreferences.contains(key)) {
                apply(key)
            }
        }

        private fun createDHSessionStore(
            context: Context,
            masterKey: MasterKey,
        ): DHSessionStore {
            // We create the DH session store here and execute a null operation on it to prevent
            // the app from being launched when the database is downgraded.
            val dhSessionStore = SQLDHSessionStore(context, masterKey.value)
            try {
                dhSessionStore.executeNull()
                return dhSessionStore
            } catch (e: Exception) {
                logger.error("Could not execute a statement on the DH session database", e)
                // The database file seems to be corrupt, therefore we delete the file
                val databaseFile = context.getDatabasePath(SQLDHSessionStore.DATABASE_NAME)
                if (databaseFile.exists()) {
                    FileUtil.deleteFileOrWarn(databaseFile, "sql dh session database", logger)
                }
                return SQLDHSessionStore(context, masterKey.value)
            }
        }

        @WorkerThread
        private fun markUploadingFilesAsFailed(databaseService: DatabaseService) {
            // Mark all file messages with state 'uploading' as failed. This is because the file
            // upload is not continued after app restarts. When the state has been changed to
            // failed, a resend button is displayed on the message. We only need to do this in the
            // uploading state as in sending state a persistent task is already scheduled and the
            // message will be sent when a connection is available.
            with(databaseService) {
                contactMessageModelFactory.markUnscheduledFileMessagesAsFailed()
                groupMessageModelFactory.markUnscheduledFileMessagesAsFailed()
                distributionListMessageModelFactory.markUnscheduledFileMessagesAsFailed()
            }
        }

        @Deprecated("Use DI instead")
        @JvmStatic
        fun getAppContext(): Context = instance
    }
}
