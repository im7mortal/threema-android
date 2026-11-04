package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * add yet another version column
 */
internal class FSDatabaseUpgradeToVersion3(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        // Create remote 4DH version column with default value 0x0100 (Version 1.0)
        if (!sqLiteDatabase.fieldExists("session", "peerCurrentVersion_4dh")) {
            sqLiteDatabase.rawExecSQL("ALTER TABLE session ADD COLUMN peerCurrentVersion_4dh INTEGER DEFAULT 256")
        }
    }

    override val version = VERSION

    companion object {
        const val VERSION = 3
    }
}
