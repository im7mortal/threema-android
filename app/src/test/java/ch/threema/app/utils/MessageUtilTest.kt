package ch.threema.app.utils

import ch.threema.app.AppConstants
import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.common.minus
import ch.threema.common.plus
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.UserState
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.protocol.csp.messages.DeleteMessage
import ch.threema.domain.protocol.csp.messages.file.FileData.RenderingType
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import ch.threema.storage.models.MessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.media.FileDataModel
import ch.threema.testhelpers.nonSecureRandomArray
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import testdata.TestData

class MessageUtilTest {
    private lateinit var contactMessageModelInbox: ContactMessageModel
    private lateinit var contactMessageModelInboxUserAcknowledged: ContactMessageModel
    private lateinit var contactMessageModelInboxUserDeclined: ContactMessageModel
    private lateinit var contactMessageModelOutbox: ContactMessageModel
    private lateinit var contactMessageModelOutboxUserAcknowledged: ContactMessageModel
    private lateinit var contactMessageModelOutboxUserDeclined: ContactMessageModel

    private lateinit var businessContactMessageModelInbox: ContactMessageModel
    private lateinit var businessContactMessageModelInboxUserAcknowledged: ContactMessageModel
    private lateinit var businessContactMessageModelInboxUserDeclined: ContactMessageModel
    private lateinit var businessContactMessageModelOutbox: ContactMessageModel
    private lateinit var businessContactMessageModelOutboxUserAcknowledged: ContactMessageModel
    private lateinit var businessContactMessageModelOutboxUserDeclined: ContactMessageModel

    private lateinit var groupMessageModelInbox: GroupMessageModel
    private lateinit var groupMessageModelOutbox: GroupMessageModel

    private lateinit var distributionListMessageModelOutbox: DistributionListMessageModel

    private val multiDeviceManagerMock: MultiDeviceManager = mockk<MultiDeviceManager>()
    private val taskManagerMock: TaskManager = mockk<TaskManager>()

    private val blockedIdentitiesServiceMock = mockk<BlockedIdentitiesService>()
    private val serviceManagerMock: ServiceManager = mockk<ServiceManager> {
        every { multiDeviceManager } returns multiDeviceManagerMock
        every { taskManager } returns taskManagerMock
    }

    private val databaseBackendMock: DatabaseBackend = mockk<DatabaseBackend>()
    private val contactModelRepositoryMock: ContactModelRepository = mockk<ContactModelRepository>()

    @Suppress("DEPRECATION")
    @BeforeTest
    fun setUp() {
        mockkStatic(ConfigUtils::class)

        val contactThreemaId = AppConstants.ECHO_USER_IDENTITY
        val businessContactThreemaId = "*THREEMA"

        // mock object
        this.contactMessageModelInbox = ContactMessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
        }

        this.contactMessageModelInboxUserAcknowledged = ContactMessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
            state = MessageState.USERACK
        }

        this.contactMessageModelInboxUserDeclined = ContactMessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
            state = MessageState.USERDEC
        }

        this.contactMessageModelOutbox = ContactMessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
        }

        this.contactMessageModelOutboxUserAcknowledged = ContactMessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
            state = MessageState.USERACK
        }

        this.contactMessageModelOutboxUserDeclined = ContactMessageModel().apply {
            identity = contactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
            state = MessageState.USERDEC
        }

        this.businessContactMessageModelInbox = ContactMessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
        }

        this.businessContactMessageModelInboxUserAcknowledged = ContactMessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
            state = MessageState.USERACK
        }

        this.businessContactMessageModelInboxUserDeclined = ContactMessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
            state = MessageState.USERDEC
        }

        this.businessContactMessageModelOutbox = ContactMessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
        }

        this.businessContactMessageModelOutboxUserAcknowledged = ContactMessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
            state = MessageState.USERACK
        }

        this.businessContactMessageModelOutboxUserDeclined = ContactMessageModel().apply {
            identity = businessContactThreemaId
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
            state = MessageState.USERDEC
        }

        this.groupMessageModelInbox = GroupMessageModel().apply {
            isSaved = true
            isOutbox = false
            type = MessageType.TEXT
        }

        this.groupMessageModelOutbox = GroupMessageModel().apply {
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
        }

        this.distributionListMessageModelOutbox = DistributionListMessageModel().apply {
            isSaved = true
            isOutbox = true
            type = MessageType.TEXT
        }
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic(ConfigUtils::class)
    }

    @Test
    fun canSendDeliveryReceipt() {
        assertTrue(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertTrue(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelInboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertFalse(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelOutbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertFalse(MessageUtil.canSendDeliveryReceipt(this.contactMessageModelOutboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertTrue(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertTrue(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelInboxUserAcknowledged, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertFalse(MessageUtil.canSendDeliveryReceipt(this.businessContactMessageModelOutbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
        assertFalse(
            MessageUtil.canSendDeliveryReceipt(
                this.businessContactMessageModelOutboxUserAcknowledged,
                ProtocolDefines.DELIVERYRECEIPT_MSGREAD,
            ),
        )
        assertFalse(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))

        assertTrue(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERACK))
        assertTrue(MessageUtil.canSendDeliveryReceipt(this.groupMessageModelInbox, ProtocolDefines.DELIVERYRECEIPT_MSGUSERDEC))

        assertFalse(MessageUtil.canSendDeliveryReceipt(this.distributionListMessageModelOutbox, ProtocolDefines.DELIVERYRECEIPT_MSGREAD))
    }

    /**
     * User acknowledge can be sent following message setups:
     * - User, Inbox, Not acknowledged
     * - Business, Inbox acknowledged
     */
    @Test
    fun canSendUserAcknowledge() {
        // contact message (inbox) can be acknowledge by the user
        assertTrue(MessageUtil.canSendUserAcknowledge(this.contactMessageModelInbox))

        // contact message (outbox) can be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.contactMessageModelOutbox))

        // contact message (inbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.contactMessageModelInboxUserAcknowledged))

        // contact message (outbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.contactMessageModelOutboxUserAcknowledged))

        // business contact message (inbox) can be acknowledge by the user
        assertTrue(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelInbox))

        // business contact message (outbox) can be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelOutbox))

        // business contact message (inbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelInboxUserAcknowledged))

        // business contact message (outbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.businessContactMessageModelOutboxUserAcknowledged))

        // group message (inbox) can be acknowledged by the user
        assertTrue(MessageUtil.canSendUserAcknowledge(this.groupMessageModelInbox))

        // group message (outbox) can be acknowledged by the user
        assertTrue(MessageUtil.canSendUserAcknowledge(this.groupMessageModelOutbox))

        // group message (outbox) can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserAcknowledge(this.distributionListMessageModelOutbox))
    }

    /**
     * User decline can be sent following message setups:
     * - User, Inbox, Not acknowledged
     * - Business, Inbox acknowledged
     */
    @Test
    fun canSendUserDecline() {
        // contact message (inbox) can be decline by the user
        assertTrue(MessageUtil.canSendUserDecline(this.contactMessageModelInbox))

        // contact message (outbox) can be decline by the user
        assertFalse(MessageUtil.canSendUserDecline(this.contactMessageModelOutbox))

        // contact message (inbox) with state UserDec can not be decline by the user
        assertFalse(MessageUtil.canSendUserDecline(this.contactMessageModelInboxUserDeclined))

        // contact message (outbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.contactMessageModelOutboxUserDeclined))

        // business contact message (inbox) can be acknowledge by the user
        assertTrue(MessageUtil.canSendUserDecline(this.businessContactMessageModelInbox))

        // business contact message (outbox) can be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.businessContactMessageModelOutbox))

        // business contact message (inbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.businessContactMessageModelInboxUserDeclined))

        // business contact message (outbox) with state UserAck can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.businessContactMessageModelOutboxUserDeclined))

        // group message (inbox) can be declined by the user
        assertTrue(MessageUtil.canSendUserDecline(this.groupMessageModelInbox))

        // group message (outbox) can be declined by the user
        assertTrue(MessageUtil.canSendUserDecline(this.groupMessageModelOutbox))

        // group message (outbox) can not be acknowledge by the user
        assertFalse(MessageUtil.canSendUserDecline(this.distributionListMessageModelOutbox))
    }

    /**
     * status icons show for following message setups:
     * - User, Outbox
     * - User, Inbox, User acknowledged
     * - Business, Inbox, User acknowledged
     * - Business|Group|Distribution List, outbox, send failed|pending|sending
     */
    @Test
    fun showStatusIcon() {
        assertFalse(MessageUtil.showStatusIcon(this.contactMessageModelInbox))
        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.contactMessageModelInboxUserAcknowledged))
        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutboxUserAcknowledged))

        assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelInbox))
        assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelInboxUserAcknowledged))
        assertFalse(MessageUtil.showStatusIcon(this.businessContactMessageModelOutboxUserAcknowledged))

        assertFalse(MessageUtil.showStatusIcon(this.groupMessageModelInbox))
        assertFalse(MessageUtil.showStatusIcon(this.groupMessageModelOutbox))

        assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox))

        // all types in state sending
        contactMessageModelOutbox.state = MessageState.SENDING
        businessContactMessageModelOutbox.state = MessageState.SENDING
        groupMessageModelOutbox.state = MessageState.SENDING
        distributionListMessageModelOutbox.state = MessageState.SENDING

        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.groupMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox))

        // all types in state failed
        contactMessageModelOutbox.state = MessageState.SENDFAILED
        businessContactMessageModelOutbox.state = MessageState.SENDFAILED
        groupMessageModelOutbox.state = MessageState.SENDFAILED
        distributionListMessageModelOutbox.state = MessageState.SENDFAILED

        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.groupMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox))

        // all types in state pending
        contactMessageModelOutbox.state = MessageState.PENDING
        businessContactMessageModelOutbox.state = MessageState.PENDING
        groupMessageModelOutbox.state = MessageState.PENDING
        distributionListMessageModelOutbox.state = MessageState.PENDING

        assertTrue(MessageUtil.showStatusIcon(this.contactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.businessContactMessageModelOutbox))
        assertTrue(MessageUtil.showStatusIcon(this.groupMessageModelOutbox))
        assertFalse(MessageUtil.showStatusIcon(this.distributionListMessageModelOutbox))
    }

    @Test
    fun isUnread() {
        assertTrue(
            MessageUtil.isUnread(
                ContactMessageModel().apply {
                    isOutbox = false
                    type = MessageType.TEXT
                },
            ),
        )
        assertFalse(
            MessageUtil.isUnread(
                ContactMessageModel().apply {
                    isOutbox = true
                    type = MessageType.TEXT
                },
            ),
        )

        assertFalse(
            MessageUtil.isUnread(
                ContactMessageModel().apply {
                    isOutbox = false
                    type = MessageType.STATUS
                },
            ),
        )

        assertFalse(MessageUtil.isUnread(null))
    }

    @Test
    fun canMarkAsRead() {
        assertTrue(
            MessageUtil.canMarkAsRead(
                ContactMessageModel().apply {
                    isRead = false
                    isOutbox = false
                },
            ),
        )
        assertFalse(
            MessageUtil.canMarkAsRead(
                ContactMessageModel().apply {
                    isRead = true
                    isOutbox = false
                },
            ),
        )
        assertFalse(
            MessageUtil.canMarkAsRead(
                ContactMessageModel().apply {
                    isRead = false
                    isOutbox = true
                },
            ),
        )

        assertFalse(
            MessageUtil.canMarkAsRead(
                ContactMessageModel().apply {
                    isRead = true
                    isOutbox = true
                },
            ),
        )
    }

    @Test
    fun allReceivers_message_without_affected_receivers() {
        val contactMessageReceiver = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )

        // Message receiver without affected receivers
        val allReceivers: List<MessageReceiver<*>> = MessageUtil.getAllReceivers(contactMessageReceiver)
        assertEquals(1, allReceivers.size)
        assertEquals(contactMessageReceiver, allReceivers[0])
    }

    @Test
    fun allReceivers_message_with_affected_receivers() {
        val identities = listOf(TestData.Identities.OTHER_1, TestData.Identities.OTHER_2, TestData.Identities.OTHER_3)
        val distributionListReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = identities.map { identity ->
                identity to nonSecureRandomArray(32)
            },
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )
        val allReceivers: List<MessageReceiver<*>> = MessageUtil.getAllReceivers(distributionListReceiver)
        assertEquals(4, allReceivers.size)
        assertEquals(distributionListReceiver, allReceivers[0])
        identities.forEachIndexed { index: Int, identity ->
            assertEquals(identity.value, allReceivers[index + 1].identities[0])
        }
    }

    @Test
    fun addDistributionListReceivers_empty_message_receivers_list() {
        assertEquals(0, MessageUtil.addDistributionListReceivers(arrayOfNulls(0)).size)
    }

    @Test
    fun addDistributionListReceivers_must_contain_passed_receivers() {
        val contactMessageReceiver: MessageReceiver<*> = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val distributionListReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = emptyList(),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )
        val resolvedReceivers = MessageUtil.addDistributionListReceivers(
            arrayOf(
                contactMessageReceiver,
                distributionListReceiver,
            ),
        )
        assertEquals(2, resolvedReceivers.size)
        assertEquals(contactMessageReceiver, resolvedReceivers[0])
        assertEquals(distributionListReceiver, resolvedReceivers[1])
    }

    @Test
    fun addDistributionListReceivers_must_preserve_order_of_receivers() {
        val contactMessageReceiver1: MessageReceiver<*> = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            publicKey = nonSecureRandomArray(32),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val contactMessageReceiver2: MessageReceiver<*> = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_2,
            publicKey = nonSecureRandomArray(32),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val contactMessageReceiver3: MessageReceiver<*> = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_3,
            publicKey = nonSecureRandomArray(32),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val emptyDistributionListMessageReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = emptyList(),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )
        val distributionListMessageReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 2L,
            identitiesWithPublicKey = listOf(
                TestData.Identities.OTHER_4 to TestData.testPublicKey,
                TestData.Identities.OTHER_5 to TestData.testPublicKey,
            ),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )

        val receivers = arrayOf(
            contactMessageReceiver1,
            emptyDistributionListMessageReceiver,
            contactMessageReceiver2,
            distributionListMessageReceiver,
            contactMessageReceiver3,
        )

        val resolvedReceivers = MessageUtil.addDistributionListReceivers(receivers)

        assertEquals(7, resolvedReceivers.size)
        assertEquals(contactMessageReceiver1, resolvedReceivers[0])
        assertEquals(emptyDistributionListMessageReceiver, resolvedReceivers[1])
        assertEquals(contactMessageReceiver2, resolvedReceivers[2])
        assertEquals(distributionListMessageReceiver, resolvedReceivers[3])
        assertEquals(TestData.Identities.OTHER_4.value, resolvedReceivers[4].identities[0])
        assertEquals(TestData.Identities.OTHER_5.value, resolvedReceivers[5].identities[0])
        assertEquals(contactMessageReceiver3, resolvedReceivers[6])
    }

    @Test
    fun addDistributionListReceivers_must_preserve_order_of_receivers_and_remove_duplicates() {
        val contactMessageReceiver1 = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val contactMessageReceiver2 = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_2,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val contactMessageReceiver3 = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_3,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )

        val duplicate1 = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )

        val emptyDistributionListMessageReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = emptyList(),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )
        val distributionListMessageReceiver = TestData.createAndMockDistributionListMessageReceiver(
            distributionListId = 1L,
            identitiesWithPublicKey = listOf(
                TestData.Identities.OTHER_4 to TestData.testPublicKey,
                TestData.Identities.OTHER_5 to TestData.testPublicKey,
                TestData.Identities.OTHER_2 to TestData.testPublicKey,
                TestData.Identities.OTHER_3 to TestData.testPublicKey,
            ),
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
        )

        val receivers = arrayOf(
            contactMessageReceiver1,
            duplicate1,
            emptyDistributionListMessageReceiver,
            contactMessageReceiver2,
            distributionListMessageReceiver,
            contactMessageReceiver3,
            distributionListMessageReceiver,
        )

        val resolvedReceivers = MessageUtil.addDistributionListReceivers(receivers)

        assertEquals(7, resolvedReceivers.size)
        assertEquals(contactMessageReceiver1, resolvedReceivers[0])
        assertEquals(emptyDistributionListMessageReceiver, resolvedReceivers[1])
        assertEquals(contactMessageReceiver2, resolvedReceivers[2])
        assertEquals(distributionListMessageReceiver, resolvedReceivers[3])
        assertEquals(TestData.Identities.OTHER_4.value, resolvedReceivers[4].identities[0])
        assertEquals(TestData.Identities.OTHER_5.value, resolvedReceivers[5].identities[0])
        assertEquals(TestData.Identities.OTHER_3.value, resolvedReceivers[6].identities[0])
    }

    @Test
    fun `canDeleteRemotely respects message age for contact receiver`() {
        // arrange
        val contactReceiver: ContactMessageReceiver = TestData.createAndMockContactMessageReceiver(
            identity = TestData.Identities.OTHER_1,
            contactModelRepositoryMock = contactModelRepositoryMock,
            databaseBackendMock = databaseBackendMock,
            blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val messageCreatedAtTooOld = Instant.now() -
            DeleteMessage.DELETE_MESSAGES_MAX_AGE.milliseconds -
            1.days
        val message: MessageModel = createMessageModel(
            createdAt = messageCreatedAtTooOld,
            postedAt = messageCreatedAtTooOld + 100.milliseconds,
        )

        // act
        val result: Boolean = MessageUtil.canDeleteRemotely(message, contactReceiver)

        // assert
        assertFalse(result)
    }

    @Test
    fun `canDeleteRemotely respects old message age for group receiver`() {
        // arrange
        val ownIdentity = TestData.Identities.ME
        val creatorIdentity = TestData.Identities.OTHER_1
        val groupMessageReceiver: GroupMessageReceiver = TestData.createAndMockGroupMessageReceiver(
            groupDatabaseId = 1L,
            ownIdentity = ownIdentity,
            creatorIdentity = creatorIdentity,
            userState = UserState.MEMBER,
            otherMembers = emptySet(),
            databaseBackendMock = databaseBackendMock,
            contactModelRepositoryMock = contactModelRepositoryMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val messageCreatedAtTooOld: Instant = Instant.now() -
            DeleteMessage.DELETE_MESSAGES_MAX_AGE.milliseconds -
            1.days
        val message: MessageModel = createMessageModel(
            createdAt = messageCreatedAtTooOld,
            postedAt = messageCreatedAtTooOld.plus(100.milliseconds),
        )

        // act
        val result: Boolean = MessageUtil.canDeleteRemotely(message, groupMessageReceiver)

        // assert
        assertFalse(result)
    }

    @Test
    fun `canDeleteRemotely returns true for young message age for group receiver`() {
        // arrange
        val ownIdentity = TestData.Identities.ME
        val creatorIdentity = TestData.Identities.OTHER_1
        val groupMessageReceiver: GroupMessageReceiver = TestData.createAndMockGroupMessageReceiver(
            groupDatabaseId = 1L,
            ownIdentity = ownIdentity,
            creatorIdentity = creatorIdentity,
            userState = UserState.MEMBER,
            otherMembers = emptySet(),
            databaseBackendMock = databaseBackendMock,
            contactModelRepositoryMock = contactModelRepositoryMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val messageCreatedAtYoungEnough = Instant.now() - (DeleteMessage.DELETE_MESSAGES_MAX_AGE / 2).milliseconds
        val message: MessageModel = createMessageModel(
            createdAt = messageCreatedAtYoungEnough,
            postedAt = messageCreatedAtYoungEnough.plus(100.milliseconds),
        )

        // act
        val result: Boolean = MessageUtil.canDeleteRemotely(message, groupMessageReceiver)

        // assert
        assertTrue(result)
    }

    @Test
    fun `canDeleteRemotely ignores message age for notes group`() {
        // arrange
        val ownIdentity = TestData.Identities.ME
        val groupMessageReceiver: GroupMessageReceiver = TestData.createAndMockGroupMessageReceiver(
            groupDatabaseId = 1L,
            ownIdentity = ownIdentity,
            creatorIdentity = ownIdentity,
            userState = UserState.MEMBER,
            otherMembers = emptySet(),
            databaseBackendMock = databaseBackendMock,
            contactModelRepositoryMock = contactModelRepositoryMock,
            serviceManagerMock = serviceManagerMock,
            multiDeviceManager = multiDeviceManagerMock,
            taskManager = taskManagerMock,
        )
        val messageCreatedAtTooOld = Instant.now() -
            DeleteMessage.DELETE_MESSAGES_MAX_AGE.milliseconds -
            1.days
        val message: MessageModel = createMessageModel(
            createdAt = messageCreatedAtTooOld,
            postedAt = messageCreatedAtTooOld.plus(100.milliseconds),
        )

        // act
        val result: Boolean = MessageUtil.canDeleteRemotely(message, groupMessageReceiver)

        // assert
        assertTrue(result)
    }

    private fun messageModelWithType(messageType: MessageType) = ContactMessageModel().apply {
        type = messageType
    }

    companion object {
        private fun createFileDataModel(@RenderingType renderingType: Int) = FileDataModel(
            /* mimeType = */
            "image/jpeg",
            /* thumbnailMimeType = */
            "image/jpeg",
            /* fileSize = */
            100,
            /* fileName = */
            null,
            /* renderingType = */
            renderingType,
            /* caption = */
            "A photo without name",
            /* isDownloaded = */
            true,
            /* metaData = */
            HashMap(),
        )

        private fun createMessageModel(
            createdAt: Instant?,
            postedAt: Instant? = null,
        ): MessageModel {
            return mockk<ContactMessageModel> {
                val message = this
                every { message.type } returns MessageType.TEXT
                every { message.isStatusMessage } returns false
                every { message.isDeleted } returns false
                every { message.isOutbox } returns true
                every { message.createdAt } returns createdAt
                every { message.postedAt } returns postedAt
                every { message.state } returns MessageState.SENT
            }
        }
    }
}
