package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * add conversation category to contact, group, and distribution list model
 */
internal class DatabaseUpdateToVersion129(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        addNewColumn(tableName = "contacts")
        addNewColumn(tableName = "m_group")
        addNewColumn(tableName = "distribution_list")
    }

    private fun addNewColumn(tableName: String) {
        sqLiteDatabase.execSQL(
            "ALTER TABLE `$tableName` ADD COLUMN `$COLUMN_CONVERSATION_CATEGORY` INTEGER DEFAULT $DEFAULT_CATEGORY_VALUE NOT NULL",
        )
    }

    override val version = 129

    companion object {
        private const val COLUMN_CONVERSATION_CATEGORY = "conversationCategory"
        private const val DEFAULT_CATEGORY_VALUE = 0
    }
}
