package ch.threema.storage.databaseupdate

import ch.threema.storage.buildContentValues
import ch.threema.storage.runUpdate
import net.zetetic.database.sqlcipher.SQLiteDatabase

class DatabaseUpdateToVersion121(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        convertContactMessages(table = "message")
        convertContactMessages(table = "m_group_message")
        convertContactMessages(table = "distribution_list_message")
    }

    private fun convertContactMessages(table: String) {
        sqLiteDatabase.runUpdate(
            table = table,
            values = buildContentValues {
                put("type", MESSAGE_TYPE_TEXT)
            },
            whereClause = "type = ?",
            whereArgs = arrayOf(MESSAGE_TYPE_CONTACT.toString()),
        )
    }

    override val version = 121

    override fun getDescription() = "convert deprecated CONTACT messages to TEXT messages"

    companion object {
        private const val MESSAGE_TYPE_TEXT = 0
        private const val MESSAGE_TYPE_CONTACT = 5
    }
}
