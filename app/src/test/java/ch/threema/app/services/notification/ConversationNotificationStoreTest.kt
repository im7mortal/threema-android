package ch.threema.app.services.notification

import ch.threema.android.toResolvedString
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.domain.types.MessageUid
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import testdata.TestData

class ConversationNotificationStoreTest {

    @Test
    fun `access and interact with the store`() {
        val store = ConversationNotificationStore()

        store.accessExclusively { storeAccessor ->
            // Initial state
            assertFalse(storeAccessor.notificationsExist())
            assertEquals(0, storeAccessor.getConversationsCount())
            assertEquals(0, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(emptyList(), storeAccessor.getMessageNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(emptyList(), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(emptyList(), storeAccessor.getAllNotifications())
            assertFalse(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID1))
            assertFalse(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID1))
            assertFalse(storeAccessor.messageNotificationForMessageExists(MESSAGE_UID1))
            assertFalse(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID1))

            // add 1 message notification for conversation 1
            val notification1 = mockMessageNotification(CONVERSATION_ID1, MESSAGE_UID1)
            storeAccessor.addMessageNotification(notification1)
            assertTrue(storeAccessor.notificationsExist())
            assertEquals(1, storeAccessor.getConversationsCount())
            assertEquals(1, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(notification1), storeAccessor.getMessageNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(1, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID1))
            assertEquals(0, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID2))
            assertEquals(listOf(notification1), storeAccessor.getAllNotifications())
            assertTrue(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID1))
            assertTrue(storeAccessor.messageNotificationForMessageExists(MESSAGE_UID1))

            // try to add the same message notification for conversation 1
            storeAccessor.addMessageNotification(notification1)
            assertEquals(1, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(notification1), storeAccessor.getMessageNotificationsForConversation(CONVERSATION_ID1))

            // add 1 reaction notification for conversation 1
            val reactionNotification1 = mockReactionNotification(CONVERSATION_ID1, MESSAGE_UID1)
            storeAccessor.addReactionNotification(reactionNotification1)
            assertTrue(storeAccessor.notificationsExist())
            assertEquals(1, storeAccessor.getConversationsCount())
            assertEquals(1, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(reactionNotification1), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(1, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID1))
            assertEquals(0, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID2))
            assertEquals(listOf(reactionNotification1, notification1), storeAccessor.getAllNotifications())
            assertTrue(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID1))
            assertTrue(storeAccessor.messageNotificationForMessageExists(MESSAGE_UID1))
            assertTrue(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID1))

            // try to add the same reaction notification for conversation 1
            storeAccessor.addReactionNotification(reactionNotification1)
            assertEquals(1, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(reactionNotification1), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID1))

            // add 2 notifications for conversation 2 and 3 each
            val notification2 = mockMessageNotification(CONVERSATION_ID2, MESSAGE_UID2)
            val notification3 = mockMessageNotification(CONVERSATION_ID3, MESSAGE_UID3)
            val notification4 = mockMessageNotification(CONVERSATION_ID3, MESSAGE_UID4)
            val notification5 = mockMessageNotification(CONVERSATION_ID2, MESSAGE_UID5)
            storeAccessor.addMessageNotification(notification2)
            storeAccessor.addMessageNotification(notification3)
            storeAccessor.addMessageNotification(notification4)
            storeAccessor.addMessageNotification(notification5)
            assertTrue(storeAccessor.notificationsExist())
            assertEquals(3, storeAccessor.getConversationsCount())
            assertEquals(5, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(notification1), storeAccessor.getMessageNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(listOf(reactionNotification1), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(listOf(notification5, notification2), storeAccessor.getMessageNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(emptyList(), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(listOf(notification4, notification3), storeAccessor.getMessageNotificationsForConversation(CONVERSATION_ID3))
            assertEquals(emptyList(), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID3))
            assertEquals(1, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID1))
            assertEquals(2, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID2))
            assertEquals(2, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID2))
            assertEquals(
                listOf(
                    notification5,
                    notification4,
                    notification3,
                    notification2,
                    reactionNotification1,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )

            // replace 1 notification
            val notification2replacement = mockMessageNotification(CONVERSATION_ID2, MESSAGE_UID2)
            storeAccessor.replaceMessageNotification(notification2replacement)
            assertEquals(3, storeAccessor.getConversationsCount())
            assertEquals(5, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(notification5, notification2replacement), storeAccessor.getMessageNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(emptyList(), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(2, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID2))
            assertEquals(
                listOf(
                    notification5,
                    notification4,
                    notification3,
                    notification2replacement,
                    reactionNotification1,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )

            // replace 1 notification that does not exist
            val notification6replacement = mockMessageNotification(CONVERSATION_ID2, MESSAGE_UID6)
            storeAccessor.replaceMessageNotification(notification6replacement)
            assertEquals(3, storeAccessor.getConversationsCount())
            assertEquals(5, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(
                listOf(
                    notification5,
                    notification4,
                    notification3,
                    notification2replacement,
                    reactionNotification1,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )

            // remove 1 notification from conversation 3
            storeAccessor.removeMessageNotification(MESSAGE_UID4)
            assertEquals(3, storeAccessor.getConversationsCount())
            assertEquals(4, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(notification3), storeAccessor.getMessageNotificationsForConversation(CONVERSATION_ID3))
            assertEquals(emptyList(), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID3))
            assertEquals(1, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID3))
            assertEquals(
                listOf(
                    notification5,
                    notification3,
                    notification2replacement,
                    reactionNotification1,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )

            // remove 1 notification that does not exist
            storeAccessor.removeMessageNotification(MESSAGE_UID6)
            assertEquals(3, storeAccessor.getConversationsCount())
            assertEquals(4, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(
                listOf(
                    notification5,
                    notification3,
                    notification2replacement,
                    reactionNotification1,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )

            // remove all notifications of conversation 2
            storeAccessor.removeNotificationsForConversation(CONVERSATION_ID2)
            assertEquals(2, storeAccessor.getConversationsCount())
            assertEquals(2, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(emptyList(), storeAccessor.getMessageNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(emptyList(), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(0, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID2))
            assertEquals(
                listOf(
                    notification3,
                    reactionNotification1,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )

            // add another reaction notification to conversation 2
            val reactionNotification2 = mockReactionNotification(CONVERSATION_ID2, MESSAGE_UID2)
            storeAccessor.addReactionNotification(reactionNotification2)
            assertTrue(storeAccessor.notificationsExist())
            assertEquals(3, storeAccessor.getConversationsCount())
            assertEquals(2, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(reactionNotification1), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(listOf(reactionNotification2), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(1, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID1))
            assertEquals(0, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID2))
            assertEquals(
                listOf(
                    reactionNotification2,
                    notification3,
                    reactionNotification1,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )
            assertTrue(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID1))
            assertFalse(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID2))
            assertTrue(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID1))
            assertTrue(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID2))

            // replace a reaction notification that does not exist
            val reactionNotification3NonExistent = mockReactionNotification(CONVERSATION_ID2, MESSAGE_UID3)
            storeAccessor.replaceReactionNotification(reactionNotification3NonExistent)
            assertTrue(storeAccessor.notificationsExist())
            assertEquals(3, storeAccessor.getConversationsCount())
            assertEquals(2, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(reactionNotification1), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(listOf(reactionNotification2), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(1, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID1))
            assertEquals(0, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID2))
            assertEquals(
                listOf(
                    reactionNotification2,
                    notification3,
                    reactionNotification1,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )
            assertTrue(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID1))
            assertFalse(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID2))
            assertTrue(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID1))
            assertTrue(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID2))

            // replace reaction notification 2
            val reactionNotification2Replacement = mockReactionNotification(CONVERSATION_ID2, MESSAGE_UID2)
            storeAccessor.replaceReactionNotification(reactionNotification2Replacement)
            assertTrue(storeAccessor.notificationsExist())
            assertEquals(3, storeAccessor.getConversationsCount())
            assertEquals(2, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(listOf(reactionNotification1), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(listOf(reactionNotification2Replacement), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(1, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID1))
            assertEquals(0, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID2))
            assertEquals(
                listOf(
                    reactionNotification2Replacement,
                    notification3,
                    reactionNotification1,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )
            assertTrue(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID1))
            assertFalse(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID2))
            assertTrue(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID1))
            assertTrue(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID2))

            // remove reaction notification 1
            storeAccessor.removeReactionNotification(MESSAGE_UID1)
            assertTrue(storeAccessor.notificationsExist())
            assertEquals(3, storeAccessor.getConversationsCount())
            assertEquals(2, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(emptyList(), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID1))
            assertEquals(listOf(reactionNotification2Replacement), storeAccessor.getReactionNotificationsForConversation(CONVERSATION_ID2))
            assertEquals(1, storeAccessor.getMessageNotificationCountForConversation(CONVERSATION_ID1))
            assertEquals(
                listOf(
                    reactionNotification2Replacement,
                    notification3,
                    notification1,
                ),
                storeAccessor.getAllNotifications(),
            )
            assertTrue(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID1))
            assertFalse(storeAccessor.messageNotificationsForConversationExist(CONVERSATION_ID2))
            assertFalse(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID1))
            assertTrue(storeAccessor.reactionNotificationForMessageExists(MESSAGE_UID2))

            // remove all notifications
            storeAccessor.removeAllNotifications()
            assertFalse(storeAccessor.notificationsExist())
            assertEquals(0, storeAccessor.getConversationsCount())
            assertEquals(0, storeAccessor.getTotalMessageNotificationCount())
            assertEquals(emptyList(), storeAccessor.getAllNotifications())
        }
    }

    companion object {
        private val CONVERSATION_ID1 = GroupConversationId(1)
        private val CONVERSATION_ID2 = GroupConversationId(2)
        private val CONVERSATION_ID3 = ContactConversationId(TestData.Identities.OTHER_1.value)
        private const val MESSAGE_UID1: MessageUid = "1"
        private const val MESSAGE_UID2: MessageUid = "2"
        private const val MESSAGE_UID3: MessageUid = "3"
        private const val MESSAGE_UID4: MessageUid = "4"
        private const val MESSAGE_UID5: MessageUid = "5"
        private const val MESSAGE_UID6: MessageUid = "6"

        private fun mockMessageNotification(conversationId: ConversationId, messageUid: MessageUid): ConversationMessageNotification =
            mockk("conversation=$conversationId,message=$messageUid,type=message") {
                every { this@mockk.messageUid } returns messageUid
                every { conversationHandle } returns NotificationConversationHandle(
                    conversationId = conversationId,
                    name = "".toResolvedString(),
                    notificationTriggerPolicyOverride = null,
                    onFetchAvatar = { null },
                )
            }

        private fun mockReactionNotification(conversationId: ConversationId, messageUid: MessageUid): ConversationReactionNotification =
            mockk("conversation=$conversationId,message=$messageUid,type=reaction") {
                every { this@mockk.messageUid } returns messageUid
                every { conversationHandle } returns NotificationConversationHandle(
                    conversationId = conversationId,
                    name = "".toResolvedString(),
                    notificationTriggerPolicyOverride = null,
                    onFetchAvatar = { null },
                )
            }
    }
}
