package ch.threema.storage.databaseupdate

import android.database.SQLException

/**
 * A [DatabaseUpdate] is used to migrate the database schema and/or data from a previous version.
 * All [DatabaseUpdate]s are processed sequentially on a worker thread in the order of their version number when the
 * database is opened.
 *
 * Important rules for [DatabaseUpdate]:
 * - must not depend on other services
 * - must not reference constants (e.g. table or field names) from other classes
 */
interface DatabaseUpdate {
    @Throws(SQLException::class)
    fun run()

    val version: Int
}
