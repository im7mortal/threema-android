package ch.threema.app.services.notification

import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ConversationId
import ch.threema.domain.types.MessageUid
import java.util.LinkedList

private val logger = getThreemaLogger("ConversationNotificationStore")

class ConversationNotificationStore {
    private val notifications = LinkedList<ConversationNotification>()

    private val storeAccessor = object : StoreAccessor {

        override fun getTotalMessageNotificationCount(): Int =
            notifications
                .count { it is ConversationMessageNotification }

        override fun getConversationsCount(): Int =
            notifications
                .distinctBy { it.conversationHandle.conversationId }
                .count()

        override fun getMessageNotificationCountForConversation(conversationId: ConversationId): Int =
            notifications
                .filterIsInstance<ConversationMessageNotification>()
                .count { it.conversationHandle.conversationId == conversationId }

        override fun notificationsExist(): Boolean =
            notifications.isNotEmpty()

        override fun messageNotificationsForConversationExist(conversationId: ConversationId): Boolean =
            notifications
                .filterIsInstance<ConversationMessageNotification>()
                .any { it.conversationHandle.conversationId == conversationId }

        override fun messageNotificationForMessageExists(messageUid: MessageUid): Boolean =
            notifications
                .filterIsInstance<ConversationMessageNotification>()
                .any { it.messageUid == messageUid }

        override fun reactionNotificationForMessageExists(messageUid: MessageUid): Boolean =
            notifications
                .filterIsInstance<ConversationReactionNotification>()
                .any { it.messageUid == messageUid }

        override fun getAllNotifications(): List<ConversationNotification> =
            notifications.toList()

        override fun getMessageNotificationsForConversation(conversationId: ConversationId): List<ConversationMessageNotification> =
            notifications
                .filterIsInstance<ConversationMessageNotification>()
                .filter { it.conversationHandle.conversationId == conversationId }

        override fun getReactionNotificationsForConversation(conversationId: ConversationId): List<ConversationReactionNotification> =
            notifications
                .filterIsInstance<ConversationReactionNotification>()
                .filter { it.conversationHandle.conversationId == conversationId }

        override fun addMessageNotification(messageNotification: ConversationMessageNotification) {
            if (messageNotificationForMessageExists(messageNotification.messageUid)) {
                logger.error("A message notification for message {} is already present", messageNotification.messageUid)
                return
            }
            notifications.addFirst(messageNotification)
        }

        override fun addReactionNotification(reactionNotification: ConversationReactionNotification) {
            if (reactionNotificationForMessageExists(reactionNotification.messageUid)) {
                logger.error("A reaction notification for message {} is already present", reactionNotification.messageUid)
                return
            }
            notifications.addFirst(reactionNotification)
        }

        override fun replaceMessageNotification(messageNotification: ConversationMessageNotification) {
            val iterator = notifications.listIterator()
            while (iterator.hasNext()) {
                val notification = iterator.next()
                if (notification.messageUid == messageNotification.messageUid && notification is ConversationMessageNotification) {
                    iterator.set(messageNotification)
                    return
                }
            }
        }

        override fun replaceReactionNotification(reactionNotification: ConversationReactionNotification) {
            val iterator = notifications.listIterator()
            while (iterator.hasNext()) {
                val notification = iterator.next()
                if (notification.messageUid == reactionNotification.messageUid && notification is ConversationReactionNotification) {
                    iterator.set(reactionNotification)
                    return
                }
            }
        }

        override fun removeMessageNotification(messageUid: MessageUid) {
            val iterator = notifications.listIterator()
            while (iterator.hasNext()) {
                val notification = iterator.next()
                if (notification.messageUid == messageUid && notification is ConversationMessageNotification) {
                    iterator.remove()
                    return
                }
            }
        }

        override fun removeReactionNotification(messageUid: MessageUid) {
            val iterator = notifications.listIterator()
            while (iterator.hasNext()) {
                val notification = iterator.next()
                if (notification.messageUid == messageUid && notification is ConversationReactionNotification) {
                    iterator.remove()
                    return
                }
            }
        }

        override fun removeNotificationsForConversation(conversationId: ConversationId) {
            val iterator = notifications.iterator()
            while (iterator.hasNext()) {
                val conversationNotification = iterator.next()
                if (conversationNotification.conversationHandle.conversationId == conversationId) {
                    iterator.remove()
                }
            }
        }

        override fun removeMessageNotificationsForConversation(conversationId: ConversationId) {
            val iterator = notifications.iterator()
            while (iterator.hasNext()) {
                val conversationNotification = iterator.next()
                if (
                    conversationNotification is ConversationMessageNotification &&
                    conversationNotification.conversationHandle.conversationId == conversationId
                ) {
                    iterator.remove()
                }
            }
        }

        override fun removeAllNotifications() {
            notifications.clear()
        }
    }

    fun accessExclusively(block: (StoreAccessor) -> Unit) {
        synchronized(this) {
            block(storeAccessor)
        }
    }

    interface StoreAccessor {

        /**
         *  Counts all *message* notifications.
         *
         *  Reaction notifications are *not counted*.
         */
        fun getTotalMessageNotificationCount(): Int

        fun getConversationsCount(): Int

        /**
         *  Counts all *message* notifications for the given [conversationId].
         *
         *  Reaction notifications are *not counted*.
         */
        fun getMessageNotificationCountForConversation(conversationId: ConversationId): Int

        /**
         * Returns `true` if any notification (type message or reaction) currently exists.
         */
        fun notificationsExist(): Boolean

        /**
         * Returns `true` if any *message* notification currently exists for the given [conversationId].
         */
        fun messageNotificationsForConversationExist(conversationId: ConversationId): Boolean

        /**
         * Returns `true` if any *message* notification currently exists for the given [messageUid].
         *
         * @see ConversationMessageNotification
         */
        fun messageNotificationForMessageExists(messageUid: MessageUid): Boolean

        /**
         * Returns `true` if any *reaction* notification currently exists for the given [messageUid].
         *
         * @see ConversationReactionNotification
         */
        fun reactionNotificationForMessageExists(messageUid: MessageUid): Boolean

        fun getAllNotifications(): List<ConversationNotification>

        /**
         * Returns all *message* notifications for the given [conversationId].
         *
         * @see getReactionNotificationsForConversation
         */
        fun getMessageNotificationsForConversation(conversationId: ConversationId): List<ConversationMessageNotification>

        /**
         * Returns all *reaction* notifications for the given [conversationId].
         *
         * @see getMessageNotificationsForConversation
         */
        fun getReactionNotificationsForConversation(conversationId: ConversationId): List<ConversationReactionNotification>

        fun addMessageNotification(messageNotification: ConversationMessageNotification)

        fun addReactionNotification(reactionNotification: ConversationReactionNotification)

        fun replaceMessageNotification(messageNotification: ConversationMessageNotification)

        fun replaceReactionNotification(reactionNotification: ConversationReactionNotification)

        /**
         * Removes the notification of type [ConversationMessageNotification] for the given [messageUid] if present.
         *
         * A notification of type [ConversationReactionNotification] for the same [messageUid] *will persist*.
         *
         * @see removeReactionNotification
         */
        fun removeMessageNotification(messageUid: MessageUid)

        /**
         * Removes the notification of type [ConversationReactionNotification] for the given [messageUid] if present.
         *
         * A notification of type [ConversationMessageNotification] for the same [messageUid] *will persist*.
         *
         * @see removeMessageNotification
         */
        fun removeReactionNotification(messageUid: MessageUid)

        /**
         *  Removes all message- and reaction-notifications for the given [conversationId].
         */
        fun removeNotificationsForConversation(conversationId: ConversationId)

        /**
         *  Removes all message-notifications for the given [conversationId].
         */
        fun removeMessageNotificationsForConversation(conversationId: ConversationId)

        fun removeAllNotifications()
    }
}
