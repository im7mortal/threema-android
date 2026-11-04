package ch.threema.common

object DatabaseHelpers {
    @JvmStatic
    @JvmOverloads
    fun <T> convertToSelectionArgs(items: Collection<T>, convert: (T) -> String = { it.toString() }): Array<String> {
        val iterator = items.iterator()
        return Array(items.size) { convert(iterator.next()) }
    }

    /**
     * @return A concatenated string of placeholder in this form: `"?,?,?,?..."`
     */
    @JvmStatic
    fun makePlaceholders(length: Int): String {
        if (length < 1) {
            return ""
        }
        return buildString(length * 2 - 1) {
            append('?')
            repeat(length - 1) {
                append(',')
                append('?')
            }
        }
    }
}
