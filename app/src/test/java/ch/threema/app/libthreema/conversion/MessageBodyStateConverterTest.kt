package ch.threema.app.libthreema.conversion

import ch.threema.libthreema.DeletedMessageState as LibthreemaDeletedMessageState
import ch.threema.libthreema.LocationMessageState as LibthreemaLocationMessageState
import ch.threema.libthreema.MessageStateBody as LibthreemaMessageStateBody
import ch.threema.libthreema.MessageStateBodyType as LibthreemaMessageStateBodyType
import ch.threema.libthreema.PollClose as LibthreemaPollClose
import ch.threema.libthreema.PollOpen as LibthreemaPollOpen
import ch.threema.libthreema.TextMessageState as LibthreemaTextMessageState
import ch.threema.storage.factories.PollModelFactory
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.PollDataModel
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageBodyStateConverterTest {
    private lateinit var textMessageStateConverterMock: TextMessageStateConverter
    private lateinit var locationMessageStateConverterMock: LocationMessageStateConverter
    private lateinit var deletedMessageStateConverterMock: DeletedMessageStateConverter
    private lateinit var pollModelFactoryMock: PollModelFactory
    private lateinit var messageBodyStateConverter: MessageBodyStateConverter

    @BeforeTest
    fun setUp() {
        textMessageStateConverterMock = mockk()
        locationMessageStateConverterMock = mockk()
        deletedMessageStateConverterMock = mockk()
        pollModelFactoryMock = mockk()
        messageBodyStateConverter = MessageBodyStateConverter(
            textMessageStateConverter = textMessageStateConverterMock,
            locationMessageStateConverter = locationMessageStateConverterMock,
            deletedMessageStateConverter = deletedMessageStateConverterMock,
            pollModelFactory = pollModelFactoryMock,
        )
    }

    @Test
    fun `convert text`() {
        val messageBodyAndRevisionsMock = mockk<MessageBodyAndRevisions>()
        val messageStateMock = mockk<LibthreemaTextMessageState>()
        every { textMessageStateConverterMock.convertToMessageBodyAndRevisions(messageStateMock) } returns messageBodyAndRevisionsMock
        assertEquals(
            messageBodyAndRevisionsMock,
            messageBodyStateConverter.convertToMessageBodyAndRevisions(
                LibthreemaMessageStateBody.Text(messageStateMock),
            ),
        )
    }

    @Test
    fun `convert location`() {
        val messageBodyAndRevisionsMock = mockk<MessageBodyAndRevisions>()
        val messageStateMock = mockk<LibthreemaLocationMessageState>()
        every { locationMessageStateConverterMock.convertToMessageBodyAndRevisions(messageStateMock) } returns messageBodyAndRevisionsMock
        assertEquals(
            messageBodyAndRevisionsMock,
            messageBodyStateConverter.convertToMessageBodyAndRevisions(
                LibthreemaMessageStateBody.Location(messageStateMock),
            ),
        )
    }

    @Test
    fun `convert poll open`() {
        every { pollModelFactoryMock.getLocalPollIdByApiPollId("00000000000000ff") } returns 123
        val messageStateAndRevisions = messageBodyStateConverter.convertToMessageBodyAndRevisions(
            LibthreemaMessageStateBody.PollOpen(
                LibthreemaPollOpen(0xffUL),
            ),
        )
        assertEquals(
            MessageBodyAndRevisions(
                messageType = MessageType.POLL,
                originalBody = PollDataModel.createCreated(123).serialize(),
            ),
            messageStateAndRevisions,
        )
    }

    @Test
    fun `convert poll close`() {
        every { pollModelFactoryMock.getLocalPollIdByApiPollId("00000000000000ff") } returns 123
        val messageStateAndRevisions = messageBodyStateConverter.convertToMessageBodyAndRevisions(
            LibthreemaMessageStateBody.PollClose(
                LibthreemaPollClose(0xffUL),
            ),
        )
        assertEquals(
            MessageBodyAndRevisions(
                messageType = MessageType.POLL,
                originalBody = PollDataModel.createClosed(123).serialize(),
            ),
            messageStateAndRevisions,
        )
    }

    @Test
    fun `convert unknown poll`() {
        every { pollModelFactoryMock.getLocalPollIdByApiPollId(any()) } returns null
        val messageStateAndRevisions = messageBodyStateConverter.convertToMessageBodyAndRevisions(
            LibthreemaMessageStateBody.PollClose(
                LibthreemaPollClose(0xffUL),
            ),
        )
        assertEquals(
            MessageBodyAndRevisions(
                messageType = MessageType.POLL,
                originalBody = PollDataModel.createClosed(-1).serialize(),
            ),
            messageStateAndRevisions,
        )
    }

    @Test
    fun `convert deleted message`() {
        val messageBodyAndRevisionsMock = mockk<MessageBodyAndRevisions>()
        every {
            deletedMessageStateConverterMock.convertToMessageBodyAndRevisions(
                messageStateBodyType = LibthreemaMessageStateBodyType.DELETED,
                deletedAt = Instant.ofEpochMilli(1234),
            )
        } returns messageBodyAndRevisionsMock
        assertEquals(
            messageBodyAndRevisionsMock,
            messageBodyStateConverter.convertToMessageBodyAndRevisions(
                LibthreemaMessageStateBody.Deleted(
                    LibthreemaDeletedMessageState(
                        previousMessageType = LibthreemaMessageStateBodyType.DELETED,
                        deletedAt = 1234UL,
                    ),
                ),
            ),
        )
    }
}
