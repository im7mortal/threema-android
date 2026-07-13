package ch.threema.app.errorreporting

import ch.threema.common.sha256

@JvmInline
value class ErrorTypeId(private val errorId: String) {

    override fun toString() =
        errorId

    companion object {
        fun fromMessageFormat(messageFormat: String) =
            ErrorTypeId(sha256(messageFormat).toHexString(endIndex = 32))
    }
}
