package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * The notification trigger policy used to be stored as one single long value with the following meaning:
 *
 * - -1: muted forever
 * - -2: muted forever (unless mentioned; only possible for groups)
 * - > 0: muted until the value (as epoch milli timestamp)
 * - other: not muted
 */
class DatabaseUpdateToVersion127(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        migrateContactTable()
        migrateGroupTable()
    }

    private fun migrateContactTable() {
        // Add the two new columns
        sqLiteDatabase.execSQL(
            "ALTER TABLE `$CONTACT_TABLE_NAME` ADD COLUMN `$CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_POLICY` INTEGER DEFAULT NULL",
        )
        sqLiteDatabase.execSQL(
            "ALTER TABLE `$CONTACT_TABLE_NAME` ADD COLUMN `$CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_EXPIRES_AT` BIGINT DEFAULT NULL",
        )

        // Set POLICY = 0 where old value was -1 or > 0
        sqLiteDatabase.execSQL(
            """
            UPDATE `$CONTACT_TABLE_NAME`
              SET `$CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_POLICY` = $CONTACT_POLICY_NEVER
                WHERE `$CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE` = -1
                OR `$CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE` > 0
            """.trimIndent(),
        )

        // Set EXPIRES_AT for positive values
        sqLiteDatabase.execSQL(
            """
            UPDATE `${CONTACT_TABLE_NAME}`
              SET `$CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_EXPIRES_AT` = `$CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE`
                WHERE `$CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE` > 0
            """.trimIndent(),
        )

        // Drop the old column
        sqLiteDatabase.execSQL(
            "ALTER TABLE `${CONTACT_TABLE_NAME}` DROP COLUMN `$CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE`",
        )
    }

    private fun migrateGroupTable() {
        // Add the two new columns
        sqLiteDatabase.execSQL(
            "ALTER TABLE `$GROUP_TABLE_NAME` ADD COLUMN `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_POLICY` INTEGER DEFAULT NULL",
        )
        sqLiteDatabase.execSQL(
            "ALTER TABLE `$GROUP_TABLE_NAME` ADD COLUMN `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_EXPIRES_AT` BIGINT DEFAULT NULL",
        )

        // Set POLICY = 1 where old value was -1 or > 0
        sqLiteDatabase.execSQL(
            """
            UPDATE `$GROUP_TABLE_NAME`
              SET `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_POLICY` = $GROUP_POLICY_NEVER
                WHERE `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE` = -1
                OR `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE` > 0
            """.trimIndent(),
        )

        // Set POLICY = 0 where old value was -2
        sqLiteDatabase.execSQL(
            """
            UPDATE `$GROUP_TABLE_NAME`
              SET `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_POLICY` = $GROUP_POLICY_MENTIONED
                WHERE `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE` = -2
            """.trimIndent(),
        )

        // Set EXPIRES_AT for positive values
        sqLiteDatabase.execSQL(
            """
            UPDATE `$GROUP_TABLE_NAME`
              SET `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_EXPIRES_AT` = `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE`
              WHERE `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE` > 0
            """.trimIndent(),
        )

        // Drop the old column
        sqLiteDatabase.execSQL(
            "ALTER TABLE `$GROUP_TABLE_NAME` DROP COLUMN `$GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE`",
        )
    }

    override fun getDescription() = "Add full support for the notification trigger policy"

    override val version = 127

    companion object {
        private const val CONTACT_TABLE_NAME = "contacts"
        private const val CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE = "notificationTriggerPolicyOverride"
        private const val CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_POLICY = "notificationTriggerPolicyOverridePolicy"
        private const val CONTACT_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_EXPIRES_AT = "notificationTriggerPolicyOverrideExpiresAt"
        private const val CONTACT_POLICY_NEVER = 0

        private const val GROUP_TABLE_NAME = "m_group"
        private const val GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE = "notificationTriggerPolicyOverride"
        private const val GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_POLICY = "notificationTriggerPolicyOverridePolicy"
        private const val GROUP_NOTIFICATION_TRIGGER_POLICY_OVERRIDE_EXPIRES_AT = "notificationTriggerPolicyOverrideExpiresAt"
        private const val GROUP_POLICY_MENTIONED = 0
        private const val GROUP_POLICY_NEVER = 1
    }
}
