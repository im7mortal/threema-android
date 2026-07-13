package ch.threema.app.usecases.availabilitystatus

import app.cash.turbine.test
import ch.threema.app.BuildConfig
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.testhelpers.expectItem
import ch.threema.testhelpers.unconfinedTestDispatcherProvider
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assume
import testdata.TestData

class WatchAllContactAvailabilityStatusesUseCaseTest {

    @Test
    fun `feature supported - should emit current value`() = runTest {
        // precondition
        Assume.assumeTrue(BuildConfig.AVAILABILITY_STATUS_ENABLED)

        // arrange
        val allInitial = listOf(
            TestData.createContactModel(
                identity = TestData.Identities.OTHER_1,
                availabilityStatus = AvailabilityStatus.Busy(),
            ),
        )
        val allUpdated = listOf(
            TestData.createContactModel(
                identity = TestData.Identities.OTHER_1,
                availabilityStatus = AvailabilityStatus.Unavailable(
                    description = "On vacation",
                ),
            ),
        )
        val contactModelRepositoryMock = mockk<ContactModelRepository> {
            every { getAll() } returns allInitial andThen allUpdated
        }
        val contactEventsFlow = MutableSharedFlow<ContactEvent>()
        val globalEventFlowsMock = mockk<GlobalEventFlows> {
            every { contacts } returns contactEventsFlow
        }
        val useCase = WatchAllContactAvailabilityStatusesUseCase(
            contactModelRepository = contactModelRepositoryMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
            globalEventFlows = globalEventFlowsMock,
        )

        // act / assert
        useCase.call().test {
            // Expect the initial items
            expectItem(
                mapOf(
                    TestData.Identities.OTHER_1.value to AvailabilityStatus.Busy(),
                ),
            )

            // Expect the updated items
            contactEventsFlow.emit(ContactEvent.ContactUpdated(TestData.Identities.OTHER_1))
            expectItem(
                mapOf(
                    TestData.Identities.OTHER_1.value to AvailabilityStatus.Unavailable(description = "On vacation"),
                ),
            )

            // Expect no more
            ensureAllEventsConsumed()
        }

        coVerify(exactly = 2) { contactModelRepositoryMock.getAll() }
    }

    @Test
    fun `feature not supported - emits empty map`() = runTest {
        // precondition
        Assume.assumeFalse(BuildConfig.AVAILABILITY_STATUS_ENABLED)

        // arrange
        val contactModelRepositoryMock = mockk<ContactModelRepository>()
        val globalEventFlowsMock = mockk<GlobalEventFlows>()
        val useCase = WatchAllContactAvailabilityStatusesUseCase(
            contactModelRepository = contactModelRepositoryMock,
            dispatcherProvider = unconfinedTestDispatcherProvider(),
            globalEventFlows = globalEventFlowsMock,
        )

        // act / assert
        useCase.call().test {
            expectItem(emptyMap())
            awaitComplete()
        }
        verify { contactModelRepositoryMock wasNot Called }
    }
}
