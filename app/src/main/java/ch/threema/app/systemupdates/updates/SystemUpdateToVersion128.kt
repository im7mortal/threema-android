package ch.threema.app.systemupdates.updates

import ch.threema.app.services.DistributionListService
import ch.threema.app.stores.PreferenceStore
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationIdObfuscated
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion128")

/**
 * This system update moves the conversation categories from the preferences to the models. It only writes them if they are protected (private)
 * because the models are initialized with a default value.
 */
class SystemUpdateToVersion128 : SystemUpdate {
    private val preferenceStore: PreferenceStore by inject()
    private val contactModelRepository: ContactModelRepository by inject()
    private val groupModelRepository: GroupModelRepository by inject()
    private val distributionListService: DistributionListService by inject()

    override fun run() {
        val obfuscatedPrivateConversationIds = getObfuscatedPrivateConversationIdsFromPreferences()

        contactModelRepository
            .getAll()
            .forEach { contactModel ->
                val obfuscatedConversationId = ContactConversationId(contactModel.identity)
                    .obfuscated

                if (obfuscatedPrivateConversationIds.contains(obfuscatedConversationId)) {
                    // Note that it is ok to use from sync as the conversation category did not change as this is only a migration. Therefore, we
                    // don't need to reflect this.
                    contactModel.setConversationCategoryFromSync(ConversationCategory.PROTECTED)
                }
            }

        groupModelRepository
            .getAll()
            .forEach { groupModel ->
                val obfuscatedConversationId = GroupConversationId(groupModel.getDatabaseId())
                    .obfuscated

                if (obfuscatedPrivateConversationIds.contains(obfuscatedConversationId)) {
                    // Note that it is ok to use from sync as the conversation category did not change as this is only a migration. Therefore, we
                    // don't need to reflect this.
                    groupModel.setConversationCategoryFromSync(ConversationCategory.PROTECTED)
                }
            }

        distributionListService
            .getAll()
            .forEach { distributionListModel ->
                val obfuscatedConversationId = DistributionListConversationId(distributionListModel.id)
                    .obfuscated

                if (obfuscatedPrivateConversationIds.contains(obfuscatedConversationId)) {
                    distributionListService.setConversationCategory(distributionListModel, ConversationCategory.PROTECTED)
                }
            }

        removeConversationCategoriesFromPreferences()
    }

    private fun getObfuscatedPrivateConversationIdsFromPreferences(): Set<ConversationIdObfuscated> = when {
        preferenceStore.containsKey(LEGACY_PREF_LIST_NAME) -> {
            logger.info("Reading legacy list of private conversations")
            // Previously, the conversation category (private conversations) were saved with a deadline list service that used a map for storing
            // the property. The map used the obfuscated conversation id as key and always had -1 as value, as it was never possible to mark a
            // conversation as private for a limited time.
            preferenceStore
                .getMap(LEGACY_PREF_LIST_NAME)
                .keys
                .map(::ConversationIdObfuscated)
                .toSet()
        }

        preferenceStore.containsKey(PREF_LIST_NAME) -> {
            logger.info("Reading list of private conversations")
            preferenceStore
                .getStringArray(PREF_LIST_NAME)
                ?.map(::ConversationIdObfuscated)
                ?.toSet()
                ?: emptySet()
        }

        else -> emptySet()
    }

    private fun removeConversationCategoriesFromPreferences() {
        if (preferenceStore.containsKey(LEGACY_PREF_LIST_NAME)) {
            preferenceStore.remove(LEGACY_PREF_LIST_NAME)
        }
        if (preferenceStore.containsKey(PREF_LIST_NAME)) {
            preferenceStore.remove(PREF_LIST_NAME)
        }
    }

    override val version = 128

    companion object {
        // Do not change this list name as it is stored in preferences like this.
        private const val LEGACY_PREF_LIST_NAME = "list_hidden_chats"
        private const val PREF_LIST_NAME = "list_private_chats_unique_ids"
    }
}
