package ch.threema.app.backuprestore.libthreema

import ch.threema.android.ResourceIdString
import ch.threema.app.R
import ch.threema.app.libthreema.provider.ProvidersProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.common.DispatcherProvider
import ch.threema.domain.stores.IdentityStore
import ch.threema.libthreema.DataBackupRestoreTask
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("BackupRestoreJob")

class BackupRestoreJob private constructor(
    val restoreParameters: BackupRestoreParameters,
    coroutineScope: CoroutineScope,
    private val backupReader: BackupReader,
    private val backupRestoreStateManager: BackupRestoreStateManager,
) {

    private val backupReadJob: Job

    private val _backupRestorationFinished = CompletableDeferred<BackupRestoreResult>()

    val backupRestorationFinished: Deferred<BackupRestoreResult>
        get() = _backupRestorationFinished

    init {
        backupReadJob = coroutineScope.launch {
            withTimeMeasurement(logger, "BackupReadJob") {
                backupReader.readBackupFile(restoreParameters.backupFile).collect { event ->
                    val backupRestoreState = when (event) {
                        is BackupReader.BackupRestoreEvent.InProgress -> BackupRestoreState.InProgress(event.step)
                        is BackupReader.BackupRestoreEvent.Finished -> handleFinishedEvent(event)
                    }

                    backupRestoreStateManager.emitRestoreState(backupRestoreState)

                    if (backupRestoreState is BackupRestoreState.Done) {
                        logger.info("Backup restore is done")
                        _backupRestorationFinished.complete(backupRestoreState.getBackupRestoreResult())
                        return@collect
                    }
                }
            }
        }
        backupReadJob.invokeOnCompletion { throwable ->
            if (throwable == null) {
                if (!_backupRestorationFinished.isCompleted) {
                    _backupRestorationFinished.completeExceptionally(IllegalStateException("Backup restore job finished without 'done' event"))
                }
            } else {
                _backupRestorationFinished.completeExceptionally(throwable)
            }
        }
    }

    private fun handleFinishedEvent(event: BackupReader.BackupRestoreEvent.Finished): BackupRestoreState = when (event) {
        is BackupReader.BackupRestoreEvent.Finished.Failure.Canceled -> {
            logger.error("Backup restoration has been canceled", event.exception)
            BackupRestoreState.Done.Failure(errorMessage = ResourceIdString(resId = R.string.local_data_backup_restore_canceled))
        }
        is BackupReader.BackupRestoreEvent.Finished.Failure.DecryptionFailed -> {
            logger.error("Backup restoration failed", event.exception)
            BackupRestoreState.Done.Failure(errorMessage = ResourceIdString(resId = R.string.local_data_backup_restore_decryption_failed))
        }
        is BackupReader.BackupRestoreEvent.Finished.Failure.UnexpectedException -> {
            logger.error("Backup restoration failed unexpectedly", event.exception)
            BackupRestoreState.Done.Failure(errorMessage = ResourceIdString(resId = R.string.local_data_backup_restore_unexpected_exception))
        }
        BackupReader.BackupRestoreEvent.Finished.Success -> {
            logger.info("Backup successfully restored")
            BackupRestoreState.Done.Success
        }
    }

    private fun BackupRestoreState.Done.getBackupRestoreResult(): BackupRestoreResult = when (this) {
        is BackupRestoreState.Done.Failure -> BackupRestoreResult.Failure
        BackupRestoreState.Done.Success -> BackupRestoreResult.Success
    }

    class Creator(
        private val dispatcherProvider: DispatcherProvider,
        private val identityStore: IdentityStore,
        private val backupRestoreStateManager: BackupRestoreStateManager,
        private val providersProvider: ProvidersProvider,
    ) {
        suspend fun startBackupRestoreJob(
            restoreParameters: BackupRestoreParameters,
        ): BackupRestoreJob {
            val backupRestoreTask = DataBackupRestoreTask(
                password = restoreParameters.backupPassword,
            )

            val backupReader = BackupReader(
                backupRestoreTask = backupRestoreTask,
                dispatcherProvider = dispatcherProvider,
                identityStore = identityStore,
                providersProvider = providersProvider,
            )

            return BackupRestoreJob(
                restoreParameters = restoreParameters,
                coroutineScope = CoroutineScope(currentCoroutineContext()),
                backupReader = backupReader,
                backupRestoreStateManager = backupRestoreStateManager,
            )
        }
    }
}
