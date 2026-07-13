package ch.threema.storage.models

import androidx.annotation.IntDef

data class ServerMessageModel(
    @JvmField val message: String,
    @ServerMessageModelType val type: Int,
) {
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(TYPE_ALERT, TYPE_ERROR)
    annotation class ServerMessageModelType

    companion object {
        const val TABLE = "server_messages"
        const val COLUMN_MESSAGE = "message"
        const val COLUMN_TYPE = "type"
        const val TYPE_ALERT = 0
        const val TYPE_ERROR = 1
    }
}
