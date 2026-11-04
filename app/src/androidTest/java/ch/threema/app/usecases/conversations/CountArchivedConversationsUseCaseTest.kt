package ch.threema.app.usecases.conversations

// TODO(ANDR-5040): Uncomment
fun stub() {}
/*
@RunWith(AndroidJUnit4::class)
class CountArchivedConversationsUseCaseTest : KoinComponent {
    private lateinit var contactModelRepository: ContactModelRepository

    private val instrumentedTestModule = module {
        single<DatabaseProvider> { TestDatabaseProvider() }
        single<IdentityProvider> { TestIdentityProvider(TestData.Identities.ME) }
        single<MultiDeviceManager> { TestMultiDeviceManager() }
        single<DispatcherProvider> { TestDispatcherProvider() }
        single<NonceFactory> { NonceFactory(TestNonceStore()) }
        single<TaskManager> { TestTaskManager(UnusedTaskCodec()) }
        factory<TimeProvider> { TestTimeProvider(initialTimestamp = 1782217148000) }
    }

    @get:Rule
    val koinTestRule = KoinTestRule(
        modules = listOf(instrumentedTestModule),
    )

    private fun TestScope.getUseCaseUnderTest(): CountArchivedConversationsUseCase =
        CountArchivedConversationsUseCase(
            databaseProvider = get(),
            conversationCategoryService = get(),
            preferenceService = get(),
            groupModelRepository = get(),
            getGroupDisplayNameUseCase = get(),
            distributionListService = get(),
            appContext = ApplicationProvider.getApplicationContext(),
            dispatcherProvider = testDispatcherProvider(),
        )

    @BeforeTest
    fun before() {
        contactModelRepository = ContactModelRepository(
            cache = ModelTypeCache(),
            databaseBackend = SqliteDatabaseBackend(
                databaseProvider = get(),
                identityProvider = get(),
            ),
            identityProvider = get(),
            multiDeviceManager = get(),
            taskManager = get(),
            nonceFactory = get(),
            globalEventBuses = get(),
            globalEventFlows = get(),
            dispatcherProvider = get(),
        )
    }

    @Test
    fun count() = runTest {
        val conversationCategoryService = get<ConversationCategoryService>()

        val contactNotArchived = TestData.createContactModelData(
            identity = TestData.Identities.OTHER_1,
            publicKey = TestData.PublicKeys.OTHER_1,
            conversationVisibility = ConversationVisibility.NORMAL,
            lastUpdateAt = get<TimeProvider>().get(),
        )
        contactModelRepository.createFromLocal(contactNotArchived)

        val contactArchivedButNeverUpdated = TestData.createContactModelData(
            identity = TestData.Identities.OTHER_2,
            publicKey = TestData.PublicKeys.OTHER_2,
            conversationVisibility = ConversationVisibility.ARCHIVED,
            lastUpdateAt = null,
        )
        contactModelRepository.createFromLocal(contactArchivedButNeverUpdated)

        val contactArchivedAcquaintanceLevelGroup = TestData.createContactModelData(
            identity = TestData.Identities.OTHER_3,
            publicKey = TestData.PublicKeys.OTHER_3,
            conversationVisibility = ConversationVisibility.ARCHIVED,
            lastUpdateAt = get<TimeProvider>().get(),
            acquaintanceLevel = AcquaintanceLevel.GROUP_OR_DELETED,
        )
        contactModelRepository.createFromLocal(contactArchivedAcquaintanceLevelGroup)

        val contactArchived = TestData.createContactModelData(
            identity = TestData.Identities.OTHER_4,
            publicKey = TestData.PublicKeys.OTHER_4,
            conversationVisibility = ConversationVisibility.ARCHIVED,
            lastUpdateAt = get<TimeProvider>().get(),
        )
        contactModelRepository.createFromLocal(contactArchived).apply {
            setConversationVisibilityFromSync(ConversationVisibility.ARCHIVED)
        }

        conversationCategoryService.setPrivateMark(ContactConversationId(contactArchived.identity))

        val useCase = getUseCaseUnderTest()

        // Count including private
        val count = useCase.call(
            filterQuery = null,
            excludePrivate = false,
        )
        assertEquals(1, count)

        // Count excluding private
        val countExcludingPrivate = useCase.call(
            filterQuery = null,
            excludePrivate = true,
        )
        assertEquals(0, countExcludingPrivate)
    }
}
 */
