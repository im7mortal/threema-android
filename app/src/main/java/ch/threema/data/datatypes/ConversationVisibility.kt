package ch.threema.data.datatypes

enum class ConversationVisibility(
    val serializedValue: Int,
) {
    NORMAL(serializedValue = 0),
    ARCHIVED(serializedValue = 1),
    PINNED(serializedValue = 2),
    ;

    companion object {
        @JvmStatic
        fun deserialize(serializedValue: Int): ConversationVisibility? =
            entries.find { it.serializedValue == serializedValue }
    }
}
