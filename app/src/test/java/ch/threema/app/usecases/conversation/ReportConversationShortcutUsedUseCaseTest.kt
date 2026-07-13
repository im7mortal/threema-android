package ch.threema.app.usecases.conversation

import androidx.core.content.pm.ShortcutManagerCompat
import ch.threema.app.preference.service.PreferenceService
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import testdata.TestData.Identities

class ReportConversationShortcutUsedUseCaseTest {

    @BeforeTest
    fun beforeTest() {
        mockkStatic(ShortcutManagerCompat::class)
    }

    @AfterTest
    fun afterTest() {
        unmockkStatic(ShortcutManagerCompat::class)
    }

    @Test
    fun `should report as used if direct-share is enabled`() = runTest {
        // arrange
        every { ShortcutManagerCompat.reportShortcutUsed(any(), any()) } just runs
        val preferenceServiceMock = mockk<PreferenceService> {
            every { isDirectShare() } returns true
        }
        val useCase = ReportConversationShortcutUsedUseCase(
            appContext = mockk(),
            preferenceService = preferenceServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        // act
        useCase.call(
            conversationId = ContactConversationId(identity = Identities.OTHER_1.value),
        )

        // assert
        verify(exactly = 1) {
            ShortcutManagerCompat.reportShortcutUsed(
                /* context = */
                any(),
                /* shortcutId = */
                "CTAP6ZBDZFGQZ4PGKPV4R7YRT25TGQCBACXQC6ZJMYAK6OGBCURA",
            )
        }
    }

    @Test
    fun `should not report as used if direct-share is disabled`() = runTest {
        // arrange
        val preferenceServiceMock = mockk<PreferenceService> {
            every { isDirectShare() } returns false
        }
        val useCase = ReportConversationShortcutUsedUseCase(
            appContext = mockk(),
            preferenceService = preferenceServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )

        // act
        useCase.call(
            conversationId = ContactConversationId(identity = Identities.OTHER_1.value),
        )

        // assert
        verify(exactly = 0) {
            ShortcutManagerCompat.reportShortcutUsed(
                /* context = */
                any(),
                /* shortcutId = */
                any(),
            )
        }
    }
}
