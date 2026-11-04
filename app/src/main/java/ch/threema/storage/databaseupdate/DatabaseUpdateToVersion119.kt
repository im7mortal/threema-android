package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * add field workLastFullSyncAt to contact
 */
internal class DatabaseUpdateToVersion119(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.rawExecSQL(
            """
                ALTER TABLE `contacts`
                ADD COLUMN `workLastFullSyncAt` DATETIME DEFAULT NULL;
            """.trimIndent(),
        )
    }

    override val version = 119
}
