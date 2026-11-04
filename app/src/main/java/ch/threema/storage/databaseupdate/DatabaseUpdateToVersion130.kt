package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

internal class DatabaseUpdateToVersion130(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {

    override fun run() {
        renameColumn("contact_edit_history_entries")
        renameColumn("group_edit_history_entries")
    }

    private fun renameColumn(tableName: String) {
        sqLiteDatabase.rawExecSQL("ALTER TABLE `$tableName` RENAME COLUMN `editedAt` TO `createdAt`")
    }

    override val version = 130
}
