package ch.threema.app.conversation

import android.content.Context
import ch.threema.app.R
import ch.threema.app.services.poll.PollService
import ch.threema.app.ui.models.MessageViewElement
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.protocol.csp.messages.file.FileData
import ch.threema.domain.protocol.csp.messages.voip.VoipCallAnswerData
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.FileDataModelSerializer
import ch.threema.storage.models.data.media.PollDataModel
import ch.threema.storage.models.data.status.ForwardSecurityStatusDataModel
import ch.threema.storage.models.data.status.GroupCallStatusDataModel
import ch.threema.storage.models.data.status.GroupStatusDataModel
import ch.threema.storage.models.data.status.StatusDataModel
import ch.threema.storage.models.data.status.VoipStatusDataModel
import ch.threema.storage.models.poll.PollModel
import ch.threema.test.TestContext
import ch.threema.test.TestContext.getQuantityString
import ch.threema.test.TestContext.getString
import ch.threema.test.TestIdentityProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.koin.core.context.startKoin
import org.koin.test.ClosingKoinTest
import org.koin.test.mock.declare
import testdata.TestData

class MessageViewElementFactoryTest : ClosingKoinTest {

    private lateinit var messageViewElementFactory: MessageViewElementFactory
    private lateinit var contextMock: Context

    @BeforeTest
    fun setUp() {
        startKoin { }
        contextMock = TestContext.create()
        messageViewElementFactory = MessageViewElementFactory(
            appContext = contextMock,
            identityProvider = TestIdentityProvider(identity = TestData.Identities.ME),
        )
    }

    @Test
    fun `text message`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.TEXT,
            messageBody = "Hello world",
        )
        assertEquals(
            MessageViewElement(
                text = "Hello world",
            ),
            element,
        )
    }

    @Test
    fun `location message`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.LOCATION,
            messageBody = "",
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_location_pin_filled,
                placeholder = getString(R.string.location_placeholder),
                text = getString(R.string.location_placeholder),
            ),
            element,
        )
    }

    @Test
    fun `image message with caption`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(mimeType = "image/jpeg"),
            messageCaption = "Hello world",
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_photo_filled,
                placeholder = getString(R.string.image_placeholder),
                text = "Hello world",
            ),
            element,
        )
    }

    @Test
    fun `image message without caption`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(mimeType = "image/jpeg"),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_photo_filled,
                placeholder = getString(R.string.image_placeholder),
            ),
            element,
        )
    }

    @Test
    fun `video message with caption`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(mimeType = "video/mpeg"),
            messageCaption = "Hello world",
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_movie_filled,
                placeholder = getString(R.string.video_placeholder),
                text = "Hello world",
            ),
            element,
        )
    }

    @Test
    fun `video message without caption`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(mimeType = "video/mpeg"),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_movie_filled,
                placeholder = getString(R.string.video_placeholder),
            ),
            element,
        )
    }

    @Test
    fun `audio message`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(mimeType = "audio/aac"),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_doc_audio,
                placeholder = getString(R.string.audio_placeholder),
            ),
            element,
        )
    }

    @Test
    fun `voice message with duration`() {
        every {
            contextMock.getString(R.string.voice_message_with_duration_pattern, *anyVararg<Any>())
        } answers { "Voice message (${secondArg<Array<Any>>()[1]})" }
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(
                mimeType = "audio/aac",
                renderingType = FileData.RENDERING_MEDIA,
                duration = 63.seconds,
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_microphone,
                placeholder = getString(R.string.voice_message),
                text = "Voice message (01:03)",
            ),
            element,
        )
    }

    @Test
    fun `voice message without duration`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(
                mimeType = "audio/aac",
                renderingType = FileData.RENDERING_MEDIA,
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_microphone,
                placeholder = getString(R.string.voice_message),
                text = getString(R.string.voice_message),
            ),
            element,
        )
    }

    @Test
    fun `file message with caption`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(
                mimeType = "application/pdf",
                fileName = "my-doc.pdf",
            ),
            messageCaption = "Hello world",
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_doc_pdf,
                placeholder = getString(R.string.file_placeholder),
                text = "Hello world",
            ),
            element,
        )
    }

    @Test
    fun `file message without caption but with file name`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(
                mimeType = "application/msword",
                fileName = "my-doc.docx",
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_doc_word,
                placeholder = getString(R.string.file_placeholder),
                text = "my-doc.docx",
            ),
            element,
        )
    }

    @Test
    fun `file message without caption and without file name`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FILE,
            messageBody = createMessageBody(
                mimeType = "font/ttf",
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_doc_font,
                placeholder = getString(R.string.file_placeholder),
            ),
            element,
        )
    }

    @Test
    fun `poll message for open poll`() {
        val pollId = 123
        declare<PollService> {
            mockk {
                every { get(pollId) } returns mockk {
                    every { state } returns PollModel.State.OPEN
                    every { name } returns "What?"
                }
            }
        }
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.POLL,
            messageBody = "[${PollDataModel.Type.POLL_CREATED.id},$pollId]",
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_baseline_rule,
                placeholder = getString(R.string.ballot_placeholder),
                text = "What?",
            ),
            element,
        )
    }

    @Test
    fun `poll message for closed poll`() {
        val pollId = 123
        declare<PollService> {
            mockk {
                every { get(pollId) } returns mockk {
                    every { state } returns PollModel.State.CLOSED
                    every { name } returns "What?"
                }
            }
        }
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.POLL,
            messageBody = "[${PollDataModel.Type.POLL_CREATED.id},$pollId]",
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_baseline_rule,
                placeholder = getString(R.string.ballot_placeholder),
                text = getString(R.string.ballot_message_closed),
            ),
            element,
        )
    }

    @Test
    fun `group status`() {
        val displayName = "Pedro Ramirez"
        declare<ContactModelRepository> {
            mockk {
                every { getByIdentity(TestData.Identities.OTHER_1.value) } returns mockk {
                    every { data } returns mockk {
                        every { getDisplayName(ContactNameFormat.FIRSTNAME_LASTNAME, true) } returns displayName
                    }
                }
            }
        }
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.GROUP_STATUS,
            messageBody = StatusDataModel.serialize(
                GroupStatusDataModel.create(
                    type = GroupStatusDataModel.GroupStatusType.MEMBER_ADDED,
                    identity = TestData.Identities.OTHER_1.value,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                placeholder = getString(R.string.status_group_new_member, displayName),
                text = getString(R.string.status_group_new_member, displayName),
            ),
            element,
        )
    }

    @Test
    fun `incoming rejected voip call, busy`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.VOIP_STATUS,
            messageBody = StatusDataModel.serialize(
                VoipStatusDataModel.createRejected(
                    callId = 123,
                    reason = VoipCallAnswerData.RejectReason.BUSY,
                ),
            ),
        )
        val expectedPlaceholder = getString(
            R.string.missed_call_with_detail_pattern,
            getString(R.string.voip_call_status_missed),
            getString(R.string.voip_call_status_busy_short),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_call_missed_black_24dp,
                placeholder = expectedPlaceholder,
                text = expectedPlaceholder,
                iconTint = R.color.material_red,
            ),
            element,
        )
    }

    @Test
    fun `incoming rejected voip call, off-hours`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.VOIP_STATUS,
            messageBody = StatusDataModel.serialize(
                VoipStatusDataModel.createRejected(
                    callId = 123,
                    reason = VoipCallAnswerData.RejectReason.OFF_HOURS,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_call_missed_black_24dp,
                placeholder = getString(R.string.voip_call_status_off_hours),
                text = getString(R.string.voip_call_status_off_hours),
                iconTint = R.color.material_orange,
            ),
            element,
        )
    }

    @Test
    fun `outgoing rejected voip call, timeout`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.VOIP_STATUS,
            messageBody = StatusDataModel.serialize(
                VoipStatusDataModel.createRejected(
                    callId = 123,
                    reason = VoipCallAnswerData.RejectReason.TIMEOUT,
                ),
            ),
            isOutbox = true,
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_call_missed_outgoing_black_24dp,
                placeholder = getString(R.string.voip_call_status_unavailable),
                text = getString(R.string.voip_call_status_unavailable),
                iconTint = R.color.material_red,
            ),
            element,
        )
    }

    @Test
    fun `outgoing rejected voip call, unknown reason`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.VOIP_STATUS,
            messageBody = StatusDataModel.serialize(
                VoipStatusDataModel.createRejected(
                    callId = 123,
                    reason = VoipCallAnswerData.RejectReason.UNKNOWN,
                ),
            ),
            isOutbox = true,
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_call_missed_outgoing_black_24dp,
                placeholder = getString(R.string.voip_call_status_rejected),
                text = getString(R.string.voip_call_status_rejected),
                iconTint = R.color.material_red,
            ),
            element,
        )
    }

    @Test
    fun `aborted call`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.VOIP_STATUS,
            messageBody = StatusDataModel.serialize(
                VoipStatusDataModel.createAborted(callId = 123),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_call_missed_outgoing_black_24dp,
                placeholder = getString(R.string.voip_call_status_aborted),
                text = getString(R.string.voip_call_status_aborted),
                iconTint = R.color.material_orange,
            ),
            element,
        )
    }

    @Test
    fun `missed incoming call`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.VOIP_STATUS,
            messageBody = StatusDataModel.serialize(
                VoipStatusDataModel.createMissed(callId = 123),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_call_missed_black_24dp,
                placeholder = getString(R.string.voip_call_status_missed),
                text = getString(R.string.voip_call_status_missed),
                iconTint = R.color.material_red,
            ),
            element,
        )
    }

    @Test
    fun `missed outgoing call`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.VOIP_STATUS,
            messageBody = StatusDataModel.serialize(
                VoipStatusDataModel.createMissed(callId = 123),
            ),
            isOutbox = true,
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_call_missed_outgoing_black_24dp,
                placeholder = getString(R.string.voip_call_status_missed),
                text = getString(R.string.voip_call_status_missed),
                iconTint = R.color.material_red,
            ),
            element,
        )
    }

    @Test
    fun `finished incoming call`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.VOIP_STATUS,
            messageBody = StatusDataModel.serialize(
                VoipStatusDataModel.createFinished(
                    callId = 123,
                    duration = 3.seconds,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_call_received_black_24dp,
                placeholder = getString(R.string.voip_call_finished_inbox),
                text = getString(R.string.voip_call_finished_inbox),
                iconTint = R.color.material_green,
            ),
            element,
        )
    }

    @Test
    fun `finished outgoing call`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.VOIP_STATUS,
            messageBody = StatusDataModel.serialize(
                VoipStatusDataModel.createFinished(
                    callId = 123,
                    duration = 3.seconds,
                ),
            ),
            isOutbox = true,
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_call_made_black_24dp,
                placeholder = getString(R.string.voip_call_finished_outbox),
                text = getString(R.string.voip_call_finished_outbox),
                iconTint = R.color.material_green,
            ),
            element,
        )
    }

    @Test
    fun `started group call, with known name`() {
        val shortName = "Pedro"
        declare<ContactModelRepository> {
            mockk {
                every { getByIdentity(TestData.Identities.OTHER_1.value) } returns mockk {
                    every { data } returns mockk {
                        every { getShortName() } returns shortName
                    }
                }
            }
        }
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.GROUP_CALL_STATUS,
            messageBody = StatusDataModel.serialize(
                GroupCallStatusDataModel.createStarted(
                    callId = "123",
                    groupId = 42,
                    callerIdentity = TestData.Identities.OTHER_1.value,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_phone_locked_outline,
                placeholder = getString(R.string.voip_gc_call_started),
                text = getString(R.string.voip_gc_notification_call_started_generic, shortName),
            ),
            element,
        )
    }

    @Test
    fun `started group call, without known name`() {
        declare<ContactModelRepository> {
            mockk {
                every { getByIdentity(TestData.Identities.OTHER_1.value) } returns null
            }
        }
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.GROUP_CALL_STATUS,
            messageBody = StatusDataModel.serialize(
                GroupCallStatusDataModel.createStarted(
                    callId = "123",
                    groupId = 42,
                    callerIdentity = TestData.Identities.OTHER_1.value,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_phone_locked_outline,
                placeholder = getString(R.string.voip_gc_call_started),
                text = getString(R.string.voip_gc_call_started),
            ),
            element,
        )
    }

    @Test
    fun `ended group call`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.GROUP_CALL_STATUS,
            messageBody = StatusDataModel.serialize(
                GroupCallStatusDataModel.createEnded(
                    callId = "123",
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_phone_locked_outline,
                placeholder = getString(R.string.voip_gc_call_ended),
                text = getString(R.string.voip_gc_call_ended),
            ),
            element,
        )
    }

    @Test
    fun `forward security reset`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FORWARD_SECURITY_STATUS,
            messageBody = StatusDataModel.serialize(
                ForwardSecurityStatusDataModel.create(
                    statusType = ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_RESET,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_key_off,
                placeholder = getString(R.string.forward_security_reset_simple),
                text = getString(R.string.forward_security_reset_simple),
                iconTint = R.color.material_red,
            ),
            element,
        )
    }

    @Test
    fun `forward security established`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FORWARD_SECURITY_STATUS,
            messageBody = StatusDataModel.serialize(
                ForwardSecurityStatusDataModel.create(
                    statusType = ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_ESTABLISHED,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_baseline_key_24,
                placeholder = getString(R.string.forward_security_established),
                text = getString(R.string.forward_security_established),
                iconTint = R.color.material_green,
            ),
            element,
        )
    }

    @Test
    fun `forward security messages skipped`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FORWARD_SECURITY_STATUS,
            messageBody = StatusDataModel.serialize(
                ForwardSecurityStatusDataModel.create(
                    statusType = ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGES_SKIPPED,
                    quantity = 2,
                ),
            ),
        )
        val expectedPlaceholder = getQuantityString(R.plurals.forward_security_messages_skipped, 2, 2)
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_baseline_key_24,
                placeholder = expectedPlaceholder,
                text = expectedPlaceholder,
            ),
            element,
        )
    }

    @Test
    fun `forward security messages out of order`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FORWARD_SECURITY_STATUS,
            messageBody = StatusDataModel.serialize(
                ForwardSecurityStatusDataModel.create(
                    statusType = ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_MESSAGE_OUT_OF_ORDER,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_baseline_key_24,
                placeholder = getString(R.string.forward_security_message_out_of_order),
                text = getString(R.string.forward_security_message_out_of_order),
            ),
            element,
        )
    }

    @Test
    fun `forward security messages, message without forward security`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FORWARD_SECURITY_STATUS,
            messageBody = StatusDataModel.serialize(
                ForwardSecurityStatusDataModel.create(
                    statusType = ForwardSecurityStatusDataModel.ForwardSecurityStatusType.MESSAGE_WITHOUT_FORWARD_SECURITY,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_key_off,
                placeholder = getString(R.string.message_without_forward_security),
                text = getString(R.string.message_without_forward_security),
            ),
            element,
        )
    }

    @Test
    fun `forward security messages, unavailable downgrade`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FORWARD_SECURITY_STATUS,
            messageBody = StatusDataModel.serialize(
                ForwardSecurityStatusDataModel.create(
                    statusType = ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_UNAVAILABLE_DOWNGRADE,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_baseline_key_24,
                placeholder = getString(R.string.forward_security_downgraded_status_message),
                text = getString(R.string.forward_security_downgraded_status_message),
            ),
            element,
        )
    }

    @Test
    fun `forward security messages, illegal session state`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FORWARD_SECURITY_STATUS,
            messageBody = StatusDataModel.serialize(
                ForwardSecurityStatusDataModel.create(
                    statusType = ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_ILLEGAL_SESSION_STATE,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_key_off,
                placeholder = getString(R.string.forward_security_illegal_session_status_message),
                text = getString(R.string.forward_security_illegal_session_status_message),
            ),
            element,
        )
    }

    @Test
    fun `forward security messages disabled`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FORWARD_SECURITY_STATUS,
            messageBody = StatusDataModel.serialize(
                ForwardSecurityStatusDataModel.create(
                    statusType = ForwardSecurityStatusDataModel.ForwardSecurityStatusType.FORWARD_SECURITY_DISABLED,
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_key_off,
                placeholder = getString(R.string.forward_security_disabled),
                text = getString(R.string.forward_security_disabled),
            ),
            element,
        )
    }

    @Test
    fun `forward security messages disabled, static text`() {
        val element = messageViewElementFactory.getElement(
            messageType = MessageType.FORWARD_SECURITY_STATUS,
            messageBody = StatusDataModel.serialize(
                ForwardSecurityStatusDataModel.create(
                    statusType = ForwardSecurityStatusDataModel.ForwardSecurityStatusType.STATIC_TEXT,
                    staticText = "Hello world",
                ),
            ),
        )
        assertEquals(
            MessageViewElement(
                icon = R.drawable.ic_baseline_key_24,
                placeholder = "Hello world",
                text = "Hello world",
            ),
            element,
        )
    }

    companion object {
        private fun createMessageBody(
            mimeType: String,
            renderingType: Int = FileData.RENDERING_DEFAULT,
            fileName: String? = null,
            duration: Duration? = null,
        ) =
            FileDataModelSerializer.serializeFileDataBody(
                blobId = null,
                encryptionKey = null,
                mimeType = mimeType,
                renderingType = renderingType,
                fileName = fileName,
                metaData = duration?.let {
                    mapOf("d" to duration.inWholeSeconds)
                },
            )

        private fun MessageViewElementFactory.getElement(
            messageType: MessageType,
            messageBody: String? = null,
            messageCaption: String? = null,
            isOutbox: Boolean = false,
        ) = getViewElement(
            messageType = messageType,
            messageBody = messageBody,
            messageCaption = messageCaption,
            isOutbox = isOutbox,
            contactNameFormat = ContactNameFormat.FIRSTNAME_LASTNAME,
        )
    }
}
