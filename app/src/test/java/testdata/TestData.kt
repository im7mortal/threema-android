package testdata

import ch.threema.app.managers.ServiceManager
import ch.threema.app.messagereceiver.ContactMessageReceiver
import ch.threema.app.messagereceiver.DistributionListMessageReceiver
import ch.threema.app.messagereceiver.GroupMessageReceiver
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.UserService
import ch.threema.base.crypto.NaCl
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationSpamReportingState
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.ContactModelData.Companion.javaCreate
import ch.threema.data.models.GroupModel
import ch.threema.data.models.GroupModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.data.storage.DatabaseBackend
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.GroupId
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.MessageId
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.UserState
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.storage.DatabaseService
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.ConversationModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.group.GroupModelOld
import ch.threema.test.TestIdentityProvider
import ch.threema.testhelpers.utcDate
import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import java.time.Instant

object TestData {

    object Identities {

        val ME = Identity("12345678")

        val OTHER_1 = Identity("11111111")
        val OTHER_2 = Identity("22222222")
        val OTHER_3 = Identity("33333333")
        val OTHER_4 = Identity("44444444")
        val OTHER_5 = Identity("55555555")

        val BROADCAST = Identity("*0000000")

        const val INVALID = "INVALID"
    }

    object GroupIdentities {

        val GROUP_ME = GroupIdentity(
            creatorIdentity = Identities.ME.value,
            groupId = 0L,
        )
        val GROUP_1 = GroupIdentity(
            creatorIdentity = Identities.OTHER_1.value,
            groupId = 1L,
        )
        val GROUP_2 = GroupIdentity(
            creatorIdentity = Identities.OTHER_2.value,
            groupId = 2L,
        )
        val GROUP_3 = GroupIdentity(
            creatorIdentity = Identities.OTHER_3.value,
            groupId = 3L,
        )
    }

    object MessageIds {
        val MESSAGE_ID_1 = MessageId.fromString("0000111122223333")
        val MESSAGE_ID_2 = MessageId.fromString("4444555566667777")
    }

    val testPublicKey = ByteArray(32) { it.toByte() }
    val testPrivateKey = ByteArray(32) { it.toByte() }

    fun createContactConversationModel(contactConversationId: ContactConversationId) =
        createContactConversationModel(
            identity = Identity(contactConversationId.identity),
        )

    fun createContactConversationModel(
        identity: Identity,
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        databaseBackendMock: DatabaseBackend = mockk(relaxed = true),
        databaseServiceMock: DatabaseService = mockk(relaxed = true),
        blockedIdentitiesServiceMock: BlockedIdentitiesService = mockk(relaxed = true),
        contactModelRepositoryMock: ContactModelRepository = mockk(relaxed = true),
        serviceManagerMock: ServiceManager = mockk(relaxed = true),
        multiDeviceManager: MultiDeviceManager = mockk(relaxed = true),
        taskManager: TaskManager = mockk(relaxed = true),
        contactServiceMock: ContactService = mockk(relaxed = true),
        identitySoreMock: IdentityStore = mockk(relaxed = true),
    ): ConversationModel =
        ConversationModel(
            messageReceiver = createAndMockContactMessageReceiver(
                identity = identity,
                conversationVisibility = conversationVisibility,
                contactModelRepositoryMock = contactModelRepositoryMock,
                databaseBackendMock = databaseBackendMock,
                databaseServiceMock = databaseServiceMock,
                serviceManagerMock = serviceManagerMock,
                multiDeviceManager = multiDeviceManager,
                taskManager = taskManager,
                blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
                contactServiceMock = contactServiceMock,
                identityStoreMock = identitySoreMock,
            ),
        ).apply {
            this.conversationVisibility = conversationVisibility
        }

    fun createAndMockContactMessageReceiver(
        identity: Identity,
        publicKey: ByteArray = testPublicKey,
        firstname: String = "Firstname",
        lastname: String = "Lastname",
        nickname: String? = null,
        createdAt: Instant = utcDate(2025, 10, 25),
        isRestored: Boolean = false,
        activityState: IdentityState = IdentityState.ACTIVE,
        syncState: ContactSyncState = ContactSyncState.INITIAL,
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        contactModelRepositoryMock: ContactModelRepository = mockk(relaxed = true),
        databaseBackendMock: DatabaseBackend = mockk(relaxed = true),
        databaseServiceMock: DatabaseService = mockk(relaxed = true),
        serviceManagerMock: ServiceManager = mockk(relaxed = true),
        blockedIdentitiesServiceMock: BlockedIdentitiesService = mockk(relaxed = true),
        contactServiceMock: ContactService = mockk(relaxed = true),
        identityStoreMock: IdentityStore = mockk(relaxed = true),
        multiDeviceManager: MultiDeviceManager = mockk(relaxed = true),
        taskManager: TaskManager = mockk(relaxed = true),
    ): ContactMessageReceiver {
        val contactModelOld = ContactModel.create(identity.value, publicKey).apply {
            this.firstName = lastname
            this.lastName = lastname
            this.publicNickName = nickname
            this.state = activityState
            this.dateCreated = createdAt
            this.setIsRestored(isRestored)
            this.setConversationVisibility(conversationVisibility)
        }
        val contactModel = createContactModel(
            identity = identity,
            publicKey = publicKey,
            firstname = firstname,
            lastname = lastname,
            nickname = nickname,
            createdAt = createdAt,
            isRestored = isRestored,
            activityState = activityState,
            syncState = syncState,
            conversationVisibility = conversationVisibility,
            databaseBackendMock = databaseBackendMock,
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
        )

        every { contactModelRepositoryMock.getByIdentity(identity) } returns contactModel
        every { contactModelRepositoryMock.getByIdentity(identity.value) } returns contactModel

        return ContactMessageReceiver(
            /* contact = */
            contactModelOld,
            /* contactService = */
            contactServiceMock,
            /* serviceManager = */
            serviceManagerMock,
            /* databaseService = */
            databaseServiceMock,
            /* identityStore = */
            identityStoreMock,
            /* blockedIdentitiesService = */
            blockedIdentitiesServiceMock,
            /* contactModelRepository = */
            contactModelRepositoryMock,
        )
    }

    fun createGroupConversationModel(groupConversationId: GroupConversationId) =
        createGroupConversationModel(
            groupDatabaseId = groupConversationId.groupDatabaseId,
        )

    fun createGroupConversationModel(
        groupDatabaseId: GroupDatabaseId,
        apiGroupId: GroupId = GroupId(),
        creatorIdentity: Identity = Identities.OTHER_1,
        userState: UserState = UserState.MEMBER,
        otherMembers: Set<Identity> = setOf(Identities.OTHER_2),
        createdAt: Instant = utcDate(2025, 10, 25),
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        groupServiceMock: GroupService = mockk(relaxed = true),
        databaseServiceMock: DatabaseService = mockk(relaxed = true),
        databaseBackendMock: DatabaseBackend = mockk(relaxed = true),
        userServiceMock: UserService = mockk(relaxed = true),
        contactModelRepositoryMock: ContactModelRepository = mockk(relaxed = true),
        groupModelRepositoryMock: GroupModelRepository = mockk(relaxed = true),
        serviceManagerMock: ServiceManager = mockk(relaxed = true),
        multiDeviceManager: MultiDeviceManager = mockk(relaxed = true),
        taskManager: TaskManager = mockk(relaxed = true),
    ): ConversationModel {
        return ConversationModel(
            messageReceiver = createAndMockGroupMessageReceiver(
                groupDatabaseId = groupDatabaseId,
                apiGroupId = apiGroupId,
                ownIdentity = Identities.ME,
                creatorIdentity = creatorIdentity,
                userState = userState,
                otherMembers = otherMembers,
                createdAt = createdAt,
                conversationVisibility = conversationVisibility,
                groupServiceMock = groupServiceMock,
                databaseServiceMock = databaseServiceMock,
                databaseBackendMock = databaseBackendMock,
                userServiceMock = userServiceMock,
                contactModelRepositoryMock = contactModelRepositoryMock,
                groupModelRepositoryMock = groupModelRepositoryMock,
                serviceManagerMock = serviceManagerMock,
                multiDeviceManager = multiDeviceManager,
                taskManager = taskManager,
            ),
        ).apply {
            this.conversationVisibility = conversationVisibility
        }
    }

    /**
     *  Create a GroupMessageReceiver and mock the [groupModelRepositoryMock] to return it.
     *
     *  @param otherMembers has to follow the rules defined in [GroupModelData.otherMembers].
     */
    fun createAndMockGroupMessageReceiver(
        groupDatabaseId: GroupDatabaseId,
        apiGroupId: GroupId = GroupId(),
        ownIdentity: Identity,
        creatorIdentity: Identity,
        userState: UserState,
        otherMembers: Set<Identity>,
        createdAt: Instant = utcDate(2025, 10, 25),
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        groupServiceMock: GroupService = mockk(relaxed = true),
        databaseServiceMock: DatabaseService = mockk(relaxed = true),
        databaseBackendMock: DatabaseBackend = mockk(relaxed = true),
        userServiceMock: UserService = mockk(relaxed = true),
        contactModelRepositoryMock: ContactModelRepository = mockk(relaxed = true),
        groupModelRepositoryMock: GroupModelRepository = mockk(relaxed = true),
        serviceManagerMock: ServiceManager = mockk(relaxed = true),
        multiDeviceManager: MultiDeviceManager = mockk(relaxed = true),
        taskManager: TaskManager = mockk(relaxed = true),
    ): GroupMessageReceiver {
        val groupIdentity = GroupIdentity(
            creatorIdentity = creatorIdentity.value,
            groupId = apiGroupId.toLong(),
        )
        val groupModelOld: GroupModelOld = GroupModelOld().apply {
            this.id = groupDatabaseId.toInt()
            this.apiGroupId = apiGroupId
            this.creatorIdentity = creatorIdentity.value
            this.conversationVisibility = conversationVisibility
        }

        val newGroupModel = GroupModel(
            groupIdentity = groupIdentity,
            data = GroupModelData(
                groupIdentity = groupIdentity,
                name = null,
                createdAt = createdAt,
                synchronizedAt = null,
                lastUpdate = null,
                conversationVisibility = conversationVisibility,
                conversationCategory = ConversationCategory.DEFAULT,
                precomputedIdColor = IdColor.ofGroup(groupIdentity),
                groupDescription = null,
                groupDescriptionChangedAt = null,
                otherMembers = otherMembers.map { it.value }.toSet(),
                userState = userState,
                notificationTriggerPolicyOverride = null,
                spamReportingState = ConversationSpamReportingState.DEFAULT,
            ),
            databaseBackend = databaseBackendMock,
            identityProvider = TestIdentityProvider(ownIdentity),
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
            globalEventBuses = mockk(relaxed = true),
        )

        every {
            groupModelRepositoryMock.getByCreatorIdentityAndId(
                creatorIdentity = creatorIdentity.value,
                groupId = apiGroupId,
            )
        } returns newGroupModel

        every {
            groupModelRepositoryMock.getByGroupIdentity(
                groupIdentity = GroupIdentity(
                    creatorIdentity = creatorIdentity.value,
                    groupId = apiGroupId.toLong(),
                ),
            )
        } returns newGroupModel

        every {
            groupModelRepositoryMock.getByGroupDatabaseId(
                groupDatabaseId = groupDatabaseId,
            )
        } returns newGroupModel

        every { databaseBackendMock.getGroupDatabaseId(groupIdentity) } returns groupDatabaseId

        return GroupMessageReceiver(
            /* group = */
            groupModelOld,
            /* groupService = */
            groupServiceMock,
            /* databaseService = */
            databaseServiceMock,
            /* userService = */
            userServiceMock,
            /* contactModelRepository = */
            contactModelRepositoryMock,
            /* groupModelRepository = */
            groupModelRepositoryMock,
            /* serviceManager = */
            serviceManagerMock,
        )
    }

    fun createDistributionListConversationModel(distributionListConversationId: DistributionListConversationId) =
        createDistributionListConversationModel(
            distributionListId = distributionListConversationId.distributionListId,
        )

    fun createDistributionListConversationModel(
        distributionListId: Long,
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        identitiesWithPublicKey: List<Pair<Identity, ByteArray>> = listOf(Identities.OTHER_1 to testPublicKey),
        distributionListServiceMock: DistributionListService = mockk(relaxed = true),
        contactServiceMock: ContactService = mockk(relaxed = true),
        contactModelRepositoryMock: ContactModelRepository = mockk(relaxed = true),
        databaseBackendMock: DatabaseBackend = mockk(relaxed = true),
        databaseServiceMock: DatabaseService = mockk(relaxed = true),
        serviceManagerMock: ServiceManager = mockk(relaxed = true),
        multiDeviceManager: MultiDeviceManager = mockk(relaxed = true),
        taskManager: TaskManager = mockk(relaxed = true),
        blockedIdentitiesServiceMock: BlockedIdentitiesService = mockk(relaxed = true),
        identityStoreMock: IdentityStore = mockk(relaxed = true),
    ): ConversationModel {
        return ConversationModel(
            messageReceiver = createAndMockDistributionListMessageReceiver(
                distributionListId = distributionListId,
                conversationVisibility = conversationVisibility,
                identitiesWithPublicKey = identitiesWithPublicKey,
                distributionListServiceMock = distributionListServiceMock,
                contactServiceMock = contactServiceMock,
                contactModelRepositoryMock = contactModelRepositoryMock,
                databaseBackendMock = databaseBackendMock,
                databaseServiceMock = databaseServiceMock,
                serviceManagerMock = serviceManagerMock,
                multiDeviceManager = multiDeviceManager,
                taskManager = taskManager,
                blockedIdentitiesServiceMock = blockedIdentitiesServiceMock,
                identityStoreMock = identityStoreMock,
            ),
        ).apply {
            this.conversationVisibility = conversationVisibility
        }
    }

    fun createAndMockDistributionListMessageReceiver(
        distributionListId: Long,
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        identitiesWithPublicKey: List<Pair<Identity, ByteArray>>,
        distributionListServiceMock: DistributionListService = mockk(relaxed = true),
        contactServiceMock: ContactService = mockk(relaxed = true),
        contactModelRepositoryMock: ContactModelRepository = mockk(relaxed = true),
        databaseBackendMock: DatabaseBackend = mockk(relaxed = true),
        databaseServiceMock: DatabaseService = mockk(relaxed = true),
        serviceManagerMock: ServiceManager = mockk(relaxed = true),
        multiDeviceManager: MultiDeviceManager = mockk(relaxed = true),
        taskManager: TaskManager = mockk(relaxed = true),
        blockedIdentitiesServiceMock: BlockedIdentitiesService = mockk(relaxed = true),
        identityStoreMock: IdentityStore = mockk(relaxed = true),
    ): MessageReceiver<*> {
        val contactModelsOld = identitiesWithPublicKey.map { identityWithPublicKey ->
            ContactModel.create(identityWithPublicKey.first.value, identityWithPublicKey.second)
        }

        every { distributionListServiceMock.getMembers(any()) } returns contactModelsOld

        every { contactModelRepositoryMock.getByIdentity(any<IdentityString>()) } answers { call ->
            val memberIdentity = call.invocation.args.first() as IdentityString
            createContactModel(
                identity = Identity(memberIdentity),
                publicKey = contactModelsOld.first { it.identity == memberIdentity }.publicKey,
                databaseBackendMock = databaseBackendMock,
                multiDeviceManager = multiDeviceManager,
                taskManager = taskManager,
            )
        }

        every {
            contactServiceMock.createReceiver(any() as ContactModel)
        } answers { call ->
            val contactModelOld = call.invocation.args.first() as ContactModel
            ContactMessageReceiver(
                /* contact = */
                contactModelOld,
                /* contactService = */
                contactServiceMock,
                /* serviceManager = */
                serviceManagerMock,
                /* databaseService = */
                databaseServiceMock,
                /* identityStore = */
                identityStoreMock,
                /* blockedIdentitiesService = */
                blockedIdentitiesServiceMock,
                /* contactModelRepository = */
                contactModelRepositoryMock,
            )
        }

        val distributionListModel = DistributionListModel().apply {
            this.id = distributionListId
            this.conversationVisibility = conversationVisibility
        }

        return DistributionListMessageReceiver(
            /* databaseService = */
            databaseServiceMock,
            /* contactService = */
            contactServiceMock,
            /* distributionListModel = */
            distributionListModel,
            /* distributionListService = */
            distributionListServiceMock,
        )
    }

    fun createContactModel(
        identity: Identity? = Identities.OTHER_1,
        publicKey: ByteArray = testPublicKey,
        firstname: String = "Firstname",
        lastname: String = "Lastname",
        nickname: String? = null,
        createdAt: Instant = utcDate(2025, 10, 25),
        lastUpdateAt: Instant? = null,
        isRestored: Boolean = false,
        activityState: IdentityState = IdentityState.ACTIVE,
        syncState: ContactSyncState = ContactSyncState.INITIAL,
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        conversationCategory: ConversationCategory = ConversationCategory.DEFAULT,
        workLastFullSyncAt: Instant? = null,
        availabilityStatus: AvailabilityStatus = AvailabilityStatus.Busy(),
        databaseBackendMock: DatabaseBackend = mockk(relaxed = true),
        identityProvider: IdentityProvider = TestIdentityProvider(identity = null),
        multiDeviceManager: MultiDeviceManager = mockk(relaxed = true),
        taskManager: TaskManager = mockk(relaxed = true),
        verificationLevel: VerificationLevel = VerificationLevel.FULLY_VERIFIED,
    ): ch.threema.data.models.ContactModel {
        return ch.threema.data.models.ContactModel(
            identity = identity?.value ?: "",
            data = javaCreate(
                identity = identity?.value ?: "",
                publicKey = publicKey,
                createdAt = createdAt,
                lastUpdateAt = lastUpdateAt,
                firstName = firstname,
                lastName = lastname,
                nickname = nickname,
                idColor = IdColor(0),
                verificationLevel = verificationLevel,
                workVerificationLevel = WorkVerificationLevel.NONE,
                identityType = IdentityType.REGULAR,
                acquaintanceLevel = AcquaintanceLevel.DIRECT,
                activityState = activityState,
                featureMask = BigInteger.ONE,
                syncState = syncState,
                readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
                typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
                conversationVisibility = conversationVisibility,
                conversationCategory = conversationCategory,
                androidContactLookupInfo = null,
                localAvatarExpires = null,
                isRestored = isRestored,
                profilePictureBlobId = null,
                jobTitle = null,
                department = null,
                notificationTriggerPolicyOverride = null,
                availabilityStatus = availabilityStatus,
                workLastFullSyncAt = workLastFullSyncAt,
            ),
            databaseBackend = databaseBackendMock,
            identityProvider = identityProvider,
            multiDeviceManager = multiDeviceManager,
            taskManager = taskManager,
            globalEventBuses = mockk(relaxed = true),
        )
    }

    fun createContactModelData(
        identity: Identity = Identities.OTHER_1,
        publicKey: ByteArray = ByteArray(NaCl.PUBLIC_KEY_BYTES),
        createdAt: Instant = Instant.now(),
        lastUpdateAt: Instant? = null,
        firstName: String = "First",
        lastName: String = "Last",
        nickname: String? = null,
        verificationLevel: VerificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel: WorkVerificationLevel = WorkVerificationLevel.NONE,
        identityType: IdentityType = IdentityType.REGULAR,
        acquaintanceLevel: AcquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState: IdentityState = IdentityState.ACTIVE,
        syncState: ContactSyncState = ContactSyncState.INITIAL,
        featureMask: ULong = 0u,
        readReceiptPolicy: ReadReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy: TypingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        conversationCategory: ConversationCategory = ConversationCategory.DEFAULT,
        androidContactLookupInfo: AndroidContactLookupInfo? = null,
        localAvatarExpires: Instant? = null,
        isRestored: Boolean = false,
        profilePictureBlobId: ByteArray? = null,
        jobTitle: String? = null,
        department: String? = null,
        notificationTriggerPolicyOverride: ContactNotificationTriggerPolicyOverride? = null,
        availabilityStatus: AvailabilityStatus = AvailabilityStatus.None,
        workLastFullSyncAt: Instant? = null,
    ) = ContactModelData(
        identity = identity.value,
        publicKey = publicKey,
        createdAt = createdAt,
        lastUpdateAt = lastUpdateAt,
        firstName = firstName,
        lastName = lastName,
        nickname = nickname,
        verificationLevel = verificationLevel,
        workVerificationLevel = workVerificationLevel,
        identityType = identityType,
        acquaintanceLevel = acquaintanceLevel,
        activityState = activityState,
        syncState = syncState,
        featureMask = featureMask,
        readReceiptPolicy = readReceiptPolicy,
        typingIndicatorPolicy = typingIndicatorPolicy,
        conversationVisibility = conversationVisibility,
        conversationCategory = conversationCategory,
        androidContactLookupInfo = androidContactLookupInfo,
        localAvatarExpires = localAvatarExpires,
        isRestored = isRestored,
        profilePictureBlobId = profilePictureBlobId,
        jobTitle = jobTitle,
        department = department,
        notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
        availabilityStatus = availabilityStatus,
        workLastFullSyncAt = workLastFullSyncAt,
    )

    fun createGroupModel(
        groupIdentity: GroupIdentity = GroupIdentity(creatorIdentity = Identities.OTHER_1.value, groupId = 42),
        name: String = "Group",
        createdAt: Instant = utcDate(2026, 7, 21),
        synchronizedAt: Instant? = null,
        lastUpdate: Instant? = null,
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        conversationCategory: ConversationCategory = ConversationCategory.DEFAULT,
        groupDescription: String? = null,
        groupDescriptionChangedAt: Instant? = null,
        otherMembers: Set<String> = emptySet(),
        userState: UserState = UserState.MEMBER,
        notificationTriggerPolicyOverride: GroupNotificationTriggerPolicyOverride? = null,
        spamReportingState: ConversationSpamReportingState = ConversationSpamReportingState.DEFAULT,
        databaseBackend: DatabaseBackend = mockk(relaxed = true),
        identityProvider: IdentityProvider = TestIdentityProvider(identity = null),
        multiDeviceManager: MultiDeviceManager = mockk(relaxed = true),
        taskManager: TaskManager = mockk(relaxed = true),
    ) = GroupModel(
        groupIdentity = groupIdentity,
        data = createGroupModelData(
            groupIdentity = groupIdentity,
            name = name,
            createdAt = createdAt,
            synchronizedAt = synchronizedAt,
            lastUpdate = lastUpdate,
            conversationVisibility = conversationVisibility,
            conversationCategory = conversationCategory,
            groupDescription = groupDescription,
            groupDescriptionChangedAt = groupDescriptionChangedAt,
            otherMembers = otherMembers,
            userState = userState,
            notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
            spamReportingState = spamReportingState,
        ),
        databaseBackend = databaseBackend,
        multiDeviceManager = multiDeviceManager,
        taskManager = taskManager,
        identityProvider = identityProvider,
        globalEventBuses = mockk(relaxed = true),
    )

    fun createGroupModelData(
        groupIdentity: GroupIdentity = GroupIdentity(creatorIdentity = Identities.OTHER_1.value, groupId = 42),
        name: String = "Group",
        createdAt: Instant = utcDate(2026, 7, 21),
        synchronizedAt: Instant? = null,
        lastUpdate: Instant? = null,
        conversationVisibility: ConversationVisibility = ConversationVisibility.NORMAL,
        conversationCategory: ConversationCategory = ConversationCategory.DEFAULT,
        groupDescription: String? = null,
        groupDescriptionChangedAt: Instant? = null,
        otherMembers: Set<String> = emptySet(),
        userState: UserState = UserState.MEMBER,
        notificationTriggerPolicyOverride: GroupNotificationTriggerPolicyOverride? = null,
        spamReportingState: ConversationSpamReportingState = ConversationSpamReportingState.DEFAULT,
    ) = GroupModelData(
        groupIdentity = groupIdentity,
        name = name,
        createdAt = createdAt,
        synchronizedAt = synchronizedAt,
        lastUpdate = lastUpdate,
        conversationVisibility = conversationVisibility,
        conversationCategory = conversationCategory,
        groupDescription = groupDescription,
        groupDescriptionChangedAt = groupDescriptionChangedAt,
        otherMembers = otherMembers,
        userState = userState,
        notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
        spamReportingState = spamReportingState,
    )
}
