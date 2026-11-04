package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * create rejected group message table
 */
internal class DatabaseUpdateToVersion87(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.execSQL(
            "CREATE TABLE `rejected_group_messages` (" +
                "`messageId` INTEGER NOT NULL, " +
                "`rejectedIdentity` VARCHAR NOT NULL," +
                "`groupId` INTEGER NOT NULL," +
                "PRIMARY KEY (`messageId`, `rejectedIdentity`, `groupId`) ON CONFLICT IGNORE " +
                ")",
        )
    }

    override val version = 87
}
