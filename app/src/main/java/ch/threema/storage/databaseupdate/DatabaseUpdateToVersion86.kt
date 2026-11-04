package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * create task archive table
 */
internal class DatabaseUpdateToVersion86(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            "CREATE TABLE `tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`task` STRING NOT NULL)",
        )
    }

    override val version = 86
}
