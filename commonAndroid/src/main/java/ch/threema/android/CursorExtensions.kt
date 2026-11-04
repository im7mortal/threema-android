package ch.threema.android

import android.database.Cursor

/**
 * Iterates through the cursor starting at its next position.
 *
 * Note that the cursor is *closed* afterward.
 */
fun Cursor.iterate(operation: (Cursor) -> Unit) = use {
    while (moveToNext()) {
        operation(this)
    }
}

/**
 * Iterates through the cursor starting at its next position and returns a list of the results of [transform] for each of the cursor's entries.
 *
 * Note that the cursor is *closed* afterward.
 */
fun <T> Cursor.map(transform: (Cursor) -> T): List<T> = buildList {
    this@map.iterate { cursor ->
        add(transform(cursor))
    }
}

/**
 * Moves the cursor to the first position if there is one and returns the transformed result for the entry at that position, or null otherwise.
 *
 * Note that the cursor is *closed* afterward.
 */
fun <T> Cursor.firstOrNull(transform: (Cursor) -> T) = use {
    if (moveToFirst()) {
        transform(this)
    } else {
        null
    }
}

/**
 * Reads and returns the result of a `SELECT COUNT(*) ...` query.
 *
 * Note that the cursor is *closed* afterward.
 */
fun Cursor?.readCountQueryResult(): Long =
    this?.use {
        if (moveToFirst()) {
            getLong(0)
        } else {
            0L
        }
    }
        ?: 0L
