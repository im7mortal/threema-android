package ch.threema.data.storage

import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverridePolicy
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.UserState
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.IdentityString
import ch.threema.test.TestData
import ch.threema.test.TestData.GroupIdentities
import ch.threema.test.TestData.Identities
import ch.threema.test.TestDatabaseProvider
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.utcDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SqliteDatabaseBackendTest {

    private lateinit var databaseBackend: SqliteDatabaseBackend

    @BeforeTest
    fun setUp() {
        databaseBackend = SqliteDatabaseBackend(
            databaseProvider = TestDatabaseProvider(),
            identityProvider = TestIdentityProvider(identity = Identities.ME),
        )
    }

    @Test
    fun canCreateMultipleDifferentContacts() {
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
        )
        val contact2 = createDbContact(
            identity = Identities.OTHER_2.value,
            publicKey = TestData.PublicKeys.OTHER_2,
        )
        databaseBackend.createContact(contact1)
        databaseBackend.createContact(contact2)
        val storedIdentities = databaseBackend
            .getAllContacts()
            .map(DbContact::identity)
        assertEquals(
            expected = listOf(contact1.identity, contact2.identity),
            actual = storedIdentities,
        )
    }

    @Test
    fun cannotCreateContactsWithInvalidIdentity() {
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(
                createDbContact(
                    identity = "INVALID",
                    publicKey = TestData.PublicKeys.OTHER_1,
                ),
            )
        }
    }

    @Test
    fun cannotCreateContactsWithUserIdentity() {
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(
                createDbContact(
                    identity = Identities.ME.value,
                    publicKey = TestData.PublicKeys.OTHER_1,
                ),
            )
        }
    }

    @Test
    fun cannotCreateContactsWithWrongLengthPublicKey() {
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(
                createDbContact(
                    identity = Identities.OTHER_1.value,
                    publicKey = ByteArray(33) { 1 },
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(
                createDbContact(
                    identity = Identities.OTHER_1.value,
                    publicKey = ByteArray(31) { 1 },
                ),
            )
        }
    }

    @Test
    fun cannotCreateContactsWithAllZeroesPublicKey() {
        assertFailsWith<IllegalArgumentException> {
            databaseBackend.createContact(
                createDbContact(
                    identity = Identities.OTHER_1.value,
                    publicKey = ByteArray(32),
                ),
            )
        }
    }

    @Test
    fun cannotCreateMultipleContactsWithSameIdentity() {
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
        )
        databaseBackend.createContact(contact1)
        assertFailsWith<DatabaseException> {
            databaseBackend.createContact(contact1)
        }
    }

    @Test
    fun cannotCreateMultipleContactsWithSamePublicKey() {
        val publicKey = TestData.PublicKeys.OTHER_1
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = publicKey,
        )
        val contact2 = createDbContact(
            identity = Identities.OTHER_2.value,
            publicKey = publicKey,
        )
        databaseBackend.createContact(contact1)
        assertFailsWith<DatabaseException> {
            databaseBackend.createContact(contact2)
        }
    }

    @Test
    fun isPartOfAnyGroup_NoGroups() {
        // arrange
        // empty DB

        // act
        val isPart = databaseBackend.isPartOfAnyGroup(
            contactIdentity = Identities.OTHER_1.value,
        )

        // assert
        assertFalse(isPart)
    }

    @Test
    fun isPartOfAnyGroup_OneGroup_Creator() {
        // arrange
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
        )
        databaseBackend.createContact(contact1)
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroup(
            contactIdentity = Identities.OTHER_1.value,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroup_OneNotesGroup_Creator() {
        // arrange
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
        )
        databaseBackend.createContact(contact1)
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = emptySet(), // notes group
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroup(
            contactIdentity = Identities.OTHER_1.value,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroup_MultipleGroups_Creator() {
        // arrange
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
        )
        databaseBackend.createContact(contact1)
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroup(
            contactIdentity = Identities.OTHER_1.value,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroup_OneGroup_Member() {
        // arrange
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
        )
        databaseBackend.createContact(contact1)
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroup(
            contactIdentity = Identities.OTHER_2.value,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroup_MultipleGroups_Member() {
        // arrange
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
        )
        databaseBackend.createContact(contact1)
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroup(
            contactIdentity = Identities.OTHER_2.value,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroup_MultipleGroups_CreatorAndMember() {
        // arrange
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
        )
        databaseBackend.createContact(contact1)
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_2.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_1.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroup(
            contactIdentity = Identities.OTHER_1.value,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroup_MultipleGroups_NotPart() {
        // arrange
        val contact1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
        )
        databaseBackend.createContact(contact1)
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_2.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_1.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroup(
            contactIdentity = Identities.OTHER_4.value,
        )

        // assert
        assertFalse(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_NoGroups() {
        // arrange
        // empty DB

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_1.value,
            userState = UserState.MEMBER,
        )

        // assert
        assertFalse(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_OneGroup_Creator() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_1.value,
            userState = UserState.LEFT,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_OneNotesGroup_Creator() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = emptySet(), // notes group
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_1.value,
            userState = UserState.LEFT,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_MultipleGroups_Creator() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_1.value,
            userState = UserState.LEFT,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_OneGroup_Member() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_2.value,
            userState = UserState.LEFT,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_MultipleGroups_Member() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_2.value,
            userState = UserState.LEFT,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_MultipleGroups_CreatorAndMember() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_2.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_1.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_1.value,
            userState = UserState.LEFT,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_MultipleGroups_NotPart() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_2.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_1.value,
                    Identities.OTHER_3.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_4.value,
            userState = UserState.LEFT,
        )

        // assert
        assertFalse(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_MultipleGroups() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                ),
                userState = UserState.MEMBER,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_2.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_1.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_1.value,
            userState = UserState.MEMBER,
        )

        // assert
        assertTrue(isPart)
    }

    @Test
    fun isPartOfAnyGroupWithUserState_MultipleGroups_UserStateNotMatching() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_2.value,
                ),
                userState = UserState.KICKED,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_2.value,
                    groupId = 2L,
                ),
                otherMembers = setOf(
                    Identities.OTHER_1.value,
                ),
                userState = UserState.LEFT,
            ),
        )

        // act
        val isPart = databaseBackend.isPartOfAnyGroupWithUserState(
            contactIdentity = Identities.OTHER_1.value,
            userState = UserState.MEMBER,
        )

        // assert
        assertFalse(isPart)
    }

    @Test
    fun getAllGroupIdentitiesWorks() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.OTHER_2.value,
                    groupId = 2L,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    creatorIdentity = Identities.ME.value,
                    groupId = 3L,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )

        // act
        val groupIdentities = databaseBackend.getAllGroupIdentities()

        // assert
        assertEquals(
            expected = setOf(
                GroupIdentity(
                    creatorIdentity = Identities.OTHER_1.value,
                    groupId = 1L,
                ),
                GroupIdentity(
                    creatorIdentity = Identities.OTHER_2.value,
                    groupId = 2L,
                ),
                GroupIdentity(
                    creatorIdentity = Identities.ME.value,
                    groupId = 3L,
                ),
            ),
            actual = groupIdentities,
        )
    }

    @Test
    fun anyGroupExists_should_find_group() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 1L,
                    creatorIdentity = Identities.OTHER_1.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )

        // act
        val existsAny = databaseBackend.anyGroupExists(
            groupIdentities = setOf(
                GroupIdentity(
                    groupId = 2L,
                    creatorIdentity = Identities.OTHER_2.value,
                ),
                GroupIdentity(
                    groupId = 1L,
                    creatorIdentity = Identities.OTHER_1.value,
                ),
            ),
        )

        // assert
        assertTrue(existsAny)
    }

    @Test
    fun anyGroupExists_should_find_multiple_groups() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 1L,
                    creatorIdentity = Identities.OTHER_1.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 2L,
                    creatorIdentity = Identities.OTHER_2.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 3L,
                    creatorIdentity = Identities.OTHER_3.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )

        // act
        val existsAny = databaseBackend.anyGroupExists(
            groupIdentities = setOf(
                GroupIdentity(
                    groupId = 2L,
                    creatorIdentity = Identities.OTHER_2.value,
                ),
                GroupIdentity(
                    groupId = 3L,
                    creatorIdentity = Identities.OTHER_3.value,
                ),
            ),
        )

        // assert
        assertTrue(existsAny)
    }

    @Test
    fun anyGroupExists_should_not_find_group_1() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 1L,
                    creatorIdentity = Identities.OTHER_1.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 2L,
                    creatorIdentity = Identities.OTHER_2.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )

        // act
        val existsAny = databaseBackend.anyGroupExists(
            groupIdentities = setOf(
                GroupIdentity(
                    groupId = 3L,
                    creatorIdentity = Identities.OTHER_3.value,
                ),
                GroupIdentity(
                    groupId = 4L,
                    creatorIdentity = Identities.OTHER_4.value,
                ),
            ),
        )

        // assert
        assertFalse(existsAny)
    }

    @Test
    fun anyGroupExists_should_not_find_group_2() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 1L,
                    creatorIdentity = Identities.OTHER_1.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )

        // act
        // creatorIdentity is not matching
        val existsAny = databaseBackend.anyGroupExists(
            groupIdentities = setOf(
                GroupIdentity(
                    groupId = 1L,
                    creatorIdentity = Identities.OTHER_2.value,
                ),
            ),
        )

        // assert
        assertFalse(existsAny)
    }

    @Test
    fun groupExists_should_find_group() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 1L,
                    creatorIdentity = Identities.OTHER_1.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 2L,
                    creatorIdentity = Identities.OTHER_2.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )

        // act
        val exists = databaseBackend.groupExists(
            GroupIdentity(
                groupId = 2L,
                creatorIdentity = Identities.OTHER_2.value,
            ),
        )

        // assert
        assertTrue(exists)
    }

    @Test
    fun groupExists_should_not_find_group() {
        // arrange
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 1L,
                    creatorIdentity = Identities.OTHER_1.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )
        databaseBackend.createGroup(
            createDbGroup(
                groupIdentity = GroupIdentity(
                    groupId = 2L,
                    creatorIdentity = Identities.OTHER_2.value,
                ),
                otherMembers = emptySet(),
                userState = UserState.MEMBER,
            ),
        )

        // act
        val exists = databaseBackend.groupExists(
            GroupIdentity(
                groupId = 3L,
                creatorIdentity = Identities.OTHER_3.value,
            ),
        )

        // assert
        assertFalse(exists)
    }

    @Test
    fun getGroupsByGroupIdentities_should_return_empty_1() {
        // arrange
        // empty db

        // act
        val dbGroups = databaseBackend.getGroupsByGroupIdentities(
            groupIdentities = emptySet(),
        )

        // assert
        assertTrue(dbGroups.isEmpty())
    }

    @Test
    fun getGroupsByGroupIdentities_should_return_empty_2() {
        // arrange
        // empty db

        // act
        val dbGroups = databaseBackend.getGroupsByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
            ),
        )

        // assert
        assertTrue(dbGroups.isEmpty())
    }

    @Test
    fun getGroupsByGroupIdentities_should_return_empty_3() {
        // arrange
        databaseBackend.createGroup(createDbGroup(groupIdentity = GroupIdentities.GROUP_1))
        databaseBackend.createGroup(createDbGroup(groupIdentity = GroupIdentities.GROUP_2))

        // act
        val dbGroups = databaseBackend.getGroupsByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_3,
                GroupIdentities.GROUP_4,
            ),
        )

        // assert
        assertTrue(dbGroups.isEmpty())
    }

    @Test
    fun getGroupsByGroupIdentities_should_return_subset() {
        // arrange
        databaseBackend.createGroup(createDbGroup(groupIdentity = GroupIdentities.GROUP_1))
        databaseBackend.createGroup(createDbGroup(groupIdentity = GroupIdentities.GROUP_2))
        databaseBackend.createGroup(createDbGroup(groupIdentity = GroupIdentities.GROUP_3))

        // act
        val dbGroups = databaseBackend.getGroupsByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_3,
                GroupIdentities.GROUP_4,
            ),
        )

        // assert
        assertEquals(
            expected = setOf(GroupId(3L).toString()),
            actual = dbGroups.map(DbGroup::groupId).toSet(),
        )
    }

    @Test
    fun getGroupsByGroupIdentities_should_return_all() {
        // arrange
        databaseBackend.createGroup(createDbGroup(groupIdentity = GroupIdentities.GROUP_1))
        databaseBackend.createGroup(createDbGroup(groupIdentity = GroupIdentities.GROUP_2))
        databaseBackend.createGroup(createDbGroup(groupIdentity = GroupIdentities.GROUP_3))
        databaseBackend.createGroup(createDbGroup(groupIdentity = GroupIdentities.GROUP_4))

        // act
        val dbGroups = databaseBackend.getGroupsByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_4,
                GroupIdentities.GROUP_2,
                GroupIdentities.GROUP_3,
            ),
        )

        // assert
        assertEquals(
            expected = setOf(
                GroupId(2L).toString(),
                GroupId(3L).toString(),
                GroupId(4L).toString(),
            ),
            actual = dbGroups.map(DbGroup::groupId).toSet(),
        )
    }

    @Test
    fun createGroups_should_not_create_anything_for_empty_input() {
        // arrange
        // empty db

        // act
        val createdGroupIdentities = databaseBackend.createGroups(emptySet())

        // assert
        assertEquals(
            expected = 0,
            actual = createdGroupIdentities.size,
        )
        assertEquals(
            expected = 0,
            actual = databaseBackend.getAllGroups().size,
        )
    }

    @Test
    fun createGroups_should_create_single_group_no_members() {
        // arrange
        val groupNoMembers = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_1,
            otherMembers = emptySet(),
        )

        // act
        val createdGroupIdentities = databaseBackend.createGroups(
            setOf(
                groupNoMembers,
            ),
        )

        // assert
        assertEquals(
            expected = 1,
            actual = createdGroupIdentities.size,
        )
        val groups = databaseBackend.getAllGroups()
        assertEquals(
            expected = 1,
            actual = groups.size,
        )
        assertEquals(
            expected = groupNoMembers,
            actual = groups.first(),
        )
    }

    @Test
    fun createGroups_should_create_single_group_with_members() {
        // arrange
        val groupWithMembers = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_1,
            otherMembers = setOf(
                Identities.OTHER_2.value,
                Identities.OTHER_3.value,
            ),
        )

        // act
        val createdGroupIdentities = databaseBackend.createGroups(
            setOf(
                groupWithMembers,
            ),
        )

        // assert
        assertEquals(
            expected = setOf(GroupIdentities.GROUP_1),
            actual = createdGroupIdentities,
        )
        val groups = databaseBackend.getAllGroups()
        assertEquals(
            expected = 1,
            actual = groups.size,
        )
        assertEquals(
            expected = groupWithMembers,
            actual = groups.first(),
        )
    }

    @Test
    fun createGroups_should_create_multiple_groups() {
        // arrange
        val group1 = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_1,
            otherMembers = setOf(
                Identities.OTHER_2.value,
                Identities.OTHER_3.value,
            ),
            userState = UserState.MEMBER,
        )
        val group2 = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_2,
            otherMembers = emptySet(),
            userState = UserState.LEFT,
        )
        val group3 = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_3,
            otherMembers = setOf(
                Identities.OTHER_4.value,
            ),
            userState = UserState.KICKED,
        )
        val groupMe = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_ME,
            otherMembers = setOf(
                Identities.OTHER_1.value,
            ),
            userState = UserState.MEMBER,
        )

        // act
        val createdGroupIdentities = databaseBackend.createGroups(
            setOf(
                group1,
                group2,
                group3,
                groupMe,
            ),
        )

        // assert
        assertEquals(
            expected = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
                GroupIdentities.GROUP_3,
                GroupIdentities.GROUP_ME,
            ),
            actual = createdGroupIdentities,
        )
        val groups = databaseBackend.getAllGroups()
        assertEquals(
            expected = 4,
            actual = groups.size,
        )
        val createdGroup1 = groups.find { it.groupId == group1.groupId }
        assertEquals(
            expected = group1,
            actual = createdGroup1,
        )
        val createdGroup2 = groups.find { it.groupId == group2.groupId }
        assertEquals(
            expected = group2,
            actual = createdGroup2,
        )
        val createdGroup3 = groups.find { it.groupId == group3.groupId }
        assertEquals(
            expected = group3,
            actual = createdGroup3,
        )
        val createdGroupMe = groups.find { it.groupId == groupMe.groupId }
        assertEquals(
            expected = groupMe,
            actual = createdGroupMe,
        )
    }

    @Test
    fun createGroups_should_fail_for_duplicate_identifiers() {
        // arrange
        val group1 = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_1,
            userState = UserState.MEMBER,
        )
        val group1Duplicate = createDbGroup(
            groupIdentity = GroupIdentities.GROUP_1,
            userState = UserState.LEFT,
        )

        // act / assert
        assertFailsWith<DatabaseException> {
            databaseBackend.createGroups(
                setOf(
                    group1,
                    group1Duplicate,
                ),
            )
        }
        val groups = databaseBackend.getAllGroups()
        assertEquals(
            expected = 0,
            actual = groups.size,
        )
    }

    @Test
    fun createGroups_stores_all_fields() {
        // arrange
        val dbGroupComplex = DbGroup(
            creatorIdentity = GroupIdentities.GROUP_1.creatorIdentity,
            groupId = GroupIdentities.GROUP_1.apiGroupIdHexString,
            name = "Group Complex",
            createdAt = utcDate(2026, 5, 14),
            synchronizedAt = utcDate(2026, 5, 15),
            lastUpdate = utcDate(2026, 5, 16),
            conversationVisibility = ConversationVisibility.PINNED,
            conversationCategory = ConversationCategory.PROTECTED.serializedValue,
            colorIndex = 44,
            groupDescription = "Group description",
            groupDescriptionChangedAt = utcDate(2026, 5, 17),
            members = setOf(
                Identities.OTHER_2.value,
                Identities.OTHER_3.value,
                Identities.OTHER_4.value,
                Identities.OTHER_5.value,
            ),
            userState = UserState.KICKED,
            notificationTriggerPolicyOverridePolicy = GroupNotificationTriggerPolicyOverridePolicy.MENTIONED.serializedValue,
            notificationTriggerPolicyOverrideExpiresAt = utcDate(2026, 5, 18),
            spamReportingState = ConversationSpamReportingState.NOT_SPAM.serializedValue,
        )

        // act
        val createdGroupIdentities = databaseBackend.createGroups(
            setOf(
                dbGroupComplex,
            ),
        )

        // assert
        assertEquals(
            expected = 1,
            actual = createdGroupIdentities.size,
        )
        val groups = databaseBackend.getAllGroups()
        assertEquals(
            expected = 1,
            actual = groups.size,
        )
        assertEquals(
            expected = dbGroupComplex,
            actual = groups.first(),
        )
    }

    @Test
    fun allContactsExistWithAcquaintanceLevel_empty_input() {
        // act
        val allExist = databaseBackend.allContactsExistWithAcquaintanceLevel(
            identities = emptySet(),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allContactsExistWithAcquaintanceLevel_empty_db() {
        // act
        val allExist = databaseBackend.allContactsExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_2.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allContactsExistWithAcquaintanceLevel_all_exist_but_wrong_acquaintance_level() {
        // arrange
        val contactOther1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        val contactOther2 = createDbContact(
            identity = Identities.OTHER_2.value,
            publicKey = TestData.PublicKeys.OTHER_2,
            acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
        )
        val contactOther3 = createDbContact(
            identity = Identities.OTHER_3.value,
            publicKey = TestData.PublicKeys.OTHER_3,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        databaseBackend.createContact(contactOther1, contactOther2, contactOther3)

        // act
        val allExist = databaseBackend.allContactsExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_2.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allContactsExistWithAcquaintanceLevel_correct_acquaintance_level_but_missing_contact() {
        // arrange
        val contactOther1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        val contactOther2 = createDbContact(
            identity = Identities.OTHER_2.value,
            publicKey = TestData.PublicKeys.OTHER_2,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        databaseBackend.createContact(contactOther1, contactOther2)

        // act
        val allExist = databaseBackend.allContactsExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_2.value,
                Identities.OTHER_3.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allContactsExistWithAcquaintanceLevel_all_exist_direct() {
        // arrange
        val contactOther1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        val contactOther2 = createDbContact(
            identity = Identities.OTHER_2.value,
            publicKey = TestData.PublicKeys.OTHER_2,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        val contactOther3 = createDbContact(
            identity = Identities.OTHER_3.value,
            publicKey = TestData.PublicKeys.OTHER_3,
            acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
        )
        val contactOther4 = createDbContact(
            identity = Identities.OTHER_4.value,
            publicKey = TestData.PublicKeys.OTHER_4,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        databaseBackend.createContact(contactOther1, contactOther2, contactOther3, contactOther4)

        // act
        val allExist = databaseBackend.allContactsExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_1.value,
                Identities.OTHER_2.value,
                Identities.OTHER_4.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allContactsExistWithAcquaintanceLevel_all_exist_groups_or_deleted() {
        // arrange
        val contactOther1 = createDbContact(
            identity = Identities.OTHER_1.value,
            publicKey = TestData.PublicKeys.OTHER_1,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        val contactOther2 = createDbContact(
            identity = Identities.OTHER_2.value,
            publicKey = TestData.PublicKeys.OTHER_2,
            acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
        )
        val contactOther3 = createDbContact(
            identity = Identities.OTHER_3.value,
            publicKey = TestData.PublicKeys.OTHER_3,
            acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
        )
        val contactOther4 = createDbContact(
            identity = Identities.OTHER_4.value,
            publicKey = TestData.PublicKeys.OTHER_4,
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
        )
        databaseBackend.createContact(contactOther1, contactOther2, contactOther3, contactOther4)

        // act
        val allExist = databaseBackend.allContactsExistWithAcquaintanceLevel(
            identities = setOf(
                Identities.OTHER_2.value,
                Identities.OTHER_3.value,
            ),
            acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allGroupsExist_empty_input() {
        // act
        val allExist = databaseBackend.allGroupsExist(
            groupIdentities = emptySet(),
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun allGroupsExist_empty_db() {
        // act
        val allExist = databaseBackend.allGroupsExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
            ),
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allGroupsExist_missing_one() {
        // arrange
        val groupOther1 = createDbGroup(groupIdentity = GroupIdentities.GROUP_1)
        val groupOther2 = createDbGroup(groupIdentity = GroupIdentities.GROUP_2)
        databaseBackend.createGroups(setOf(groupOther1, groupOther2))

        // act
        val allExist = databaseBackend.allGroupsExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
                GroupIdentities.GROUP_3,
            ),
        )

        // assert
        assertFalse(allExist)
    }

    @Test
    fun allGroupsExist_all_exist() {
        // arrange
        val groupOther1 = createDbGroup(groupIdentity = GroupIdentities.GROUP_1)
        val groupOther2 = createDbGroup(groupIdentity = GroupIdentities.GROUP_2)
        val groupOther3 = createDbGroup(groupIdentity = GroupIdentities.GROUP_3)
        val groupOther4 = createDbGroup(groupIdentity = GroupIdentities.GROUP_4)
        databaseBackend.createGroups(setOf(groupOther1, groupOther2, groupOther3, groupOther4))

        // act
        val allExist = databaseBackend.allGroupsExist(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
                GroupIdentities.GROUP_3,
            ),
        )

        // assert
        assertTrue(allExist)
    }

    @Test
    fun getGroupDatabaseIdsByGroupIdentities_empty_input() {
        // act
        val groupDatabaseIdsByGroupIdentities = databaseBackend.getGroupDatabaseIdsByGroupIdentities(
            groupIdentities = emptySet(),
        )

        // assert
        assertEquals(
            expected = 0,
            actual = groupDatabaseIdsByGroupIdentities.size,
        )
    }

    @Test
    fun getGroupDatabaseIdsByGroupIdentities_empty_db() {
        // act
        val groupDatabaseIdsByGroupIdentities = databaseBackend.getGroupDatabaseIdsByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
                GroupIdentities.GROUP_3,
            ),
        )

        // assert
        assertEquals(
            expected = 0,
            actual = groupDatabaseIdsByGroupIdentities.size,
        )
    }

    @Test
    fun getGroupDatabaseIdsByGroupIdentities_finds_existing() {
        // arrange
        val groupOther1 = createDbGroup(groupIdentity = GroupIdentities.GROUP_1)
        val groupOther2 = createDbGroup(groupIdentity = GroupIdentities.GROUP_2)
        val groupOther3 = createDbGroup(groupIdentity = GroupIdentities.GROUP_3)
        databaseBackend.createGroups(setOf(groupOther1, groupOther2, groupOther3))

        // act
        val groupDatabaseIdsByGroupIdentities = databaseBackend.getGroupDatabaseIdsByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_1,
                GroupIdentities.GROUP_2,
                GroupIdentities.GROUP_4,
            ),
        )

        // assert
        assertEquals(
            expected = 2,
            actual = groupDatabaseIdsByGroupIdentities.size,
        )
        assertEquals(
            expected = 1,
            actual = groupDatabaseIdsByGroupIdentities[GroupIdentities.GROUP_1],
        )
        assertEquals(
            expected = 2,
            actual = groupDatabaseIdsByGroupIdentities[GroupIdentities.GROUP_2],
        )
    }

    @Test
    fun getGroupDatabaseIdsByGroupIdentities_finds_none() {
        // arrange
        val groupOther1 = createDbGroup(groupIdentity = GroupIdentities.GROUP_1)
        val groupOther2 = createDbGroup(groupIdentity = GroupIdentities.GROUP_2)
        databaseBackend.createGroups(setOf(groupOther1, groupOther2))

        // act
        val groupDatabaseIdsByGroupIdentities = databaseBackend.getGroupDatabaseIdsByGroupIdentities(
            groupIdentities = setOf(
                GroupIdentities.GROUP_3,
                GroupIdentities.GROUP_4,
            ),
        )

        // assert
        assertEquals(
            expected = 0,
            actual = groupDatabaseIdsByGroupIdentities.size,
        )
    }

    private companion object {
        private fun createDbContact(
            identity: IdentityString,
            publicKey: ByteArray,
            acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
        ) = DbContact(
            identity = identity,
            publicKey = publicKey,
            createdAt = utcDate(2026, 4, 15),
            lastUpdateAt = null,
            firstName = "Testy",
            lastName = "McTestface",
            nickname = null,
            colorIndex = 0,
            verificationLevel = VerificationLevel.UNVERIFIED,
            workVerificationLevel = WorkVerificationLevel.NONE,
            identityType = IdentityType.REGULAR,
            acquaintanceLevel = acquaintanceLevel,
            activityState = IdentityState.ACTIVE,
            syncState = ContactSyncState.INITIAL,
            featureMask = 0UL,
            readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
            typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
            conversationVisibility = ConversationVisibility.NORMAL,
            conversationCategory = ConversationCategory.DEFAULT.serializedValue,
            androidContactLookupKey = null,
            localAvatarExpires = null,
            isRestored = false,
            profilePictureBlobId = null,
            jobTitle = null,
            department = null,
            notificationTriggerPolicyOverridePolicy = null,
            notificationTriggerPolicyOverrideExpiresAt = null,
            availabilityStatusSet = null,
            workLastFullSyncAt = null,
        )

        private fun createDbGroup(
            groupIdentity: GroupIdentity,
            otherMembers: Set<IdentityString> = emptySet(),
            userState: UserState = UserState.MEMBER,
        ) = DbGroup(
            creatorIdentity = groupIdentity.creatorIdentity,
            groupId = groupIdentity.apiGroupIdHexString,
            name = "Group",
            createdAt = utcDate(2026, 4, 15),
            synchronizedAt = utcDate(2026, 4, 15),
            lastUpdate = null,
            conversationVisibility = ConversationVisibility.NORMAL,
            conversationCategory = ConversationCategory.DEFAULT.serializedValue,
            colorIndex = 0,
            groupDescription = null,
            groupDescriptionChangedAt = null,
            members = otherMembers,
            userState = userState,
            notificationTriggerPolicyOverridePolicy = null,
            notificationTriggerPolicyOverrideExpiresAt = null,
            spamReportingState = ConversationSpamReportingState.DEFAULT.serializedValue,
        )
    }
}
