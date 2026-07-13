package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 *  In older versions of the app, some timestamps were stored as formatted date-time strings without a timezone.
 *  In combination with the [DatabaseUpdateToVersion115], negative timestamp values were possible.
 *
 *  This migration corrects the affected database columns by settings any negative values to `0`.
 */
internal class DatabaseUpdateToVersion124(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        migrate(
            table = "m_group",
            column = "createdAt",
        )
        migrate(
            table = "m_group",
            column = "changedGroupDescTimestamp",
        )
        migrate(
            table = "distribution_list",
            column = "createdAt",
        )
        migrate(
            table = "m_group_request_sync_log",
            column = "lastRequestAt",
        )
    }

    fun migrate(table: String, column: String) {
        sqLiteDatabase.execSQL(
            "UPDATE $table SET $column = 0 WHERE $column < 0;",
        )
    }

    override fun getDescription() = "Corrects negative timestamp values"

    override val version = 124
}
