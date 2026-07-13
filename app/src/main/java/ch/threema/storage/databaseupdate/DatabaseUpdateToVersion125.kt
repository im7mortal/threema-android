package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 *  In older versions of the app, some timestamps were stored as formatted date-time strings without a timezone.
 *  In combination with the [DatabaseUpdateToVersion115], negative timestamp values were possible.
 *
 *  These negative `createdAt` timestamps were copied into the `lastUpdateAt` field.
 *
 *  This migration corrects the affected database columns by settings any negative values to `0`.
 */
internal class DatabaseUpdateToVersion125(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        migrate(
            table = "m_group",
            column = "lastUpdateAt",
        )
        migrate(
            table = "distribution_list",
            column = "lastUpdateAt",
        )
    }

    fun migrate(table: String, column: String) {
        sqLiteDatabase.execSQL(
            "UPDATE $table SET $column = 0 WHERE $column < 0;",
        )
    }

    override fun getDescription() = "Corrects negative lastUpdateAt timestamp values"

    override val version = 125
}
