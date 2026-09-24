package ch.threema.app.adapters

import android.Manifest
import android.accounts.Account
import android.annotation.SuppressLint
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ch.threema.app.ThreemaApplication.Companion.getAppContext
import ch.threema.app.androidcontactsync.usecases.SynchronizeAndroidContactsUseCase
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.listeners.SynchronizeContactsListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.IntentDataUtil
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("ContactsSyncAdapter")

class ContactsSyncAdapter(context: Context, autoInitialize: Boolean) : AbstractThreadedSyncAdapter(context, autoInitialize), KoinComponent {
    private val synchronizeAndroidContactsUseCase: SynchronizeAndroidContactsUseCase? by injectNullableNonBinding()
    private val synchronizedSettingsService: SynchronizedSettingsService? by injectNullableNonBinding()

    private var isSyncEnabled = true

    fun setSyncEnabled(enabled: Boolean) {
        isSyncEnabled = enabled
    }

    @SuppressLint("MissingPermission")
    override fun onPerformSync(
        account: Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProviderClient?,
        syncResult: SyncResult,
    ) {
        logger.info("onPerformSync")

        if (!isSyncEnabled) {
            logger.info("Contact sync is disabled; retry later.")
            // Workaround to trigger a soft error to retry the sync at a later moment
            // See
            //  - https://developer.android.com/reference/android/content/SyncResult#hasSoftError()
            //  - https://developer.android.com/reference/android/content/SyncResult#SyncResult()
            syncResult.stats.numIoExceptions++
            return
        }

        when (synchronizedSettingsService?.isSyncContacts()) {
            true -> Unit
            false -> {
                logger.warn("Aborting android contact synchronization because synchronization is disabled in settings")
                return
            }

            null -> {
                logger.error("Aborting android contact synchronization because synchronized settings service is null")
                return
            }
        }

        logger.info("Start sync adapter run")
        if (SynchronizeAndroidContactsUseCase.isRunning) {
            logger.info("A full sync is already running")
            syncResult.stats.numUpdates = 0
            syncResult.stats.numInserts = 0
            syncResult.stats.numDeletes = 0
            syncResult.stats.numEntries = 0
            return
        }

        if (!ConfigUtils.isPermissionGranted(context, Manifest.permission.WRITE_CONTACTS)) {
            logger.warn("No permission given for writing contacts. Aborting sync.")
            return
        }

        val synchronizeAndroidContactsUseCase = synchronizeAndroidContactsUseCase
            ?: run {
                logger.error("Synchronize android contacts use case is null. Aborting sync.")
                return
            }

        val synchronizedContactsListener = object : SynchronizeContactsListener {
            override fun onFinished() {
                // Hack to not schedule the next sync!
                syncResult.stats.numUpdates = 0
                syncResult.stats.numInserts = 0
                syncResult.stats.numDeletes = 0
                syncResult.stats.numEntries = 0

                // Send a broadcast to let others know that the list has changed
                LocalBroadcastManager.getInstance(getAppContext())
                    .sendBroadcast(IntentDataUtil.createActionIntentContactsChanged())
            }
        }

        try {
            ListenerManager.synchronizeContactsListeners.add(synchronizedContactsListener)

            // As we are running on a background thread, this is ok.
            runBlocking {
                synchronizeAndroidContactsUseCase.call()
            }
        } finally {
            logger.debug(
                "sync finished Sync [numEntries={}, updates={}, inserts={}, deletes={}",
                syncResult.stats.numEntries,
                syncResult.stats.numUpdates,
                syncResult.stats.numInserts,
                syncResult.stats.numDeletes,
            )

            ListenerManager.synchronizeContactsListeners.remove(synchronizedContactsListener)
        }
    }

    override fun onSyncCanceled() {
        logger.info("Sync has been cancelled")
        super.onSyncCanceled()
    }

    override fun onSyncCanceled(thread: Thread?) {
        logger.info("Sync has been cancelled on thread {}", thread)
        super.onSyncCanceled(thread)
    }
}
