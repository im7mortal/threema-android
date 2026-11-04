package ch.threema.app.backuprestore.libthreema

import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import ch.threema.android.isFileUri
import ch.threema.android.toTreeUriDocumentFile
import ch.threema.app.libthreema.provider.ContactProvider
import ch.threema.app.libthreema.provider.ConversationProvider
import ch.threema.app.libthreema.provider.CspNonceProvider
import ch.threema.app.libthreema.provider.DistributionListProvider
import ch.threema.app.libthreema.provider.GroupProvider
import ch.threema.app.libthreema.provider.PollProvider
import ch.threema.app.libthreema.provider.SettingsProvider
import ch.threema.app.libthreema.provider.UserProfileProvider
import ch.threema.app.services.FileService
import ch.threema.app.utils.MimeUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.common.DispatcherProvider
import ch.threema.common.byteSize
import ch.threema.domain.stores.IdentityStore
import ch.threema.libthreema.DataBackupCreateInit
import ch.threema.libthreema.DataBackupCreateTask
import ch.threema.libthreema.DataBackupProviders
import ch.threema.libthreema.IdentityData
import java.io.File
import kotlin.String
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("BackupCreateJob")

class BackupCreateJob private constructor(
    val backupCreateParameters: BackupCreateParameters,
    private val coroutineScope: CoroutineScope,
    private val appContext: Context,
    private val backupWriter: BackupWriter,
    private val backupCreateStateManager: BackupCreateStateManager,
    private val temporaryBackupFile: File,
    private val backupCreateTask: DataBackupCreateTask,
) {
    private val backupWriteJob: Job

    init {
        backupWriteJob = coroutineScope.launch {
            withTimeMeasurement(logger, "BackupWriteJob") {
                backupWriter.writeBackupFile().collect { event ->
                    val backupCreateState: BackupCreateState = when (event) {
                        is BackupWriter.BackupWriteEvent.InProgress -> BackupCreateState.InProgress(
                            backupStep = event.backupStep,
                            includeMedia = backupCreateParameters.includeMedia,
                        )
                        is BackupWriter.BackupWriteEvent.Finished -> handleFinishedEvent(event)
                    }
                    backupCreateStateManager.updateState(backupCreateState)

                    if (backupCreateState is BackupCreateState.Done) {
                        logger.info("Collected done event")
                        _backupFinished.complete(backupCreateState.getBackupCreateResult())
                        return@collect
                    }
                }
            }
        }
        backupWriteJob.invokeOnCompletion { throwable ->
            // Delete temporary backup file as this is not needed anymore
            runCatching {
                temporaryBackupFile.delete()
            }.onFailure { throwable -> logger.warn("Could not delete temporary file", throwable) }

            if (throwable == null) {
                if (!_backupFinished.isCompleted) {
                    _backupFinished.completeExceptionally(IllegalStateException("Backup write job finished without 'done' event"))
                }
            } else {
                _backupFinished.completeExceptionally(throwable)
            }
        }
    }

    private val _backupFinished = CompletableDeferred<BackupCreateResult>()

    /**
     * This deferred will be completed once the backup creation has finished. In case it was not successful, it is completed exceptionally.
     */
    val backupFinished: Deferred<BackupCreateResult>
        get() = _backupFinished

    suspend fun cancelAndJoin() {
        backupWriteJob.cancelAndJoin()
    }

    private fun handleFinishedEvent(
        event: BackupWriter.BackupWriteEvent.Finished,
    ): BackupCreateState = when (event) {
        is BackupWriter.BackupWriteEvent.Finished.Failure.InitialFileNotEmpty -> {
            logger.error("Backup creation failed because file was not empty initially")
            BackupCreateState.Done.Failure
        }

        is BackupWriter.BackupWriteEvent.Finished.Failure.UnexpectedError -> {
            logger.error("Backup creation failed unexpectedly", event.exception)
            BackupCreateState.Done.Failure
        }

        is BackupWriter.BackupWriteEvent.Finished.Success -> {
            val fileSize = temporaryBackupFile.byteSize
            val filename = backupCreateTask.getFilename()
            logger.info("Backup creation successful; copying file to backup uri ({})", fileSize)
            try {
                withTimeMeasurement(logger, "copyToBackupUri") {
                    temporaryBackupFile.copyToBackupUri(
                        backupUri = backupCreateParameters.backupUri,
                        filename = filename,
                    )
                }
                BackupCreateState.Done.Success(
                    fileSize = fileSize,
                    filename = filename,
                    fileLocation = getFileLocation(backupCreateParameters.backupUri),
                    includeMedia = backupCreateParameters.includeMedia,
                )
            } catch (e: Exception) {
                logger.error("Could not copy temporary file to final file", e)
                BackupCreateState.Done.Failure
            }
        }
    }

    private fun getFileLocation(backupUri: Uri): String? =
        backupUri.toTreeUriDocumentFile(appContext)?.name

    private fun File.copyToBackupUri(backupUri: Uri, filename: String) {
        val finalBackupFile = if (backupUri.isFileUri()) {
            DocumentFile.fromFile(File(backupUri.toFile(), filename))
        } else {
            val directory = DocumentFile.fromTreeUri(appContext, backupUri)
            if (directory != null && directory.exists()) {
                directory.createFile(MimeUtil.MIME_TYPE_DEFAULT, filename)
                    ?: error("Could not create final backup file")
            } else {
                error("Could not create file as directory does not exist")
            }
        }
        try {
            appContext.contentResolver.openOutputStream(finalBackupFile.uri)?.use { outputStream ->
                inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 12 * 1024)
                }
            }
        } catch (e: Exception) {
            finalBackupFile.delete()
            throw e
        }
    }

    private fun BackupCreateState.Done.getBackupCreateResult(): BackupCreateResult = when (this) {
        is BackupCreateState.Done.Success -> BackupCreateResult.Success(
            fileSize = fileSize,
            filename = filename,
            fileLocation = fileLocation,
            includesMedia = includeMedia,
        )
        is BackupCreateState.Done.Failure -> BackupCreateResult.Failure
    }

    class Creator(
        private val appContext: Context,
        private val identityStore: IdentityStore,
        private val fileService: FileService,
        private val dispatcherProvider: DispatcherProvider,
        private val userProfileProvider: UserProfileProvider,
        private val settingsProvider: SettingsProvider,
        private val cspNonceProvider: CspNonceProvider,
        private val contactProvider: ContactProvider,
        private val groupProvider: GroupProvider,
        private val distributionListProvider: DistributionListProvider,
        private val pollProvider: PollProvider,
        private val conversationProvider: ConversationProvider,
        private val backupCreateStateManager: BackupCreateStateManager,
    ) {
        suspend fun startBackupCreateJob(
            backupCreateParameters: BackupCreateParameters,
        ): BackupCreateJob {
            val backupCreateTask = DataBackupCreateTask(
                init = createDataBackupCreateInit(
                    backupPassword = backupCreateParameters.backupPassword,
                ),
            )

            val temporaryBackupFile = withContext(dispatcherProvider.io) {
                fileService.createTempFile("${System.currentTimeMillis()}-backup", "INCOMPLETE")
            }

            val backupWriter = BackupWriter(
                backupFile = temporaryBackupFile,
                backupCreateTask = backupCreateTask,
                dispatcherProvider = dispatcherProvider,
            )

            return BackupCreateJob(
                backupCreateParameters = backupCreateParameters,
                coroutineScope = CoroutineScope(currentCoroutineContext() + dispatcherProvider.io),
                appContext = appContext,
                backupWriter = backupWriter,
                backupCreateStateManager = backupCreateStateManager,
                temporaryBackupFile = temporaryBackupFile,
                backupCreateTask = backupCreateTask,
            )
        }

        private fun createDataBackupCreateInit(backupPassword: String): DataBackupCreateInit =
            DataBackupCreateInit(
                password = backupPassword,
                identityData = createIdentityData(),
                providers = DataBackupProviders(
                    userProfile = userProfileProvider,
                    settings = settingsProvider,
                    cspNonces = cspNonceProvider,
                    contacts = contactProvider,
                    groups = groupProvider,
                    distributionLists = distributionListProvider,
                    polls = pollProvider,
                    conversations = conversationProvider,
                ),
            )

        private fun createIdentityData(): IdentityData = IdentityData(
            userIdentity = identityStore.getIdentityString() ?: error("Cannot create backup without identity."),
            clientKey = identityStore.getPrivateKey() ?: error("Cannot create backup without client key."),
            cspServerGroup = identityStore.getServerGroup() ?: error("Cannot create backup without server group."),
        )
    }
}
