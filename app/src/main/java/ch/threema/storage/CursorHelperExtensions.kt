package ch.threema.storage

import java.time.Instant

fun CursorHelper.getNonNullString(columnName: String): String =
    getString(columnName) ?: error("Column $columnName returned unexpected NULL value")

fun CursorHelper.getNonNullInstant(columnName: String): Instant =
    getInstant(columnName) ?: error("Column $columnName returned unexpected NULL value")

fun <T : Any> CursorHelper.deserializeEnum(columnName: String, deserialize: (String) -> T?): T {
    val value = getNonNullString(columnName)
    return deserialize(value) ?: error("Unsupported value '$value' found in column $columnName")
}
