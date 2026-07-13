package ch.threema.app.errorreporting

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorRecordLevel {
    FATAL,
    ERROR,
}
