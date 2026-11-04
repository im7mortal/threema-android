package ch.threema.app.backuprestore.libthreema

import ch.threema.app.libthreema.provider.ProvidersProvider
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.domain.stores.IdentityStore
import ch.threema.libthreema.DataBackupRestoreException
import ch.threema.libthreema.DataBackupRestoreInstruction
import ch.threema.libthreema.DataBackupRestoreLocalReadInstruction
import ch.threema.libthreema.DataBackupRestoreLoop
import ch.threema.libthreema.DataBackupRestoreReadInstruction
import ch.threema.libthreema.DataBackupRestoreReadResponse
import ch.threema.libthreema.DataBackupRestoreResponse
import ch.threema.libthreema.DataBackupRestoreStateUpdate
import ch.threema.libthreema.DataBackupRestoreTask
import ch.threema.libthreema.IdentityData
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

private val logger = getThreemaLogger("BackupReader")

class BackupReader(
    private val backupRestoreTask: DataBackupRestoreTask,
    private val dispatcherProvider: DispatcherProvider,
    private val identityStore: IdentityStore,
    private val providersProvider: ProvidersProvider,
) {

    @Volatile
    private var identityStored = false

    fun readBackupFile(backupFile: File): Flow<BackupRestoreEvent> = flow {
        logger.info("Start restoring {}", backupFile)
        RandomAccessFile(backupFile, "r").use { randomAccessBackupFile ->
            runRestoreLoop(
                randomAccessBackupFile = randomAccessBackupFile,
            )
        }
    }.flowOn(dispatcherProvider.io)

    private suspend fun FlowCollector<BackupRestoreEvent>.runRestoreLoop(randomAccessBackupFile: RandomAccessFile) {
        while (currentCoroutineContext().isActive) {
            try {
                when (val loop = backupRestoreTask.poll()) {
                    is DataBackupRestoreLoop.Instruction ->
                        executeDataBackupRestoreInstruction(
                            randomAccessBackupFile = randomAccessBackupFile,
                            instruction = loop.v1,
                        )
                    is DataBackupRestoreLoop.Done -> {
                        applyBackupRestoreDone()
                        return
                    }
                }
            } catch (e: Exception) {
                val event = when (e) {
                    is DataBackupRestoreException.DecryptionFailed -> BackupRestoreEvent.Finished.Failure.DecryptionFailed(exception = e)
                    else -> BackupRestoreEvent.Finished.Failure.UnexpectedException(exception = e)
                }
                emit(event)
                return
            }
        }
        emit(BackupRestoreEvent.Finished.Failure.Canceled(exception = CancellationException("Backup read job has been canceled")))
    }

    private suspend fun FlowCollector<BackupRestoreEvent>.executeDataBackupRestoreInstruction(
        randomAccessBackupFile: RandomAccessFile,
        instruction: DataBackupRestoreInstruction,
    ) {
        val identityData: IdentityData? = instruction.identityData
        if (identityData != null && !identityStored) {
            // TODO(ANDR-4998): Consolidate further steps after backup restoration
            identityStore.storeIdentity(
                identity = identityData.userIdentity,
                serverGroup = identityData.cspServerGroup,
                privateKey = identityData.clientKey,
            )
            identityStored = true
            backupRestoreTask.response(
                DataBackupRestoreResponse(
                    providers = providersProvider.createBackupRestoreProviders(),
                    readResponse = null,
                ),
            )
        }

        val stateUpdate = instruction.stateUpdate
        if (stateUpdate != null) {
            applyStateUpdate(stateUpdate)
        }

        when (val readInstruction = instruction.readInstruction) {
            is DataBackupRestoreReadInstruction.Local -> {
                executeLocalReadInstruction(
                    randomAccessBackupFile = randomAccessBackupFile,
                    readInstruction = readInstruction.v1,
                )
            }
            null -> Unit
        }
    }

    private suspend fun FlowCollector<BackupRestoreEvent>.applyStateUpdate(stateUpdate: DataBackupRestoreStateUpdate) {
        logger.info("Commencing restore step {}", stateUpdate)
        val nextStep = when (stateUpdate) {
            DataBackupRestoreStateUpdate.USER_DATA -> BackupRestoreStep.USER_DATA
            DataBackupRestoreStateUpdate.CONTACTS -> BackupRestoreStep.CONTACTS
            DataBackupRestoreStateUpdate.DISTRIBUTION_LISTS -> BackupRestoreStep.DISTRIBUTION_LISTS
            DataBackupRestoreStateUpdate.GROUPS -> BackupRestoreStep.GROUPS
            DataBackupRestoreStateUpdate.CSP_NONCES -> BackupRestoreStep.CSP_NONCES
            DataBackupRestoreStateUpdate.POLLS -> BackupRestoreStep.POLLS
            DataBackupRestoreStateUpdate.MESSAGES -> BackupRestoreStep.MESSAGES
        }
        emit(BackupRestoreEvent.InProgress(nextStep))
    }

    private fun executeLocalReadInstruction(
        randomAccessBackupFile: RandomAccessFile,
        readInstruction: DataBackupRestoreLocalReadInstruction,
    ) {
        val fileLength = randomAccessBackupFile.length()

        val streamOffset = readInstruction.getSanitizedStreamOffset(fileLength)
        val offset = streamOffset.toLong()

        val length = readInstruction.getSanitizedLength(fileLength)

        require(streamOffset + length.toULong() <= fileLength.toULong()) {
            "Read instruction with offset=$streamOffset and length=$length points beyond file of length $fileLength"
        }

        val buffer = ByteArray(length)
        randomAccessBackupFile.seek(offset)
        val numberOfBytesRead = randomAccessBackupFile.read(buffer)

        require(numberOfBytesRead == length) {
            "Invalid number of bytes read $numberOfBytesRead instead of expected $length"
        }

        backupRestoreTask.response(
            DataBackupRestoreResponse(
                providers = null,
                readResponse = DataBackupRestoreReadResponse(
                    offset = streamOffset,
                    chunk = buffer,
                ),
            ),
        )
    }

    private fun DataBackupRestoreLocalReadInstruction.getSanitizedStreamOffset(fileLength: Long) = offset.also {
        require(it <= Long.MAX_VALUE.toULong()) {
            "Offset of $it is too high"
        }
        require(it.toLong() <= fileLength) {
            "Offset $it is beyond the length of the backup file ($fileLength)"
        }
    }

    private fun DataBackupRestoreLocalReadInstruction.getSanitizedLength(fileLength: Long) = length.let {
        require(it > 0UL) {
            "Length is zero"
        }
        require(it <= Int.MAX_VALUE.toUInt()) {
            "Length of $it is too high"
        }
        require(it.toLong() <= fileLength) {
            "Length is larger than the file length"
        }
        it.toInt()
    }

    private suspend fun FlowCollector<BackupRestoreEvent>.applyBackupRestoreDone() {
        emit(BackupRestoreEvent.Finished.Success)
    }

    sealed interface BackupRestoreEvent {
        data class InProgress(val step: BackupRestoreStep) : BackupRestoreEvent

        sealed interface Finished : BackupRestoreEvent {
            data object Success : Finished
            sealed interface Failure : Finished {
                data class Canceled(val exception: CancellationException) : Failure
                data class DecryptionFailed(val exception: DataBackupRestoreException.DecryptionFailed) : Failure
                data class UnexpectedException(val exception: Exception) : Failure
            }
        }
    }
}
