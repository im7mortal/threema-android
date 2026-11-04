package ch.threema.app.backuprestore.libthreema

import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import ch.threema.app.backuprestore.libthreema.BackupReader.BackupRestoreEvent
import ch.threema.app.libthreema.provider.ProvidersProvider
import ch.threema.common.emptyByteArray
import ch.threema.domain.stores.IdentityStore
import ch.threema.libthreema.DataBackupRestoreInstruction
import ch.threema.libthreema.DataBackupRestoreLocalReadInstruction
import ch.threema.libthreema.DataBackupRestoreLoop
import ch.threema.libthreema.DataBackupRestoreReadInstruction
import ch.threema.libthreema.DataBackupRestoreStateUpdate
import ch.threema.libthreema.DataBackupRestoreTask
import ch.threema.libthreema.IdentityData
import ch.threema.testhelpers.createTempDirectory
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import testdata.TestData

class BackupReaderTest {

    private val providersProviderMock: ProvidersProvider = mockk {
        every { createBackupRestoreProviders() } returns mockk()
    }

    @Test
    fun `reading backup fails on empty files`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(),
        )

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = mockk<IdentityStore>(relaxed = true),
            providersProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createEmptyBackupFile()

        backupReader.readBackupFile(backupFile).test {
            val item = awaitItem()
            assertIs<BackupRestoreEvent.Finished.Failure.UnexpectedException>(item)
            assertIs<NoSuchElementException>(item.exception)
            awaitComplete()
        }
    }

    @Test
    fun `reading backup terminates on done instruction`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createDoneInstruction(),
            ),
        )

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = mockk<IdentityStore>(relaxed = true),
            providersProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createNonEmptyBackupFile()

        backupReader.readBackupFile(backupFile).test {
            expectSuccess()
            awaitComplete()
        }
    }

    @Test
    fun `reading fails if offset is larger than length of backup file`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createLocalReadInstruction(1, 1),
                createDoneInstruction(),
            ),
        )

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = mockk<IdentityStore>(relaxed = true),
            providersProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createNonEmptyBackupFile()

        backupReader.readBackupFile(backupFile).test {
            val item = awaitItem()
            assertIs<BackupRestoreEvent.Finished.Failure.UnexpectedException>(item)
            assertIs<IllegalArgumentException>(item.exception)
            awaitComplete()
        }
    }

    @Test
    fun `reading fails if length is larger than file reader allows`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createLocalReadInstruction(0UL, Int.MAX_VALUE.toUInt() + 1U),
                createDoneInstruction(),
            ),
        )

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = mockk<IdentityStore>(relaxed = true),
            providersProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createNonEmptyBackupFile()

        backupReader.readBackupFile(backupFile).test {
            val item = awaitItem()
            assertIs<BackupRestoreEvent.Finished.Failure.UnexpectedException>(item)
            assertIs<IllegalArgumentException>(item.exception)
            awaitComplete()
        }
    }

    @Test
    fun `reading fails if read instructions points beyond the file length`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createLocalReadInstruction(1, 2),
                createDoneInstruction(),
            ),
        )

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = mockk<IdentityStore>(relaxed = true),
            providersProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createBackupFile(byteArrayOf(0, 1))

        backupReader.readBackupFile(backupFile).test {
            val item = awaitItem()
            assertIs<BackupRestoreEvent.Finished.Failure.UnexpectedException>(item)
            assertIs<IllegalArgumentException>(item.exception)
            awaitComplete()
        }
    }

    @Test
    fun `reading fails if backup file has fewer bytes than requested`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createLocalReadInstruction(0, 2),
                createDoneInstruction(),
            ),
        )

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = mockk<IdentityStore>(relaxed = true),
            providersProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createNonEmptyBackupFile()

        backupReader.readBackupFile(backupFile).test {
            val item = awaitItem()
            assertIs<BackupRestoreEvent.Finished.Failure.UnexpectedException>(item)
            assertIs<IllegalArgumentException>(item.exception)
            awaitComplete()
        }
    }

    @Test
    fun `bytes are read from file as instructed`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createLocalReadInstruction(0, 8),
                createDoneInstruction(),
            ),
        )

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = mockk<IdentityStore>(relaxed = true),
            providersProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createBackupFile(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))

        backupReader.readBackupFile(backupFile).test {
            expectSuccess()
            awaitComplete()
        }

        verify(exactly = 1) {
            // A custom matcher has to be used due to https://github.com/mozilla/uniffi-rs/issues/1985
            backupRestoreTaskMock.response(
                match { arg ->
                    arg.readResponse?.offset == 0UL && arg.readResponse?.chunk.contentEquals(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
                },
            )
        }
    }

    @Test
    fun `identity data is restored once`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createIdentityDataInstruction(),
                createDoneInstruction(),
            ),
        )

        val identityStoreMock = mockk<IdentityStore>(relaxed = true)

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = identityStoreMock,
            providersProvider = providersProviderMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createNonEmptyBackupFile()

        backupReader.readBackupFile(backupFile).test {
            expectSuccess()
            awaitComplete()
        }

        verify(exactly = 1) {
            identityStoreMock.storeIdentity(
                identity = TestData.Identities.ME.value,
                serverGroup = SERVER_GROUP,
                privateKey = CLIENT_KEY,
            )
        }
    }

    @Test
    fun `identity data is not restored more than once`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createIdentityDataInstruction(),
                createIdentityDataInstruction(),
                createDoneInstruction(),
            ),
        )

        val identityStoreMock = mockk<IdentityStore>(relaxed = true)

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = identityStoreMock,
            providersProvider = providersProviderMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createNonEmptyBackupFile()

        backupReader.readBackupFile(backupFile).test {
            expectSuccess()
            awaitComplete()
        }

        verify(exactly = 1) {
            identityStoreMock.storeIdentity(
                identity = TestData.Identities.ME.value,
                serverGroup = SERVER_GROUP,
                privateKey = CLIENT_KEY,
            )
        }
    }

    @Test
    fun `state updates are propagated as expected`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createStateUpdateInstruction(DataBackupRestoreStateUpdate.USER_DATA),
                createStateUpdateInstruction(DataBackupRestoreStateUpdate.CONTACTS),
                createStateUpdateInstruction(DataBackupRestoreStateUpdate.DISTRIBUTION_LISTS),
                createStateUpdateInstruction(DataBackupRestoreStateUpdate.GROUPS),
                createStateUpdateInstruction(DataBackupRestoreStateUpdate.CSP_NONCES),
                createStateUpdateInstruction(DataBackupRestoreStateUpdate.POLLS),
                createStateUpdateInstruction(DataBackupRestoreStateUpdate.MESSAGES),
                createDoneInstruction(),
            ),
        )

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = mockk<IdentityStore>(relaxed = true),
            providersProvider = mockk(),
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createNonEmptyBackupFile()

        backupReader.readBackupFile(backupFile).test {
            expectNextStep(BackupRestoreStep.USER_DATA)
            expectNextStep(BackupRestoreStep.CONTACTS)
            expectNextStep(BackupRestoreStep.DISTRIBUTION_LISTS)
            expectNextStep(BackupRestoreStep.GROUPS)
            expectNextStep(BackupRestoreStep.CSP_NONCES)
            expectNextStep(BackupRestoreStep.POLLS)
            expectNextStep(BackupRestoreStep.MESSAGES)
            expectSuccess()
            awaitComplete()
        }
    }

    private suspend fun TurbineTestContext<BackupRestoreEvent>.expectNextStep(step: BackupRestoreStep) {
        expectItem(BackupRestoreEvent.InProgress(step))
    }

    @Test
    fun `instructions with multiple data points present are handled accordingly`() = runTest {
        val backupRestoreTaskMock = getBackupRestoreTaskMock(
            instructions = sequenceOf(
                createInstruction(
                    identityData = createIdentityData(),
                    stateUpdate = DataBackupRestoreStateUpdate.GROUPS,
                    readInstruction = createDataBackupRestoreLocalReadInstruction(
                        offset = 0UL,
                        length = 4U,
                    ),
                ),
                createInstruction(
                    identityData = null,
                    stateUpdate = DataBackupRestoreStateUpdate.MESSAGES,
                    readInstruction = createDataBackupRestoreLocalReadInstruction(
                        offset = 4UL,
                        length = 4U,
                    ),
                ),
                createDoneInstruction(),
            ),
        )

        val identityStoreMock = mockk<IdentityStore>(relaxed = true)

        val backupReader = BackupReader(
            backupRestoreTask = backupRestoreTaskMock,
            identityStore = identityStoreMock,
            providersProvider = providersProviderMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        val backupFile = createBackupFile(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))

        backupReader.readBackupFile(backupFile).test {
            expectNextStep(BackupRestoreStep.GROUPS)
            expectNextStep(BackupRestoreStep.MESSAGES)
            expectSuccess()
            awaitComplete()
        }

        verify(exactly = 1) {
            identityStoreMock.storeIdentity(
                identity = TestData.Identities.ME.value,
                serverGroup = SERVER_GROUP,
                privateKey = CLIENT_KEY,
            )
        }

        verify(exactly = 1) {
            // A custom matcher has to be used due to https://github.com/mozilla/uniffi-rs/issues/1985
            backupRestoreTaskMock.response(
                match { arg ->
                    arg.providers != null
                },
            )
        }

        verify(exactly = 1) {
            // A custom matcher has to be used due to https://github.com/mozilla/uniffi-rs/issues/1985
            backupRestoreTaskMock.response(
                match { arg ->
                    arg.readResponse?.offset == 0UL && arg.readResponse?.chunk.contentEquals(byteArrayOf(0, 1, 2, 3))
                },
            )
        }

        verify(exactly = 1) {
            // A custom matcher has to be used due to https://github.com/mozilla/uniffi-rs/issues/1985
            backupRestoreTaskMock.response(
                match { arg ->
                    arg.readResponse?.offset == 4UL && arg.readResponse?.chunk.contentEquals(byteArrayOf(4, 5, 6, 7))
                },
            )
        }
    }

    private suspend fun TurbineTestContext<BackupRestoreEvent>.expectSuccess() {
        expectItem(BackupRestoreEvent.Finished.Success)
    }

    private fun getBackupRestoreTaskMock(
        instructions: Sequence<DataBackupRestoreLoop>,
    ): DataBackupRestoreTask = mockk(relaxed = true) {
        val instructionIterator = instructions.iterator()
        every { poll() } answers {
            instructionIterator.next()
        }
    }

    private fun createIdentityDataInstruction() = createInstruction(
        identityData = createIdentityData(),
    )

    private fun createIdentityData() = IdentityData(
        userIdentity = TestData.Identities.ME.value,
        clientKey = CLIENT_KEY,
        cspServerGroup = SERVER_GROUP,
    )

    private fun createLocalReadInstruction(offset: Int, length: Int) = createLocalReadInstruction(
        offset = offset.toULong(),
        length = length.toUInt(),
    )

    private fun createLocalReadInstruction(offset: ULong, length: UInt) = createInstruction(
        readInstruction = createDataBackupRestoreLocalReadInstruction(
            offset = offset,
            length = length,
        ),
    )

    private fun createDataBackupRestoreLocalReadInstruction(offset: ULong, length: UInt) = DataBackupRestoreReadInstruction.Local(
        v1 = DataBackupRestoreLocalReadInstruction(
            offset = offset,
            length = length,
        ),
    )

    private fun createStateUpdateInstruction(state: DataBackupRestoreStateUpdate) = createInstruction(
        stateUpdate = state,
    )

    private fun createInstruction(
        identityData: IdentityData? = null,
        stateUpdate: DataBackupRestoreStateUpdate? = null,
        readInstruction: DataBackupRestoreReadInstruction? = null,
    ) = DataBackupRestoreLoop.Instruction(
        v1 = DataBackupRestoreInstruction(
            identityData = identityData,
            stateUpdate = stateUpdate,
            readInstruction = readInstruction,
        ),
    )

    private fun createDoneInstruction() = DataBackupRestoreLoop.Done

    private fun createEmptyBackupFile(): File = createBackupFile(emptyByteArray())

    /**
     * This creates a backup file that contains a single byte.
     * This can be used for reader tests when the file contents are not relevant, as an empty file is not accepted in the reader.
     */
    private fun createNonEmptyBackupFile(): File = createBackupFile(byteArrayOf(0))

    private fun createBackupFile(bytes: ByteArray): File = File(createTempDirectory(), "internal_backup").apply {
        writeBytes(bytes)
    }

    private companion object {
        val CLIENT_KEY = ByteArray(32) { it.toByte() }
        const val SERVER_GROUP = "0"
    }
}
