package ch.threema.app.services

import ch.threema.app.eventbus.EventBus
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.domain.types.Identity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockedIdentitiesServiceTest {
    private lateinit var storedList: Array<String>
    private lateinit var contactEventBusMock: EventBus<ContactEvent>
    private lateinit var blockedIdentitiesService: BlockedIdentitiesService

    @BeforeTest
    fun setUp() {
        storedList = emptyArray()
        contactEventBusMock = mockk(relaxed = true)
        blockedIdentitiesService = BlockedIdentitiesServiceImpl(
            preferenceService = mockk {
                every { getEncryptedList("identity_list_blacklist") } answers { storedList }
                every { setEncryptedList("identity_list_blacklist", any()) } answers {
                    storedList = secondArg()
                }
            },
            multiDeviceManager = mockk(relaxed = true),
            taskCreator = mockk(relaxed = true),
            globalEventBuses = mockk {
                every { contacts } returns contactEventBusMock
            },
        )
    }

    @Test
    fun `initially empty`() {
        assertTrue(blockedIdentitiesService.getAllBlockedIdentities().isEmpty())
    }

    @Test
    fun `block identities`() {
        blockedIdentitiesService.blockIdentity("ABCDEFGH")
        blockedIdentitiesService.blockIdentity("TESTTEST")

        assertTrue(blockedIdentitiesService.isBlocked("ABCDEFGH"))
        assertTrue(blockedIdentitiesService.isBlocked("TESTTEST"))

        verify(exactly = 1) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("ABCDEFGH"))) }
        verify(exactly = 1) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("TESTTEST"))) }
    }

    @Test
    fun `unblock identities`() {
        blockedIdentitiesService.blockIdentity("ABCDEFGH")
        blockedIdentitiesService.blockIdentity("TESTTEST")
        blockedIdentitiesService.unblockIdentity("ABCDEFGH")

        assertFalse(blockedIdentitiesService.isBlocked("ABCDEFGH"))
        assertTrue(blockedIdentitiesService.isBlocked("TESTTEST"))

        verify(exactly = 2) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("ABCDEFGH"))) }
        verify(exactly = 1) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("TESTTEST"))) }
    }

    @Test
    fun `persist identities`() {
        blockedIdentitiesService.persistBlockedIdentities(setOf("ABCDEFGH", "12345678"))

        verify(exactly = 1) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("ABCDEFGH"))) }
        verify(exactly = 1) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("12345678"))) }

        blockedIdentitiesService.persistBlockedIdentities(setOf("ABCDEFGH", "TESTTEST"))

        verify(exactly = 1) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("TESTTEST"))) }
        verify(exactly = 2) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("12345678"))) }

        // No other emits are emitted
        verify(exactly = 4) { contactEventBusMock.emit(any()) }
    }

    @Test
    fun `toggle blocked`() {
        blockedIdentitiesService.toggleBlocked("12345678")

        assertTrue(blockedIdentitiesService.isBlocked("12345678"))
        verify(exactly = 1) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("12345678"))) }

        blockedIdentitiesService.toggleBlocked("12345678")

        assertFalse(blockedIdentitiesService.isBlocked("12345678"))
        verify(exactly = 2) { contactEventBusMock.emit(ContactEvent.ContactUpdated(Identity("12345678"))) }

        // No other emits are emitted
        verify(exactly = 2) { contactEventBusMock.emit(any()) }
    }
}
