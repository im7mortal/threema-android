package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Add column `readAt` to both emoji reaction tables
 */
internal class DatabaseUpdateToVersion133(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        addReadAtColumn("contact_reactions")
        addReadAtColumn("group_reactions")
    }

    private fun addReadAtColumn(tableName: String) {
        sqLiteDatabase.rawExecSQL(
            """
                ALTER TABLE `$tableName`
                ADD COLUMN `readAt` DATETIME DEFAULT NULL;
            """.trimIndent(),
        )
    }

    override val version = 133
}
