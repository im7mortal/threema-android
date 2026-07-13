package ch.threema.app.usecases.contact

import app.cash.turbine.test
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.ProfileEvent
import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.MentionNameData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.unconfinedTestDispatcherProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import testdata.TestData

class WatchAllMentionNamesUseCaseTest {

    @Test
    fun `should emit current value`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns TestData.Identities.ME
            every { getIdentityString() } returns TestData.Identities.ME.value
            every { getPublicNickname() } returns ""
        }

        val contactModelRepositoryMock = mockk<ContactModelRepository>()
        val contact1 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_1,
        )
        val contact2 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_2,
        )
        every { contactModelRepositoryMock.getAll() } returns listOf(contact1, contact2)

        val useCase = WatchAllMentionNamesUseCase(
            globalEventFlows = mockk {
                every { contacts } returns MutableSharedFlow()
                every { profiles } returns MutableSharedFlow()
            },
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "",
                    ),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit current value without own user data`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns null
            every { getIdentityString() } returns null
        }

        val contactModelRepositoryMock = mockk<ContactModelRepository>()
        val contact1 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_1,
        )
        val contact2 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_2,
        )
        every { contactModelRepositoryMock.getAll() } returns listOf(contact1, contact2)

        val useCase = WatchAllMentionNamesUseCase(
            globalEventFlows = mockk {
                every { contacts } returns MutableSharedFlow()
                every { profiles } returns MutableSharedFlow()
            },
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit current value with own current nickname`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns TestData.Identities.ME
            every { getIdentityString() } returns TestData.Identities.ME.value
            every { getPublicNickname() } returns "Nickname"
        }
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns emptyList()
        }
        val useCase = WatchAllMentionNamesUseCase(
            globalEventFlows = mockk {
                every { contacts } returns MutableSharedFlow()
                every { profiles } returns MutableSharedFlow()
            },
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(
                listOf(
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "Nickname",
                    ),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit current value no data`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns null
            every { getIdentityString() } returns null
        }
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns emptyList()
        }
        val useCase = WatchAllMentionNamesUseCase(
            globalEventFlows = mockk {
                every { contacts } returns MutableSharedFlow()
                every { profiles } returns MutableSharedFlow()
            },
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(emptyList())

            // Expect no more
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `should emit updated values`() = runTest {
        // arrange
        val identityStoreMock = mockk<IdentityStore> {
            every { getIdentity() } returns TestData.Identities.ME
            every { getIdentityString() } returns TestData.Identities.ME.value
            every { getPublicNickname() } returns "Nickname"
        }
        val contactModelRepositoryMock = mockk<ContactModelRepository>()
        val contact1 = TestData.createContactModel(
            identity = TestData.Identities.OTHER_1,
        )
        every { contactModelRepositoryMock.getAll() } returns listOf(contact1)
        val contactEvents = MutableSharedFlow<ContactEvent>()
        val profileEvents = MutableSharedFlow<ProfileEvent>()
        val useCase = WatchAllMentionNamesUseCase(
            globalEventFlows = mockk {
                every { contacts } returns contactEvents
                every { profiles } returns profileEvents
            },
            contactModelRepository = contactModelRepositoryMock,
            identityStore = identityStoreMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
        )

        // act / assert
        useCase.call().test {
            // Expect current values
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "Nickname",
                    ),
                ),
            )

            // User changes own nickname
            profileEvents.emit(ProfileEvent.NicknameUpdated("NicknameNew"))
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameNew",
                    ),
                ),
            )

            // Add a new contact
            val contact2 = TestData.createContactModel(
                identity = TestData.Identities.OTHER_2,
            )
            every { contactModelRepositoryMock.getAll() } returns listOf(contact1, contact2)
            contactEvents.emit(ContactEvent.NewContact(Identity(contact2.identity)))
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameNew",
                    ),
                ),
            )

            // Update existing contact
            val contact1Updated = TestData.createContactModel(
                identity = TestData.Identities.OTHER_1,
                conversationVisibility = ConversationVisibility.ARCHIVED,
            )
            every { contactModelRepositoryMock.getAll() } returns listOf(contact1Updated, contact2)
            contactEvents.emit(ContactEvent.ContactUpdated(Identity(contact1Updated.identity)))
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_1,
                        firstname = contact1.data!!.firstName,
                        lastname = contact1.data!!.lastName,
                        nickname = contact1.data!!.nickname,
                    ),
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameNew",
                    ),
                ),
            )

            // Remove the first contact
            every { contactModelRepositoryMock.getAll() } returns listOf(contact2)
            contactEvents.emit(ContactEvent.ContactRemoved(Identity(contact1Updated.identity)))
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameNew",
                    ),
                ),
            )

            // Edge-Case: Identity not present anymore
            every { identityStoreMock.getIdentity() } returns null
            every { identityStoreMock.getIdentityString() } returns null
            profileEvents.emit(ProfileEvent.NicknameUpdated("NicknameOld"))
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                ),
            )

            // Edge-Case: Identity is present again
            every { identityStoreMock.getIdentity() } returns TestData.Identities.ME
            every { identityStoreMock.getIdentityString() } returns TestData.Identities.ME.value
            profileEvents.emit(ProfileEvent.NicknameUpdated("NicknameOld"))
            expectItem(
                listOf(
                    MentionNameData.Contact(
                        identity = TestData.Identities.OTHER_2,
                        firstname = contact2.data!!.firstName,
                        lastname = contact2.data!!.lastName,
                        nickname = contact2.data!!.nickname,
                    ),
                    MentionNameData.Me(
                        identity = TestData.Identities.ME,
                        nickname = "NicknameOld",
                    ),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }
    }
}
