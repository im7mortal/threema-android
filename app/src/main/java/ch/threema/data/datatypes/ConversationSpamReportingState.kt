package ch.threema.data.datatypes

import ch.threema.base.utils.getThreemaLogger

private val logger = getThreemaLogger("ConversationSpamReportingState")

/**
 * @param serializedValue Is used to serialize this type locally.
 */
enum class ConversationSpamReportingState(
    val serializedValue: Int,
) {
    /**
     * This state indicates that no user action towards spam reporting has been taken so far.
     */
    OPEN(
        serializedValue = 0,
    ),

    /**
     * The user marked a conversation as not-spam.
     */
    NOT_SPAM(
        serializedValue = 1,
    ),

    /**
     * The user reported the conversation as spam.
     */
    REPORTED(
        serializedValue = 2,
    ),
    ;

    companion object {

        @JvmField
        val DEFAULT = OPEN

        @JvmStatic
        fun fromSerializedValue(serializedValue: Int): ConversationSpamReportingState? =
            when (serializedValue) {
                0 -> OPEN
                1 -> NOT_SPAM
                2 -> REPORTED
                else -> {
                    logger.error("Unrecognized serialized value of {} for ConversationSpamReportingState", serializedValue)
                    null
                }
            }
    }
}
