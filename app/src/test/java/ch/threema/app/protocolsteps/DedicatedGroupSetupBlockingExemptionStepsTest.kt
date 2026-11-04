package ch.threema.app.protocolsteps

import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.domain.models.GroupId
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import testdata.TestData

class DedicatedGroupSetupBlockingExemptionStepsTest {

    @Test
    fun `returns PASS if group model exists locally`() {
        // arrange
        val groupCreatorIdentity = TestData.Identities.OTHER_1.value
        val groupApiId = GroupId(1L)
        val groupIdentity = GroupIdentity(
            creatorIdentity = groupCreatorIdentity,
            groupId = groupApiId.toLong(),
        )
        val groupModelRepository = mockk<GroupModelRepository> {
            every { exists(groupIdentity) } returns true
        }
        val identityBlockedSteps = mockk<IdentityBlockedSteps>()
        val groupSetupMessage = GroupSetupMessage().apply {
            this.groupCreator = groupCreatorIdentity
            this.apiGroupId = groupApiId
        }
        val steps = DedicatedGroupSetupBlockingExemptionSteps(
            groupModelRepository = groupModelRepository,
            identityBlockedSteps = identityBlockedSteps,
        )

        // act
        val result = steps.run(groupSetupMessage)

        // assert
        assertEquals(
            expected = DedicatedGroupSetupBlockingExemptionSteps.Result.PASS,
            actual = result,
        )
        verify(exactly = 1) {
            groupModelRepository.exists(groupIdentity)
        }
        verify(exactly = 0) {
            identityBlockedSteps.run(groupCreatorIdentity)
        }
    }

    @Test
    fun `returns PASS if group creator is not blocked`() {
        // arrange
        val groupCreatorIdentity = TestData.Identities.OTHER_1.value
        val groupApiId = GroupId(1L)
        val groupIdentity = GroupIdentity(
            creatorIdentity = groupCreatorIdentity,
            groupId = groupApiId.toLong(),
        )
        val groupModelRepository = mockk<GroupModelRepository> {
            every { exists(groupIdentity) } returns false
        }
        val identityBlockedSteps = mockk<IdentityBlockedSteps> {
            every { run(groupCreatorIdentity) } returns BlockState.NOT_BLOCKED
        }
        val groupSetupMessage = GroupSetupMessage().apply {
            this.groupCreator = groupCreatorIdentity
            this.apiGroupId = groupApiId
        }
        val steps = DedicatedGroupSetupBlockingExemptionSteps(
            groupModelRepository = groupModelRepository,
            identityBlockedSteps = identityBlockedSteps,
        )

        // act
        val result = steps.run(groupSetupMessage)

        // assert
        assertEquals(
            expected = DedicatedGroupSetupBlockingExemptionSteps.Result.PASS,
            actual = result,
        )
        verify(exactly = 1) {
            groupModelRepository.exists(groupIdentity)
        }
        verify(exactly = 1) {
            identityBlockedSteps.run(groupCreatorIdentity)
        }
    }

    @Test
    fun `returns BLOCK if group creator is explicitly blocked`() {
        // arrange
        val groupCreatorIdentity = TestData.Identities.OTHER_1.value
        val groupApiId = GroupId(1L)
        val groupIdentity = GroupIdentity(
            creatorIdentity = groupCreatorIdentity,
            groupId = groupApiId.toLong(),
        )
        val groupModelRepository = mockk<GroupModelRepository> {
            every { exists(groupIdentity) } returns false
        }
        val identityBlockedSteps = mockk<IdentityBlockedSteps> {
            every { run(groupCreatorIdentity) } returns BlockState.EXPLICITLY_BLOCKED
        }
        val groupSetupMessage = GroupSetupMessage().apply {
            this.groupCreator = groupCreatorIdentity
            this.apiGroupId = groupApiId
        }
        val steps = DedicatedGroupSetupBlockingExemptionSteps(
            groupModelRepository = groupModelRepository,
            identityBlockedSteps = identityBlockedSteps,
        )

        // act
        val result = steps.run(groupSetupMessage)

        // assert
        assertEquals(
            expected = DedicatedGroupSetupBlockingExemptionSteps.Result.BLOCK,
            actual = result,
        )
        verify(exactly = 1) {
            groupModelRepository.exists(groupIdentity)
        }
        verify(exactly = 1) {
            identityBlockedSteps.run(groupCreatorIdentity)
        }
    }

    @Test
    fun `returns BLOCK if group creator is implicitly blocked`() {
        // arrange
        val groupCreatorIdentity = TestData.Identities.OTHER_1.value
        val groupApiId = GroupId(1L)
        val groupIdentity = GroupIdentity(
            creatorIdentity = groupCreatorIdentity,
            groupId = groupApiId.toLong(),
        )
        val groupModelRepository = mockk<GroupModelRepository> {
            every { exists(groupIdentity) } returns false
        }
        val identityBlockedSteps = mockk<IdentityBlockedSteps> {
            every { run(groupCreatorIdentity) } returns BlockState.IMPLICITLY_BLOCKED
        }
        val groupSetupMessage = GroupSetupMessage().apply {
            this.groupCreator = groupCreatorIdentity
            this.apiGroupId = groupApiId
        }
        val steps = DedicatedGroupSetupBlockingExemptionSteps(
            groupModelRepository = groupModelRepository,
            identityBlockedSteps = identityBlockedSteps,
        )

        // act
        val result = steps.run(groupSetupMessage)

        // assert
        assertEquals(
            expected = DedicatedGroupSetupBlockingExemptionSteps.Result.BLOCK,
            actual = result,
        )
        verify(exactly = 1) {
            groupModelRepository.exists(groupIdentity)
        }
        verify(exactly = 1) {
            identityBlockedSteps.run(groupCreatorIdentity)
        }
    }
}
