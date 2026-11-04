package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Create table for storing server messages
 */
internal class DatabaseUpdateToVersion81(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            "CREATE TABLE `server_messages` (" +
                "`message` VARCHAR PRIMARY KEY ON CONFLICT REPLACE, " +
                "`type` INTEGER)",
        )
    }

    override val version = 81
}
