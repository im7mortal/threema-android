package ch.threema.app.webclient.converter

import androidx.annotation.AnyThread
import ch.threema.app.utils.QuoteUtil
import ch.threema.app.webclient.exceptions.ConversionException

object Quote : Converter() {
    private const val FIELD_IDENTITY = "identity"
    private const val FIELD_TEXT = "text"
    private const val FIELD_MESSAGE_ID = "messageId"
    private const val FIELD_MESSAGE = "message"

    @AnyThread
    @JvmStatic
    @Throws(ConversionException::class)
    fun convert(quoteContent: QuoteUtil.QuoteContent): MsgpackObjectBuilder =
        MsgpackObjectBuilder().apply {
            put(FIELD_IDENTITY, quoteContent.identity)
            put(FIELD_TEXT, quoteContent.quotedText)
            put(FIELD_MESSAGE_ID, quoteContent.quotedMessageId.toString())
            quoteContent.quotedMessageModel?.let { quotedMessageModel ->
                quoteContent.messageReceiver?.let { messageReceiver ->
                    put(
                        FIELD_MESSAGE,
                        Message.convert(
                            quotedMessageModel,
                            messageReceiver,
                            false,
                            Message.DETAILS_NO_QUOTE,
                        ),
                    )
                }
            }
        }
}
