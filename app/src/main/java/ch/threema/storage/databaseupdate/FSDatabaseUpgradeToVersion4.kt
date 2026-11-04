package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * add timestamp of last sent fs message
 */
internal class FSDatabaseUpgradeToVersion4(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        if (!sqLiteDatabase.fieldExists("session", "lastOutgoingMessageTimestamp")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE session ADD COLUMN lastOutgoingMessageTimestamp INTEGER DEFAULT 0")
        }
    }

    override val version = VERSION

    companion object {
        const val VERSION = 4
    }
}
