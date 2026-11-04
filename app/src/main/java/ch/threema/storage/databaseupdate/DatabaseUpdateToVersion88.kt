package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * remove pending group message table
 */
internal class DatabaseUpdateToVersion88(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS `m_group_message_pending_msg_id`")
    }

    override val version = 88
}
