package ch.threema.storage.databaseupdate

import ch.threema.base.utils.getThreemaLogger
import ch.threema.storage.runDelete
import net.zetetic.database.sqlcipher.SQLiteDatabase

private val logger = getThreemaLogger("DatabaseUpdateToVersion111")

class DatabaseUpdateToVersion111(
    private val sqLiteDatabase: SQLiteDatabase,
) : DatabaseUpdate {
    override fun run() {
        val orphanedPollIdQuery = "SELECT ib.id FROM identity_ballot AS ib WHERE NOT EXISTS (SELECT 1 FROM contacts WHERE identity = ib.identity)"

        logger.info("Deleting orphaned polls")
        val pollCount = sqLiteDatabase.runDelete(
            table = "ballot",
            whereClause = "id IN ($orphanedPollIdQuery)",
        )
        logger.info("Deleted {} orphaned polls", pollCount)

        logger.info("Deleting choices from orphaned polls")
        val pollChoiceCount = sqLiteDatabase.runDelete(
            table = "ballot_choice",
            whereClause = "ballotId IN ($orphanedPollIdQuery)",
        )
        logger.info("Deleted {} choices from orphaned polls", pollChoiceCount)

        logger.info("Deleting votes from orphaned polls")
        val pollVoteCount = sqLiteDatabase.runDelete(
            table = "ballot_vote",
            whereClause = "ballotId IN ($orphanedPollIdQuery)",
        )
        logger.info("Deleted {} votes from orphaned polls", pollVoteCount)

        logger.info("Deleting identity links to orphaned polls")
        val identityLinkCount = sqLiteDatabase.runDelete(
            table = "identity_ballot",
            whereClause = "identity NOT IN (SELECT identity FROM contacts)",
        )
        logger.info("Deleted {} identity links to orphaned polls", identityLinkCount)
    }

    override val version = 111

    override fun getDescription() = "remove polls from deleted contacts"
}
