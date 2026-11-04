package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Add the spam reporting state to groups.
 *
 * @see [ch.threema.data.datatypes.ConversationSpamReportingState]
 */
internal class DatabaseUpdateToVersion132(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {

    override fun run() {
        sqLiteDatabase.execSQL(
            "ALTER TABLE `m_group` ADD COLUMN `spamReportingState` INTEGER NOT NULL DEFAULT 0",
        )
    }

    override val version = 132
}
