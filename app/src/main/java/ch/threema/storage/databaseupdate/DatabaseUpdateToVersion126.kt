package ch.threema.storage.databaseupdate

import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * The conversation visibility is either 'normal', 'archived', or 'pinned'. This applies to all conversations, i.e., contact conversations (1:1),
 * groups, and distribution lists.
 *
 * We used to store whether a conversation was archived inside the models' tables as 'isArchived' boolean. In the table 'conversation_tag' we used to
 * store the information whether a conversation was 'pinned' or not.
 *
 * With this database migration we combine both values in a single column that holds the conversation visibility.
 */
internal class DatabaseUpdateToVersion126(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        migrateContacts()
        migrateGroups()
        migrateDistributionLists()
        sqLiteDatabase.execSQL("DELETE FROM $CONVERSATION_TAG_TABLE_NAME WHERE $CONVERSATION_TAG_COLUMN_TAG = 'star'")
    }

    private fun migrateContacts() {
        // Initially, the new conversation visibility column is set to normal visibility
        addNewColumn(CONTACT_TABLE_NAME)

        // We now update the visibility to pinned, in case the conversation pin tag is set in the conversation tag table
        sqLiteDatabase.execSQL(
            """
            UPDATE `$CONTACT_TABLE_NAME` SET $COLUMN_CONVERSATION_VISIBILITY = $PINNED_VISIBILITY_VALUE
            WHERE EXISTS (
              SELECT 1
              FROM `$CONVERSATION_TAG_TABLE_NAME`
              WHERE $CONVERSATION_TAG_TABLE_NAME.$CONVERSATION_TAG_COLUMN_TAG = 'star'
                AND $CONVERSATION_TAG_TABLE_NAME.$CONVERSATION_TAG_COLUMN_CONVERSATION_UID = 'i-' || $CONTACT_TABLE_NAME.identity
            );
            """.trimIndent(),
        )

        // In case the old column indicates that the conversation is archived, we set the new column to archived as well
        updateConversationVisibilityIfArchived(CONTACT_TABLE_NAME)

        // We can finally remove the old column as it has been migrated to the new column
        removeOldColumn(CONTACT_TABLE_NAME)
    }

    private fun migrateGroups() {
        // Initially, the new conversation visibility column is set to normal visibility
        addNewColumn(GROUP_TABLE_NAME)

        // We now update the visibility to pinned, in case the conversation pin tag is set in the conversation tag table
        sqLiteDatabase.execSQL(
            """
            UPDATE `$GROUP_TABLE_NAME` SET $COLUMN_CONVERSATION_VISIBILITY = $PINNED_VISIBILITY_VALUE
            WHERE EXISTS (
              SELECT 1
              FROM `$CONVERSATION_TAG_TABLE_NAME`
              WHERE $CONVERSATION_TAG_TABLE_NAME.$CONVERSATION_TAG_COLUMN_TAG = 'star'
                AND $CONVERSATION_TAG_TABLE_NAME.$CONVERSATION_TAG_COLUMN_CONVERSATION_UID = 'g-' || $GROUP_TABLE_NAME.id
            );
            """.trimIndent(),
        )

        // In case the old column indicates that the conversation is archived, we set the new column to archived as well
        updateConversationVisibilityIfArchived(GROUP_TABLE_NAME)

        // We can finally remove the old column as it has been migrated to the new column
        removeOldColumn(GROUP_TABLE_NAME)
    }

    private fun migrateDistributionLists() {
        // Initially, the new conversation visibility column is set to normal visibility
        addNewColumn(DISTRIBUTION_LIST_TABLE_NAME)

        // We now update the visibility to pinned, in case the conversation pin tag is set in the conversation tag table
        sqLiteDatabase.execSQL(
            """
            UPDATE `$DISTRIBUTION_LIST_TABLE_NAME` SET $COLUMN_CONVERSATION_VISIBILITY = $PINNED_VISIBILITY_VALUE
            WHERE EXISTS (
              SELECT 1
              FROM `$CONVERSATION_TAG_TABLE_NAME`
              WHERE $CONVERSATION_TAG_TABLE_NAME.$CONVERSATION_TAG_COLUMN_TAG = 'star'
                AND $CONVERSATION_TAG_TABLE_NAME.$CONVERSATION_TAG_COLUMN_CONVERSATION_UID = 'd-' || $DISTRIBUTION_LIST_TABLE_NAME.id
            );
            """.trimIndent(),
        )

        // In case the old column indicates that the conversation is archived, we set the new column to archived as well
        updateConversationVisibilityIfArchived(DISTRIBUTION_LIST_TABLE_NAME)

        // We can finally remove the old column as it has been migrated to the new column
        removeOldColumn(DISTRIBUTION_LIST_TABLE_NAME)
    }

    private fun addNewColumn(tableName: String) {
        sqLiteDatabase.execSQL(
            "ALTER TABLE `$tableName` ADD COLUMN `$COLUMN_CONVERSATION_VISIBILITY` INTEGER NOT NULL DEFAULT $NORMAL_VISIBILITY_VALUE",
        )
    }

    private fun updateConversationVisibilityIfArchived(tableName: String) {
        sqLiteDatabase.execSQL(
            "UPDATE `$tableName` SET `$COLUMN_CONVERSATION_VISIBILITY` = $ARCHIVED_VISIBILITY_VALUE WHERE `$COLUMN_IS_ARCHIVED` != 0",
        )
    }

    private fun removeOldColumn(tableName: String) {
        sqLiteDatabase.execSQL("ALTER TABLE `$tableName` DROP COLUMN `$COLUMN_IS_ARCHIVED`")
    }

    override fun getDescription() = "Move conversation visibility into models"

    override val version = 126

    companion object {
        private const val CONTACT_TABLE_NAME = "contacts"
        private const val GROUP_TABLE_NAME = "m_group"
        private const val DISTRIBUTION_LIST_TABLE_NAME = "distribution_list"
        private const val CONVERSATION_TAG_TABLE_NAME = "conversation_tag"
        private const val CONVERSATION_TAG_COLUMN_TAG = "tag"
        private const val CONVERSATION_TAG_COLUMN_CONVERSATION_UID = "conversationUid"

        private const val COLUMN_IS_ARCHIVED = "isArchived"
        private const val COLUMN_CONVERSATION_VISIBILITY = "conversationVisibility"

        private const val NORMAL_VISIBILITY_VALUE = 0
        private const val ARCHIVED_VISIBILITY_VALUE = 1
        private const val PINNED_VISIBILITY_VALUE = 2
    }
}
