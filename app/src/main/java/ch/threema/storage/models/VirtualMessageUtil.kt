package ch.threema.storage.models

import java.time.Instant

object VirtualMessageUtil {

    private const val FIRST_UNREAD = "[[first-unread]]"
    private const val DATE_SEPARATOR = "[[date-separator]]"

    @JvmStatic
    fun isFirstUnreadMessage(message: MessageModel?): Boolean =
        message?.type == MessageType.VIRTUAL && message.body == FIRST_UNREAD

    @JvmStatic
    fun isDateSeparator(message: MessageModel?): Boolean =
        message?.type == MessageType.VIRTUAL && message.body == DATE_SEPARATOR

    @JvmStatic
    fun createFirstUnreadMessage(source: MessageModel?): MessageModel? =
        createVirtualMessage(source)?.apply {
            body = FIRST_UNREAD
            createdAt = source?.createdAt
        }

    @JvmOverloads
    @JvmStatic
    fun createDateSeparatorMessage(source: MessageModel?, date: Instant? = null): MessageModel? =
        createVirtualMessage(source)?.apply {
            body = DATE_SEPARATOR
            this.createdAt = date ?: source?.createdAt
        }

    private fun createVirtualMessage(source: MessageModel?): MessageModel? =
        when (source) {
            is ContactMessageModel -> ContactMessageModel()
            is GroupMessageModel -> GroupMessageModel()
            is DistributionListMessageModel,
            null,
            -> null
        }
            ?.apply {
                this.type = MessageType.VIRTUAL
            }
}
