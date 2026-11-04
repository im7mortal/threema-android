package ch.threema.app.backuprestore.libthreema

import app.cash.turbine.test
import ch.threema.libthreema.DataBackupCreateInstruction
import ch.threema.libthreema.DataBackupCreateLocalWriteInstruction
import ch.threema.libthreema.DataBackupCreateLoop
import ch.threema.libthreema.DataBackupCreateStateUpdate
import ch.threema.libthreema.DataBackupCreateTask
import ch.threema.libthreema.DataBackupCreateWriteInstruction
import ch.threema.testhelpers.createTempDirectory
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.sequences.sequence
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlinx.coroutines.test.runTest

class BackupWriterTest {

    @Test
    fun `writing backup fails if initial file is not empty`() = runTest {
        val nonEmptyFile = File(createTempDirectory(), "internal_backup").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        val backupWriter = BackupWriter(
            backupFile = nonEmptyFile,
            backupCreateTask = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        backupWriter.writeBackupFile().test {
            expectItem(BackupWriter.BackupWriteEvent.Finished.Failure.InitialFileNotEmpty)
            awaitComplete()
        }
    }

    @Test
    fun `writing simple file succeeds`() = runTest {
        val expectedBytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        val backupFile = File(createTempDirectory(), "internal_backup")

        val backupCreateTaskMock = getDataBackupCreateTaskMock(
            instructions = sequenceOf(
                createWriteAtInstruction(
                    offset = 2u,
                    data = byteArrayOf(2, 3, 4, 5),
                    stateUpdate = null,
                ),
                createWriteAtInstruction(
                    offset = 1u,
                    data = byteArrayOf(1),
                    stateUpdate = DataBackupCreateStateUpdate.CONTACTS,
                ),
                createInstructionWithStateUpdate(
                    stateUpdate = DataBackupCreateStateUpdate.CSP_NONCES,
                ),
                createAppendInstruction(
                    data = byteArrayOf(6, 7),
                ),
                createWriteAtInstruction(
                    offset = 0u,
                    data = byteArrayOf(0),
                ),
                DataBackupCreateLoop.Done,
            ),
        )

        val backupWriter = BackupWriter(
            backupFile = backupFile,
            backupCreateTask = backupCreateTaskMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        backupWriter.writeBackupFile().test {
            expectItem(BackupWriter.BackupWriteEvent.InProgress(backupStep = BackupStep.Contacts))
            expectItem(BackupWriter.BackupWriteEvent.InProgress(backupStep = BackupStep.CspNonces))
            expectItem(BackupWriter.BackupWriteEvent.Finished.Success(fileName = EXPECTED_FILE_NAME))
            awaitComplete()

            assertContentEquals(expectedBytes, backupFile.readBytes())
        }
    }

    @Test
    fun `writing backup aborts if cancelled`() = runTest {
        val backupFile = File(createTempDirectory(), "internal_backup")

        // This backup create task mock never terminates
        val backupCreateTaskMock = getDataBackupCreateTaskMock(
            instructions = sequence {
                while (true) {
                    yield(
                        createWriteAtInstruction(
                            offset = 0u,
                            data = byteArrayOf(0),
                        ),
                    )
                }
            },
        )

        val backupWriter = BackupWriter(
            backupFile = backupFile,
            backupCreateTask = backupCreateTaskMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        backupWriter.writeBackupFile().test {
            // Cancels the underlying flow (coroutine). Note that without cancellation, this flow would never complete.
            cancel()
            // Note that in case the cancellation would not work, this would never be completed.
            awaitComplete()
        }
    }

    private fun getDataBackupCreateTaskMock(
        instructions: Sequence<DataBackupCreateLoop>,
    ): DataBackupCreateTask = mockk {
        val instructionIterator = instructions.iterator()
        every { poll() } answers {
            instructionIterator.next()
        }
        every { getFilename() } returns EXPECTED_FILE_NAME
    }

    private fun createWriteAtInstruction(
        offset: ULong,
        data: ByteArray,
        stateUpdate: DataBackupCreateStateUpdate? = null,
    ) = DataBackupCreateLoop.Instruction(
        DataBackupCreateInstruction(
            writeInstruction = DataBackupCreateWriteInstruction.Local(
                v1 = DataBackupCreateLocalWriteInstruction.WriteAt(
                    offset = offset,
                    data = data,
                ),
            ),
            stateUpdate = stateUpdate,
        ),
    )

    private fun createAppendInstruction(
        data: ByteArray,
        stateUpdate: DataBackupCreateStateUpdate? = null,
    ) = DataBackupCreateLoop.Instruction(
        DataBackupCreateInstruction(
            writeInstruction = DataBackupCreateWriteInstruction.Local(
                v1 = DataBackupCreateLocalWriteInstruction.Append(
                    v1 = data,
                ),
            ),
            stateUpdate = stateUpdate,
        ),
    )

    private fun createInstructionWithStateUpdate(
        stateUpdate: DataBackupCreateStateUpdate,
    ) = DataBackupCreateLoop.Instruction(
        DataBackupCreateInstruction(
            writeInstruction = null,
            stateUpdate = stateUpdate,
        ),
    )

    private companion object {
        private const val EXPECTED_FILE_NAME = "final_file_name"
    }
}
