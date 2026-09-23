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
