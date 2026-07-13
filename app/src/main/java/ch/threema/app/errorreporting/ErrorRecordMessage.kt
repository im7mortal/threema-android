package ch.threema.app.errorreporting

import java.util.Collections.emptyIterator
import kotlinx.serialization.Serializable

@Serializable
data class ErrorRecordMessage(
    val message: String,
    val parameters: List<String>?,
) {
    fun formatted(): String {
        val iterator = parameters?.iterator() ?: emptyIterator()
        return message.replace("%s".toRegex()) {
            if (iterator.hasNext()) {
                iterator.next()
            } else {
                "?"
            }
        }
    }
}
