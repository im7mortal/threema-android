package ch.threema.app.drafts

import ch.threema.app.preference.service.PreferenceService
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.domain.models.MessageId
import ch.threema.testhelpers.testDispatcherProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import testdata.TestData

@OptIn(ExperimentalCoroutinesApi::class)
class DraftManagerImplTest {

    @Test
    fun `drafts are restored from storage`() = runTest {
        val draftManager = DraftManagerImpl(
            preferenceService = mockk {
                every { getMessageDrafts() } returns mapOf(
                    conversationId1.obfuscated to "Hello",
                    conversationId2.obfuscated to "World",
                )
                every { getQuoteDrafts() } returns mapOf(
                    conversationId1.obfuscated to MESSAGE_ID_STRING,
                )
            },
            dispatcherProvider = testDispatcherProvider(),
        )

        draftManager.init()

        assertEquals(
            MessageDraft(
                text = "Hello",
                quotedMessageId = MESSAGE_ID,
            ),
            draftManager.get(conversationId1),
        )
        assertEquals(
            MessageDraft(
                text = "World",
                quotedMessageId = null,
            ),
            draftManager.get(conversationId2),
        )
        assertNull(draftManager.get(conversationId3))
    }

    @Test
    fun `drafts can be retrieved after being set`() = runTest {
        val draftManager = DraftManagerImpl(
            preferenceService = mockk {
                every { getMessageDrafts() } returns emptyMap()
                every { getQuoteDrafts() } returns emptyMap()
            },
            dispatcherProvider = testDispatcherProvider(),
        )
        draftManager.init()

        draftManager.set(conversationId1, text = "Hello", quotedMessageId = MESSAGE_ID)
        draftManager.set(conversationId2, text = "World")

        assertEquals(
            MessageDraft(
                text = "Hello",
                quotedMessageId = MESSAGE_ID,
            ),
            draftManager.get(conversationId1),
        )
        assertEquals(
            MessageDraft(
                text = "World",
                quotedMessageId = null,
            ),
            draftManager.get(conversationId2),
        )
        assertNull(draftManager.get(conversationId3))
    }

    @Test
    fun `drafts are persisted when set`() = runTest {
        val preferenceServiceMock = mockk<PreferenceService> {
            every { getMessageDrafts() } returns emptyMap()
            every { getQuoteDrafts() } returns emptyMap()
            every { setMessageDrafts(any()) } just runs
            every { setQuoteDrafts(any()) } just runs
        }
        val draftManager = DraftManagerImpl(
            preferenceService = preferenceServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )
        draftManager.init()
        advanceUntilIdle()

        draftManager.set(conversationId1, text = "Hello", quotedMessageId = MESSAGE_ID)
        draftManager.set(conversationId2, text = "World")
        advanceUntilIdle()

        verify(exactly = 1) {
            preferenceServiceMock.setMessageDrafts(
                mapOf(
                    conversationId1.obfuscated to "Hello",
                    conversationId2.obfuscated to "World",
                ),
            )
        }
        verify(exactly = 1) {
            preferenceServiceMock.setQuoteDrafts(
                mapOf(
                    conversationId1.obfuscated to MESSAGE_ID_STRING,
                ),
            )
        }
    }

    @Test
    fun `drafts can be replaced and removed`() = runTest {
        val preferenceServiceMock = mockk<PreferenceService> {
            every { getMessageDrafts() } returns mapOf(
                conversationId1.obfuscated to "Hello",
                conversationId2.obfuscated to "World",
            )
            every { getQuoteDrafts() } returns mapOf(
                conversationId1.obfuscated to MESSAGE_ID_STRING,
            )
            every { setMessageDrafts(any()) } just runs
            every { setQuoteDrafts(any()) } just runs
        }
        val draftManager = DraftManagerImpl(
            preferenceService = preferenceServiceMock,
            dispatcherProvider = testDispatcherProvider(),
        )
        draftManager.init()
        advanceUntilIdle()

        draftManager.set(conversationId1, text = "HELLO!!!")
        draftManager.remove(conversationId2)
        advanceUntilIdle()

        verify(exactly = 1) {
            preferenceServiceMock.setMessageDrafts(
                mapOf(
                    conversationId1.obfuscated to "HELLO!!!",
                ),
            )
        }
        verify(exactly = 1) {
            preferenceServiceMock.setQuoteDrafts(emptyMap())
        }
    }

    companion object {
        private val conversationId1 = ContactConversationId(identity = TestData.Identities.OTHER_1.value)
        private val conversationId2 = ContactConversationId(identity = TestData.Identities.OTHER_2.value)
        private val conversationId3 = ContactConversationId(identity = TestData.Identities.OTHER_3.value)

        private const val MESSAGE_ID_STRING = "00dead0000beef00"
        private val MESSAGE_ID = MessageId.fromString(MESSAGE_ID_STRING)
    }
}
