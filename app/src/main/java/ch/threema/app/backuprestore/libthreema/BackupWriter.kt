package ch.threema.app.backuprestore.libthreema

import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.libthreema.DataBackupCreateInstruction
import ch.threema.libthreema.DataBackupCreateLocalWriteInstruction
import ch.threema.libthreema.DataBackupCreateLoop
import ch.threema.libthreema.DataBackupCreateStateUpdate
import ch.threema.libthreema.DataBackupCreateTask
import ch.threema.libthreema.DataBackupCreateWriteInstruction
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

private val logger = getThreemaLogger("BackupWriter")

class BackupWriter(
    private val backupFile: File,
    private val backupCreateTask: DataBackupCreateTask,
    private val dispatcherProvider: DispatcherProvider,
) {
    fun writeBackupFile(): Flow<BackupWriteEvent> = flow {
        RandomAccessFile(backupFile, "rw").use { randomAccessBackupFile ->
            val initialLength = randomAccessBackupFile.length()
            if (initialLength > 0) {
                val fileNotEmptyEvent = BackupWriteEvent.Finished.Failure.InitialFileNotEmpty
                emit(fileNotEmptyEvent)
                return@flow
            }

            runCreateLoop(randomAccessBackupFile)
        }
    }.flowOn(dispatcherProvider.io)

    private suspend fun FlowCollector<BackupWriteEvent>.runCreateLoop(randomAccessBackupFile: RandomAccessFile) {
        while (currentCoroutineContext().isActive) {
            try {
                when (val loop = backupCreateTask.poll()) {
                    is DataBackupCreateLoop.Instruction -> applyDataBackupCreateInstruction(loop.v1, randomAccessBackupFile)
                    is DataBackupCreateLoop.Done -> {
                        emit(
                            BackupWriteEvent.Finished.Success(
                                fileName = backupCreateTask.getFilename(),
                            ),
                        )
                        return
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(BackupWriteEvent.Finished.Failure.UnexpectedError(exception = e))
                return
            }
        }
        awaitCancellation()
    }

    private suspend fun FlowCollector<BackupWriteEvent>.applyDataBackupCreateInstruction(
        backupCreateInstruction: DataBackupCreateInstruction,
        randomAccessBackupFile: RandomAccessFile,
    ) {
        // Apply the write instruction
        when (val writeInstruction = backupCreateInstruction.writeInstruction) {
            is DataBackupCreateWriteInstruction.Local -> applyLocalWriteInstruction(writeInstruction.v1, randomAccessBackupFile)
            null -> Unit
        }

        // Forward the state update
        backupCreateInstruction.stateUpdate.toBackupCreateEvent()?.let { event ->
            emit(event)
        }
    }

    private fun applyLocalWriteInstruction(
        localWriteInstruction: DataBackupCreateLocalWriteInstruction,
        randomAccessBackupFile: RandomAccessFile,
    ) = when (localWriteInstruction) {
        is DataBackupCreateLocalWriteInstruction.Append -> {
            randomAccessBackupFile.seek(randomAccessBackupFile.length())
            randomAccessBackupFile.write(localWriteInstruction.v1)
        }

        is DataBackupCreateLocalWriteInstruction.WriteAt -> {
            logger.debug(
                "Writing {} bytes at position {} of a file with length {}",
                localWriteInstruction.data.size,
                localWriteInstruction.offset,
                randomAccessBackupFile.length(),
            )

            randomAccessBackupFile.seek(localWriteInstruction.offset.toLong())
            randomAccessBackupFile.write(localWriteInstruction.data)
        }
    }

    private fun DataBackupCreateStateUpdate?.toBackupCreateEvent(): BackupWriteEvent? = BackupWriteEvent.InProgress(
        backupStep = when (this) {
            DataBackupCreateStateUpdate.USER_DATA -> BackupStep.UserData
            DataBackupCreateStateUpdate.CONTACTS -> BackupStep.Contacts
            DataBackupCreateStateUpdate.CSP_NONCES -> BackupStep.CspNonces
            DataBackupCreateStateUpdate.DISTRIBUTION_LISTS -> BackupStep.DistributionLists
            DataBackupCreateStateUpdate.FINALIZE -> BackupStep.Finalizing
            DataBackupCreateStateUpdate.GROUPS -> BackupStep.Groups
            DataBackupCreateStateUpdate.MESSAGES -> BackupStep.Messages
            DataBackupCreateStateUpdate.POLLS -> BackupStep.Polls
            null -> return null
        },
    )

    sealed interface BackupWriteEvent {
        data class InProgress(val backupStep: BackupStep) : BackupWriteEvent

        sealed interface Finished : BackupWriteEvent {
            data class Success(val fileName: String) : Finished
            sealed interface Failure : Finished {
                data object InitialFileNotEmpty : Failure
                data class UnexpectedError(val exception: Exception) : Failure
            }
        }
    }
}
