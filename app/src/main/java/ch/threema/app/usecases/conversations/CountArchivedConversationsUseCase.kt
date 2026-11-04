package ch.threema.app.usecases.conversations

import android.content.Context
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.DistributionListService
import ch.threema.app.usecases.groups.GetGroupDisplayNameUseCase
import ch.threema.app.utils.NameUtil
import ch.threema.app.utils.TextUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.storage.DatabaseProvider
import ch.threema.storage.models.ContactModel
import ch.threema.storage.models.DistributionListModel
import ch.threema.storage.models.group.GroupModelOld
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("CountArchivedConversationsUseCase")

class CountArchivedConversationsUseCase(
    private val databaseProvider: DatabaseProvider,
    private val preferenceService: PreferenceService,
    private val groupModelRepository: GroupModelRepository,
    private val getGroupDisplayNameUseCase: GetGroupDisplayNameUseCase,
    private val distributionListService: DistributionListService,
    private val appContext: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun call(filterQuery: String? = null, excludePrivate: Boolean): Long = withContext(dispatcherProvider.io) {
        if (filterQuery != null) {
            countMatchingQuery(filterQuery, excludePrivate)
        } else if (excludePrivate) {
            countExcludingPrivate()
        } else {
            count()
        }
    }

    /**
     * Counts all archived conversations, *including* conversations that are currently marked as private.
     *
     * **Query:**
     * ```
     * SELECT
     *  (
     *    SELECT COUNT(*)
     *    FROM contacts
     *    WHERE conversationVisibility = 1
     *      AND lastUpdateAt IS NOT NULL
     *      AND acquaintanceLevel != 1
     *  ) +
     *  (
     *    SELECT COUNT(*)
     *    FROM m_group
     *    WHERE conversationVisibility = 1
     *  ) +
     *  (
     *    SELECT COUNT(*)
     *    FROM distribution_list
     *    WHERE conversationVisibility = 1
     *      AND isHidden != 1
     *  )
     * ```
     *
     * @return The count, or 0 in case of any exception
     */
    private fun count(): Long {
        val query = "SELECT " +
            "(SELECT COUNT(*) FROM ${ContactModel.TABLE} " +
            "WHERE ${ContactModel.COLUMN_CONVERSATION_VISIBILITY} = ${ConversationVisibility.ARCHIVED.serializedValue} " +
            "AND ${ContactModel.COLUMN_LAST_UPDATE_AT} IS NOT NULL " +
            "AND ${ContactModel.COLUMN_ACQUAINTANCE_LEVEL} != 1) + " +
            "(SELECT COUNT(*) FROM ${GroupModelOld.TABLE} " +
            "WHERE ${GroupModelOld.COLUMN_CONVERSATION_VISIBILITY} = ${ConversationVisibility.ARCHIVED.serializedValue}) + " +
            "(SELECT COUNT(*) FROM ${DistributionListModel.TABLE} " +
            "WHERE ${DistributionListModel.COLUMN_CONVERSATION_VISIBILITY} = ${ConversationVisibility.ARCHIVED.serializedValue} " +
            "AND ${DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST} != 1)"
        val cursor = databaseProvider.readableDatabase.query(query)
        return runCatching {
            cursor.use {
                cursor.moveToFirst()
                cursor.getLong(0)
            }
        }.getOrElse {
            logger.error("Failed to count archived conversations")
            0L
        }
    }

    /**
     *  Same as [count], but without any conversations that are marked as private.
     */
    private fun countExcludingPrivate(): Long =
        countContactConversationsExcludingPrivate() + countGroupConversationsExcludingPrivate() + countDistributionListConversationsExcludingPrivate()

    /**
     * Count all archived contact conversations that are not marked as private.
     *
     * @return The count, 0 in case of any exception
     */
    private fun countContactConversationsExcludingPrivate(): Long {
        val query = "SELECT COUNT(*) FROM ${ContactModel.TABLE} " +
            "WHERE ${ContactModel.COLUMN_CONVERSATION_VISIBILITY} = ? " +
            "  AND ${ContactModel.COLUMN_LAST_UPDATE_AT} IS NOT NULL " +
            "  AND ${ContactModel.COLUMN_ACQUAINTANCE_LEVEL} != 1" +
            "  AND ${ContactModel.COLUMN_CONVERSATION_CATEGORY} != ?"
        val bindArgs = arrayOf(
            ConversationVisibility.ARCHIVED.serializedValue,
            ConversationCategory.PROTECTED.serializedValue,
        )
        val cursor = databaseProvider.readableDatabase.query(query, bindArgs)
        return runCatching {
            cursor.use {
                if (!cursor.moveToFirst()) {
                    logger.error("Could not move cursor while counting contact conversations excluding private")
                    return 0L
                }
                return cursor.getLong(0)
            }
        }.getOrElse {
            logger.error("Failed to count archived contact conversations excluding private")
            0L
        }
    }

    /**
     * Count all archived group conversations that are not marked as private.
     *
     * **Query:**
     * ```
     * SELECT COUNT(*)
     * FROM m_group
     * WHERE conversationVisibility = 1
     *   AND conversationCategory != 1
     * ```
     *
     * @return The count, 0 in case of any exception
     */
    private fun countGroupConversationsExcludingPrivate(): Long {
        val query = "SELECT COUNT(*) FROM ${GroupModelOld.TABLE} " +
            "WHERE ${GroupModelOld.COLUMN_CONVERSATION_VISIBILITY} = ?" +
            "  AND ${GroupModelOld.COLUMN_CONVERSATION_CATEGORY} != ?"
        val bindArgs = arrayOf(
            ConversationVisibility.ARCHIVED.serializedValue,
            ConversationCategory.PROTECTED.serializedValue,
        )
        val cursor = databaseProvider.readableDatabase.query(query, bindArgs)
        return runCatching {
            cursor.use {
                if (!cursor.moveToFirst()) {
                    logger.error("Could not move cursor while counting group conversations excluding private")
                    return 0L
                }
                return cursor.getLong(0)
            }
        }.getOrElse {
            logger.error("Failed to count archived group conversations excluding private")
            0L
        }
    }

    /**
     * Count all archived distribution list conversations that are not marked as private.
     *
     * **Query:**
     * ```
     * SELECT COUNT(*)
     * FROM distribution_list
     * WHERE conversationVisibility = 1
     *   AND isHidden != 1
     *   AND conversationCategory != 1
     * ```
     *
     * @return The count, 0 in case of any exception
     */
    private fun countDistributionListConversationsExcludingPrivate(): Long {
        val query = "SELECT COUNT(*) FROM ${DistributionListModel.TABLE} " +
            "WHERE ${DistributionListModel.COLUMN_CONVERSATION_VISIBILITY} = ? " +
            "  AND ${DistributionListModel.COLUMN_IS_ADHOC_DISTRIBUTION_LIST} != 1" +
            "  AND ${DistributionListModel.COLUMN_CONVERSATION_CATEGORY} != ?"
        val bindArgs = arrayOf(
            ConversationVisibility.ARCHIVED.serializedValue,
            ConversationCategory.PROTECTED.serializedValue,
        )
        val cursor = databaseProvider.readableDatabase.query(query, bindArgs)
        return runCatching {
            cursor.use {
                if (!cursor.moveToFirst()) {
                    logger.error("Could not move cursor while counting distribution list conversations excluding private")
                    return 0L
                }
                return cursor.getLong(0)
            }
        }.getOrElse {
            logger.error("Failed to count archived distribution list conversations excluding private")
            0L
        }
    }

    private fun countMatchingQuery(filterQuery: String, excludePrivate: Boolean): Long =
        countContactConversationsMatchingQuery(filterQuery, excludePrivate) +
            countGroupConversationsMatchingQuery(filterQuery, excludePrivate) +
            countDistributionListConversationsMatchingQuery(filterQuery, excludePrivate)

    /**
     * **Query**
     * ```
     * SELECT identity, firstName, lastName, publicNickName
     * FROM contacts
     * WHERE conversationVisibility = 1
     *   AND lastUpdateAt IS NOT NULL
     *   AND acquaintanceLevel != 1
     *   AND conversationCategory != 1
     * ```
     *
     * @param filterQuery Is matched against the display name of the contact conversation using [TextUtil.matchesQueryDiacriticInsensitive]
     */
    private fun countContactConversationsMatchingQuery(filterQuery: String, excludePrivate: Boolean): Long {
        val query =
            "SELECT ${ContactModel.COLUMN_IDENTITY}, " +
                "${ContactModel.COLUMN_FIRST_NAME}, " +
                "${ContactModel.COLUMN_LAST_NAME}, " +
                "${ContactModel.COLUMN_PUBLIC_NICK_NAME} " +
                "FROM ${ContactModel.TABLE} " +
                "WHERE ${ContactModel.COLUMN_CONVERSATION_VISIBILITY} = ?" +
                "  AND ${ContactModel.COLUMN_LAST_UPDATE_AT} IS NOT NULL" +
                "  AND ${ContactModel.COLUMN_ACQUAINTANCE_LEVEL} != 1" +
                if (excludePrivate) " AND ${ContactModel.COLUMN_CONVERSATION_CATEGORY} != ?" else ""
        val bindArgs = buildList {
            add(ConversationVisibility.ARCHIVED.serializedValue)
            if (excludePrivate) {
                add(ConversationCategory.PROTECTED.serializedValue)
            }
        }.toTypedArray()
        val cursor = databaseProvider.readableDatabase.query(query, bindArgs)
        val contactNameFormat = preferenceService.getContactNameFormat()
        return runCatching {
            cursor.use {
                var count = 0L
                while (cursor.moveToNext()) {
                    val identity = cursor.getString(cursor.getColumnIndexOrThrow(ContactModel.COLUMN_IDENTITY))
                    val firstName = cursor.getString(cursor.getColumnIndexOrThrow(ContactModel.COLUMN_FIRST_NAME))
                    val lastName = cursor.getString(cursor.getColumnIndexOrThrow(ContactModel.COLUMN_LAST_NAME))
                    val nickName = cursor.getString(cursor.getColumnIndexOrThrow(ContactModel.COLUMN_PUBLIC_NICK_NAME))
                    val contactDisplayName = NameUtil.getContactDisplayNameOrNickname(
                        identity,
                        firstName,
                        lastName,
                        nickName,
                        true,
                        contactNameFormat,
                    )
                    if (!TextUtil.matchesQueryDiacriticInsensitive(contactDisplayName, filterQuery)) {
                        continue
                    }
                    count++
                }
                count
            }
        }.getOrElse {
            logger.error("Failed to count archived contact conversations matching query")
            0L
        }
    }

    /**
     * @param filterQuery Is matched against the display name of the group conversation, using [TextUtil.matchesQueryDiacriticInsensitive]
     */
    private fun countGroupConversationsMatchingQuery(filterQuery: String, excludePrivate: Boolean): Int {
        val contactNameFormat = preferenceService.getContactNameFormat()
        return groupModelRepository
            .getAllArchived()
            .filter { groupModel ->
                val displayName = getGroupDisplayNameUseCase.call(groupModel, contactNameFormat).get(appContext)
                TextUtil.matchesQueryDiacriticInsensitive(displayName, filterQuery)
            }.count { groupModel ->
                if (excludePrivate) {
                    groupModel.data?.conversationCategory == ConversationCategory.DEFAULT
                } else {
                    true
                }
            }
    }

    /**
     * @param filterQuery Is matched against the display name of the distribution list conversation, using [TextUtil.matchesQueryDiacriticInsensitive]
     */
    private fun countDistributionListConversationsMatchingQuery(filterQuery: String, excludePrivate: Boolean): Int {
        val contactNameFormat = preferenceService.getContactNameFormat()
        return distributionListService
            .getAllArchived()
            .filter { distributionListModel ->
                val displayName = NameUtil.getDistributionListDisplayName(
                    distributionListModel,
                    distributionListService,
                    contactNameFormat,
                )
                TextUtil.matchesQueryDiacriticInsensitive(displayName, filterQuery)
            }
            .count { distributionListModel ->
                if (excludePrivate) {
                    distributionListModel.conversationCategory == ConversationCategory.DEFAULT
                } else {
                    true
                }
            }
    }
}
