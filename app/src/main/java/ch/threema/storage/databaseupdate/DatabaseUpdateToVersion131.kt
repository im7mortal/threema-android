package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * This cleans up the inconsistency and redundancy of the `type` and `isStatusMessage` columns
 * in the message tables. Historically it was possible to create status messages of type TEXT
 * with the `isStatusMessage` flag set to 1. This database update migrates these messages
 * by changing their `type` to STATUS.
 */
class DatabaseUpdateToVersion131(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {

    override fun run() {
        migrateTable(tableName = "message")
        migrateTable(tableName = "m_group_message")
        migrateTable(tableName = "distribution_list_message")
    }

    private fun migrateTable(tableName: String) {
        sqLiteDatabase.execSQL(
            """
            UPDATE `$tableName`
            SET `type` = $MESSAGE_TYPE_STATUS
            WHERE `type` = $MESSAGE_TYPE_TEXT AND `isStatusMessage` = 1
            """.trimIndent(),
        )
    }

    override val version = 131

    companion object {
        private const val MESSAGE_TYPE_TEXT = 0
        private const val MESSAGE_TYPE_STATUS = 6
    }
}
