package ch.threema.data.datatypes

enum class ConversationCategory(
    val serializedValue: Int,
) {
    DEFAULT(0),
    PROTECTED(1),
    ;

    companion object {
        @JvmStatic
        fun deserialize(serializedValue: Int): ConversationCategory? =
            entries.find { it.serializedValue == serializedValue }
    }
}
