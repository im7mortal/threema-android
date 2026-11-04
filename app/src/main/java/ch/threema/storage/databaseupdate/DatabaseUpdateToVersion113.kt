package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * create table for persisting app tasks
 */
class DatabaseUpdateToVersion113(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            "CREATE TABLE `app_tasks` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "`appTaskData` TEXT NOT NULL)",
        )
    }

    override val version = 113
}
