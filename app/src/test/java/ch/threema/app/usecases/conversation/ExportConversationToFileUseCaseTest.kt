package ch.threema.app.usecases.conversation

import android.net.Uri
import android.text.format.DateUtils
import ch.threema.app.R
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.app.services.MessageService
import ch.threema.app.utils.GeoLocationUtil
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.Identity
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.status.VoipStatusDataModel
import ch.threema.testhelpers.createTempDirectory
import ch.threema.testhelpers.loadResource
import ch.threema.testhelpers.testDispatcherProvider
import ch.threema.testhelpers.utcDate
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.ZipFile
import testdata.TestData.Identities

class ExportConversationToFileUseCaseTest {

    private lateinit var fileServiceMock: FileService
    private lateinit var messageServiceMock: MessageService
    private lateinit var contactModelRepositoryMock: ContactModelRepository
    private lateinit var preferenceServiceMock: PreferenceService
    private lateinit var tempDirectory: File

    @BeforeTest
    fun setUp() {
        fileServiceMock = mockk {
            every { decryptMessageFileToStream(any()) } returns null
            every { decryptMessageFileToStream("file1") } answers { "FILE1".byteInputStream() }
            every { decryptMessageFileToStream("file4") } answers { "FILE4".byteInputStream() }
            every { decryptedMessageThumbnailToStream(any()) } returns null
            every { decryptedMessageThumbnailToStream("file2") } answers { "THUMBNAIL2".byteInputStream() }
        }
        messageServiceMock = mockk {
            every { getMessageString(any(), 0) } answers {
                val message = firstArg<MessageModel>()
                val result = if (message.type == MessageType.TEXT) message.body else message.type.toString()
                mockk<MessageService.MessageString> {
                    every { rawMessage } returns result
                }
            }
        }
        contactModelRepositoryMock = mockk {
            every { getByIdentity(Identities.OTHER_1.value) } returns mockk {
                every { data } returns mockk {
                    every { getDisplayName(ContactNameFormat.DEFAULT, true) } returns "Bob Smith"
                }
            }
        }
        preferenceServiceMock = mockk {
            every { getContactNameFormat() } returns ContactNameFormat.DEFAULT
        }
        tempDirectory = createTempDirectory()
        mockkStatic(DateUtils::class)
        every { DateUtils.formatDateTime(any(), any(), any()) } answers {
            Instant.ofEpochMilli(secondArg<Long>()).toString()
        }
        mockkStatic(GeoLocationUtil::class)
        every { GeoLocationUtil.getLocationUri(any()) } returns mockk<Uri> {
            every { this@mockk.toString() } returns "Location-URI"
        }
    }

    @AfterTest
    fun tearDown() {
        tempDirectory.deleteRecursively()
        unmockkStatic(DateUtils::class)
        unmockkStatic(GeoLocationUtil::class)
    }

    @Test
    fun `export group conversation with media`() = runTest {
        val messageReceiverMock = mockk<GroupMessageReceiver> {
            every { type } returns MessageReceiver.Type_GROUP
            every { groupModel } returns mockk {
                every { getDatabaseId() } returns 0
            }
        }
        every { messageServiceMock.getMessagesForReceiver(messageReceiverMock) } returns listOf(
            // Text message from other member
            mockMessage(sender = Identities.OTHER_1) {
                every { body } returns "Hello there"
                every { postedAt } returns utcDate(2026, 1, 30)
            },
            // Text message from us
            mockMessage(sender = Identities.ME) {
                every { body } returns "Hi!"
                every { postedAt } returns utcDate(2026, 1, 29)
            },
            // Status message (filtered out)
            mockMessage {
                every { isStatusMessage } returns true
            },
            // File message (downloaded, file exists)
            mockMessage(sender = Identities.ME) {
                every { type } returns MessageType.FILE
                every { fileData } returns mockk {
                    every { fileName } returns "my-file.pdf"
                    every { isDownloaded } returns true
                }
                every { apiMessageId } returns "abcd"
                every { postedAt } returns utcDate(2026, 1, 28)
                every { uid } returns "file1"
            },
            // File message (downloaded, only thumbnail exists)
            mockMessage(sender = Identities.ME) {
                every { type } returns MessageType.FILE
                every { fileData } returns mockk {
                    every { fileName } returns "my-image.jpg"
                    every { isDownloaded } returns true
                }
                every { apiMessageId } returns "efgh"
                every { postedAt } returns utcDate(2026, 1, 27)
                every { uid } returns "file2"
            },
            // File message (downloaded, but doesn't exist)
            mockMessage(sender = Identities.ME) {
                every { type } returns MessageType.FILE
                every { fileData } returns mockk {
                    every { fileName } returns "missing-file"
                    every { isDownloaded } returns true
                }
                every { apiMessageId } returns "ijkl"
                every { postedAt } returns utcDate(2026, 1, 26)
                every { uid } returns "file3"
            },
            // File message (not downloaded)
            mockMessage(sender = Identities.ME) {
                every { type } returns MessageType.FILE
                every { fileData } returns mockk {
                    every { fileName } returns "not-downloaded.pdf"
                    every { isDownloaded } returns false
                }
                every { apiMessageId } returns "mnop"
                every { postedAt } returns utcDate(2026, 1, 25)
                every { uid } returns "file4"
            },
            // Location message
            mockMessage {
                every { type } returns MessageType.LOCATION
                every { locationData } returns mockk()
                every { postedAt } returns utcDate(2026, 1, 24)
            },
            // Voip status message
            mockMessage {
                every { type } returns MessageType.VOIP_STATUS
                every { voipStatusData } returns VoipStatusDataModel.createFinished(callId = 123, duration = 42.seconds)
                every { postedAt } returns utcDate(2026, 1, 23)
            },
        )

        val result = createExportConversationToFileUseCase(testDispatcherProvider()).call(
            conversationModel = ConversationModel(
                messageReceiverMock,
            ),
            password = PASSWORD,
            includeMedia = true,
        )

        assertIs<ExportConversationToFileUseCase.Result.Success>(result)
        verifyFilesInZipFile(
            result.file,
            expectedFiles = setOf(
                "messages.txt",
                "abcd-my-file.pdf",
                "efgh-my-image.jpg",
            ),
        )
        assertEquals(
            loadResource("conversation-export/export1.txt").trim(),
            String(getZipFileContent(result.file, "messages.txt")).trim(),
        )
        assertContentEquals(
            "FILE1".toByteArray(),
            getZipFileContent(result.file, "abcd-my-file.pdf"),
        )
        assertContentEquals(
            "THUMBNAIL2".toByteArray(),
            getZipFileContent(result.file, "efgh-my-image.jpg"),
        )
    }

    @Test
    fun `export contact conversation without media`() = runTest {
        val messageReceiverMock = mockk<ContactMessageReceiver> {
            every { type } returns MessageReceiver.Type_CONTACT
            every { contactModel } returns mockk {
                every { identity } returns Identities.OTHER_1.value
            }
        }
        every { messageServiceMock.getMessagesForReceiver(messageReceiverMock) } returns listOf(
            // Text message from other member
            mockMessage(sender = Identities.OTHER_1) {
                every { body } returns "Hello there"
                every { postedAt } returns utcDate(2026, 1, 30)
            },
            // Text message from us
            mockMessage(sender = Identities.ME) {
                every { body } returns "Hi!"
                every { postedAt } returns utcDate(2026, 1, 29)
            },
            // File message (downloaded, file exists)
            mockMessage(sender = Identities.ME) {
                every { type } returns MessageType.FILE
                every { fileData } returns mockk {
                    every { fileName } returns "my-file.pdf"
                    every { isDownloaded } returns true
                }
                every { apiMessageId } returns "abcd"
                every { postedAt } returns utcDate(2026, 1, 28)
                every { uid } returns "file1"
            },
        )

        val result = createExportConversationToFileUseCase(testDispatcherProvider()).call(
            conversationModel = ConversationModel(
                messageReceiverMock,
            ),
            password = PASSWORD,
            includeMedia = false,
        )

        assertIs<ExportConversationToFileUseCase.Result.Success>(result)
        verifyFilesInZipFile(
            result.file,
            expectedFiles = setOf("messages.txt"),
        )
        assertEquals(
            loadResource("conversation-export/export2.txt").trim(),
            String(getZipFileContent(result.file, "messages.txt")).trim(),
        )
    }

    private fun createExportConversationToFileUseCase(
        dispatcherProvider: DispatcherProvider,
    ): ExportConversationToFileUseCase =
        ExportConversationToFileUseCase(
            appContext = mockk {
                every { getString(R.string.me_myself_and_i) } returns "Me"
            },
            fileService = fileServiceMock,
            messageService = messageServiceMock,
            contactModelRepository = contactModelRepositoryMock,
            preferenceService = preferenceServiceMock,
            appDirectoryProvider = mockk {
                every { shareDirectory } returns tempDirectory
            },
            dispatcherProvider = dispatcherProvider,
        )

    private fun mockMessage(sender: Identity = Identities.ME, block: MessageModel.() -> Unit): MessageModel =
        mockk<MessageModel> {
            every { type } returns MessageType.TEXT
            every { isStatusMessage } returns false
            every { uid } returns "?"
            every { body } returns ""
            every { isOutbox } returns (sender == Identities.ME)
            every { identity } returns sender.value
            block()
        }

    private fun verifyFilesInZipFile(zipFile: File, expectedFiles: Set<String>) {
        val zipFile = ZipFile(zipFile, PASSWORD.toCharArray())
        assertEquals(
            expectedFiles,
            zipFile.fileHeaders.map { it.fileName }.toSet(),
        )
    }

    private fun getZipFileContent(zipFile: File, filename: String): ByteArray {
        val zipFile = ZipFile(zipFile, PASSWORD.toCharArray())
        return zipFile.getInputStream(zipFile.getFileHeader(filename))
            .use { it.readBytes() }
    }

    companion object {
        private const val PASSWORD = "12345678"
    }
}
