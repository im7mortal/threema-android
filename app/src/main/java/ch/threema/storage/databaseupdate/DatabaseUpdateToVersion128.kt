package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * The logic of this [DatabaseUpdate] was moved from [DatabaseUpdateToVersion123] to re-apply it.
 */
internal class DatabaseUpdateToVersion128(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        migrateTable(tableName = "message")
        migrateTable(tableName = "m_group_message")
        migrateTable(tableName = "distribution_list_message")
    }

    private fun migrateTable(tableName: String) {
        /*
         * The following message types are required to have a message id:
         *
         * - 0: TEXT
         * - 4: LOCATION
         * - 7: POLL
         * - 8: FILE
         */
        sqLiteDatabase.execSQL(
            """
            UPDATE $tableName
            SET apiMessageId = lower(hex(randomblob(8)))
            WHERE type IN (0, 4, 7, 8)
            AND apiMessageId IS NULL;
            """.trimIndent(),
        )

        sqLiteDatabase.execSQL(
            """
            UPDATE $tableName
            SET apiMessageId = NULL
            WHERE apiMessageId = "";
            """.trimIndent(),
        )
    }

    override fun getDescription() = "assign a random message id for messages that require a message id but are missing one"

    override val version = 128
}
