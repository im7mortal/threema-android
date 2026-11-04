package ch.threema.app.processors.incomingcspmessage.blocking

import ch.threema.app.protocolsteps.BlockState
import ch.threema.app.protocolsteps.DedicatedGroupSetupBlockingExemptionSteps
import ch.threema.app.protocolsteps.IdentityBlockedSteps
import ch.threema.domain.protocol.csp.messages.AbstractMessage
import ch.threema.domain.protocol.csp.messages.ExemptFromBlocking
import ch.threema.domain.protocol.csp.messages.GroupSetupMessage
import ch.threema.domain.types.Identity
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertTrue
import kotlin.test.Test
import kotlin.test.assertFalse
import testdata.TestData

class IncomingCspMessageBlockingDetectorTest {

    @Test
    fun `should exempt from blocking`() {
        // arrange
        val identityBlockedSteps = mockk<IdentityBlockedSteps>()
        val dedicatedGroupSetupBlockingExemptionSteps = mockk<DedicatedGroupSetupBlockingExemptionSteps>()
        val detector = IncomingCspMessageBlockingDetector(
            identityBlockedSteps = identityBlockedSteps,
            dedicatedGroupSetupBlockingExemptionSteps = dedicatedGroupSetupBlockingExemptionSteps,
        )

        // act
        val shouldBlock = detector.shouldBlockMessage(
            message = TestMessage(
                fromIdentity = TestData.Identities.OTHER_1,
                exemptFromBlocking = ExemptFromBlocking.YES,
            ),
        )

        // assert
        assertFalse(shouldBlock)
        confirmVerified(identityBlockedSteps, dedicatedGroupSetupBlockingExemptionSteps)
    }

    @Test
    fun `should not exempt from blocking - explicitly blocked`() {
        // arrange
        val message = TestMessage(
            fromIdentity = TestData.Identities.OTHER_1,
            exemptFromBlocking = ExemptFromBlocking.NO,
        )
        val identityBlockedSteps = mockk<IdentityBlockedSteps> {
            every { run(TestData.Identities.OTHER_1.value) } returns BlockState.EXPLICITLY_BLOCKED
        }
        val dedicatedGroupSetupBlockingExemptionSteps = mockk<DedicatedGroupSetupBlockingExemptionSteps>()
        val detector = IncomingCspMessageBlockingDetector(
            identityBlockedSteps = identityBlockedSteps,
            dedicatedGroupSetupBlockingExemptionSteps = dedicatedGroupSetupBlockingExemptionSteps,
        )

        // act
        val shouldBlock = detector.shouldBlockMessage(message)

        // assert
        assertTrue(shouldBlock)
        verify(exactly = 1) {
            identityBlockedSteps.run(TestData.Identities.OTHER_1.value)
        }
        confirmVerified(dedicatedGroupSetupBlockingExemptionSteps)
    }

    @Test
    fun `should not exempt from blocking - implicitly blocked`() {
        // arrange
        val message = TestMessage(
            fromIdentity = TestData.Identities.OTHER_1,
            exemptFromBlocking = ExemptFromBlocking.NO,
        )
        val identityBlockedSteps = mockk<IdentityBlockedSteps> {
            every { run(TestData.Identities.OTHER_1.value) } returns BlockState.IMPLICITLY_BLOCKED
        }
        val dedicatedGroupSetupBlockingExemptionSteps = mockk<DedicatedGroupSetupBlockingExemptionSteps>()
        val detector = IncomingCspMessageBlockingDetector(
            identityBlockedSteps = identityBlockedSteps,
            dedicatedGroupSetupBlockingExemptionSteps = dedicatedGroupSetupBlockingExemptionSteps,
        )

        // act
        val shouldBlock = detector.shouldBlockMessage(message)

        // assert
        assertTrue(shouldBlock)
        verify(exactly = 1) {
            identityBlockedSteps.run(TestData.Identities.OTHER_1.value)
        }
        confirmVerified(dedicatedGroupSetupBlockingExemptionSteps)
    }

    @Test
    fun `should not exempt from blocking - not blocked`() {
        // arrange
        val message = TestMessage(
            fromIdentity = TestData.Identities.OTHER_1,
            exemptFromBlocking = ExemptFromBlocking.NO,
        )
        val identityBlockedSteps = mockk<IdentityBlockedSteps> {
            every { run(TestData.Identities.OTHER_1.value) } returns BlockState.NOT_BLOCKED
        }
        val dedicatedGroupSetupBlockingExemptionSteps = mockk<DedicatedGroupSetupBlockingExemptionSteps>()
        val detector = IncomingCspMessageBlockingDetector(
            identityBlockedSteps = identityBlockedSteps,
            dedicatedGroupSetupBlockingExemptionSteps = dedicatedGroupSetupBlockingExemptionSteps,
        )

        // act
        val shouldBlock = detector.shouldBlockMessage(message)

        // assert
        assertFalse(shouldBlock)
        verify(exactly = 1) {
            identityBlockedSteps.run(TestData.Identities.OTHER_1.value)
        }
        confirmVerified(dedicatedGroupSetupBlockingExemptionSteps)
    }

    @Test
    fun `should not exempt from blocking and run dedicated steps - block`() {
        // arrange
        val groupSetupMessage = GroupSetupMessage()
        val identityBlockedSteps = mockk<IdentityBlockedSteps>()
        val dedicatedGroupSetupBlockingExemptionSteps = mockk<DedicatedGroupSetupBlockingExemptionSteps> {
            every { run(groupSetupMessage) } returns DedicatedGroupSetupBlockingExemptionSteps.Result.BLOCK
        }
        val detector = IncomingCspMessageBlockingDetector(
            identityBlockedSteps = identityBlockedSteps,
            dedicatedGroupSetupBlockingExemptionSteps = dedicatedGroupSetupBlockingExemptionSteps,
        )

        // act
        val shouldBlock = detector.shouldBlockMessage(groupSetupMessage)

        // assert
        assertTrue(shouldBlock)
        verify(exactly = 1) {
            dedicatedGroupSetupBlockingExemptionSteps.run(groupSetupMessage)
        }
        confirmVerified(identityBlockedSteps)
    }

    @Test
    fun `should not exempt from blocking and run dedicated steps - pass`() {
        // arrange
        val groupSetupMessage = GroupSetupMessage()
        val identityBlockedSteps = mockk<IdentityBlockedSteps>()
        val dedicatedGroupSetupBlockingExemptionSteps = mockk<DedicatedGroupSetupBlockingExemptionSteps> {
            every { run(groupSetupMessage) } returns DedicatedGroupSetupBlockingExemptionSteps.Result.PASS
        }
        val detector = IncomingCspMessageBlockingDetector(
            identityBlockedSteps = identityBlockedSteps,
            dedicatedGroupSetupBlockingExemptionSteps = dedicatedGroupSetupBlockingExemptionSteps,
        )

        // act
        val shouldBlock = detector.shouldBlockMessage(groupSetupMessage)

        // assert
        assertFalse(shouldBlock)
        verify(exactly = 1) {
            dedicatedGroupSetupBlockingExemptionSteps.run(groupSetupMessage)
        }
        confirmVerified(identityBlockedSteps)
    }

    @Test
    fun `should block message when dedicated blocking exemption steps are missing`() {
        // arrange
        val message = TestMessage(
            fromIdentity = TestData.Identities.OTHER_1,
            exemptFromBlocking = ExemptFromBlocking.DEDICATED_STEPS,
        )
        val identityBlockedSteps = mockk<IdentityBlockedSteps>()
        val dedicatedGroupSetupBlockingExemptionSteps = mockk<DedicatedGroupSetupBlockingExemptionSteps>()
        val detector = IncomingCspMessageBlockingDetector(
            identityBlockedSteps = identityBlockedSteps,
            dedicatedGroupSetupBlockingExemptionSteps = dedicatedGroupSetupBlockingExemptionSteps,
        )

        // act
        val shouldBlock = detector.shouldBlockMessage(message)

        // assert
        assertTrue(shouldBlock)
        confirmVerified(identityBlockedSteps, dedicatedGroupSetupBlockingExemptionSteps)
    }

    private class TestMessage(
        fromIdentity: Identity,
        private val exemptFromBlocking: ExemptFromBlocking,
    ) : AbstractMessage() {
        init {
            this.fromIdentity = fromIdentity.value
        }

        override fun getType() = 0
        override fun getMinimumRequiredForwardSecurityVersion() = null
        override fun getBody() = null
        override fun allowUserProfileDistribution() = false
        override fun exemptFromBlocking() = exemptFromBlocking
        override fun createImplicitlyDirectContact() = false
        override fun protectAgainstReplay() = true
        override fun reflectIncoming() = true
        override fun reflectOutgoing() = true
        override fun reflectSentUpdate() = true
        override fun sendAutomaticDeliveryReceipt() = true
        override fun bumpLastUpdate() = true
    }
}
