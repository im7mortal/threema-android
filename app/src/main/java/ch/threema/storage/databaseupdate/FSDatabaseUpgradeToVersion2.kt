package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * add negotiated version column
 */
internal class FSDatabaseUpgradeToVersion2(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        // Create negotiated version column with default value 0x0100 (Version 1.0)
        if (!sqLiteDatabase.fieldExists("session", "negotiatedVersion")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE session ADD COLUMN negotiatedVersion INTEGER DEFAULT 256")
        }
    }

    override val version = VERSION

    companion object {
        const val VERSION = 2
    }
}
