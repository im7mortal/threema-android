package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * create table for contact availability status
 */
internal class DatabaseUpdateToVersion118(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        sqLiteDatabase.rawExecSQL(
            """
                CREATE TABLE IF NOT EXISTS `contact_availability_status` (
                    `identity` VARCHAR NOT NULL PRIMARY KEY,
                    `category` INTEGER NOT NULL,
                    `description` TEXT NOT NULL,
                    CONSTRAINT `fk_contacts_identity`
                        FOREIGN KEY(`identity`)
                        REFERENCES contacts (`identity`)
                        ON UPDATE CASCADE
                        ON DELETE CASCADE
                )
            """.trimMargin(),
        )
    }

    override val version = 118
}
