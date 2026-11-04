package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Add table for Group Calls
 */
internal class DatabaseUpdateToVersion77(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            "CREATE TABLE `group_call` (" +
                "`callId` TEXT PRIMARY KEY NOT NULL, " +
                "`groupId` INTEGER NOT NULL, " +
                "`sfuBaseUrl` TEXT NOT NULL, " +
                "`gck` TEXT NOT NULL)",
        )
    }

    override val version = 77
}
