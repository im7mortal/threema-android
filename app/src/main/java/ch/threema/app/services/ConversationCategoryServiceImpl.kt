package ch.threema.app.services

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.tasks.TaskCreator
import ch.threema.base.utils.getThreemaLogger
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.datatypes.ConversationId
import ch.threema.data.datatypes.ConversationIdObfuscated
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.IdentityString
import java.lang.ref.WeakReference

private val logger = getThreemaLogger("ConversationCategoryServiceImpl")

class ConversationCategoryServiceImpl(
    preferenceService: PreferenceService,
    preferenceStore: PreferenceStore,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskCreator: TaskCreator,
) : ConversationCategoryService {

    private val privateConversationsCache = PrivateConversationsCache(preferenceService, preferenceStore)

    @Synchronized
    override fun isMarkedAsPrivate(conversationId: ConversationId): Boolean =
        privateConversationsCache.isPrivateConversation(
            conversationIdObfuscated = conversationId.obfuscated,
        )

    @Synchronized
    override fun setPrivateMark(conversationId: ConversationId): Boolean {
        if (isMarkedAsPrivate(conversationId)) {
            // Nothing to do as the conversation is already marked as private
            return false
        }
        persistAddPrivateMark(conversationId)
        when (conversationId) {
            is ContactConversationId -> {
                reflectContactConversationPrivateMark(
                    identity = conversationId.identity,
                    isMarkedAsPrivate = true,
                )
            }
            is GroupConversationId -> {
                reflectGroupConversationPrivateMark(
                    groupDatabaseId = conversationId.groupDatabaseId,
                    isMarkedAsPrivate = true,
                )
            }
            is DistributionListConversationId -> {
                // TODO(ANDR-2718) or TODO(ANDR-3010): This change needs to be reflected when distribution lists are supported in MD.
            }
        }
        return true
    }

    @Synchronized
    override fun persistAddPrivateMark(conversationId: ConversationId) {
        val conversationIdObfuscated = conversationId.obfuscated
        if (!privateConversationsCache.isPrivateConversation(conversationIdObfuscated)) {
            privateConversationsCache.addPrivateConversation(conversationIdObfuscated)
        }
    }

    @Synchronized
    override fun removePrivateMark(conversationId: ConversationId): Boolean {
        if (!isMarkedAsPrivate(conversationId)) {
            // Nothing to do as the conversation isn't private
            return false
        }
        persistRemovePrivateMark(conversationId)
        when (conversationId) {
            is ContactConversationId -> {
                reflectContactConversationPrivateMark(
                    identity = conversationId.identity,
                    isMarkedAsPrivate = false,
                )
            }
            is GroupConversationId -> {
                reflectGroupConversationPrivateMark(
                    groupDatabaseId = conversationId.groupDatabaseId,
                    isMarkedAsPrivate = false,
                )
            }
            is DistributionListConversationId -> {
                // TODO(ANDR-2718) or TODO(ANDR-3010): This change needs to be reflected when distribution lists are supported in MD.
            }
        }
        return true
    }

    @Synchronized
    override fun persistRemovePrivateMark(conversationId: ConversationId) {
        val conversationIdObfuscated = conversationId.obfuscated
        if (privateConversationsCache.isPrivateConversation(conversationIdObfuscated)) {
            privateConversationsCache.removePrivateConversation(conversationIdObfuscated)
        }
    }

    @Synchronized
    override fun hasAnyPrivateMarks(): Boolean =
        privateConversationsCache.hasPrivateConversations()

    override fun invalidateCache() {
        privateConversationsCache.invalidate()
    }

    private fun reflectContactConversationPrivateMark(identity: IdentityString, isMarkedAsPrivate: Boolean) {
        if (multiDeviceManager.isMultiDeviceActive) {
            taskCreator.scheduleReflectContactConversationCategory(
                contactIdentity = identity,
                isPrivateChat = isMarkedAsPrivate,
            )
        }
    }

    private fun reflectGroupConversationPrivateMark(groupDatabaseId: GroupDatabaseId, isMarkedAsPrivate: Boolean) {
        if (multiDeviceManager.isMultiDeviceActive) {
            taskCreator.scheduleReflectGroupConversationCategory(
                groupDatabaseId = groupDatabaseId,
                isPrivateChat = isMarkedAsPrivate,
            )
        }
    }

    private class PrivateConversationsCache(
        private val preferenceService: PreferenceService,
        private val preferenceStore: PreferenceStore,
    ) {
        private var privateConversationsCache: WeakReference<MutableSet<ConversationIdObfuscated>> = WeakReference(null)

        @Synchronized
        fun isPrivateConversation(conversationIdObfuscated: ConversationIdObfuscated): Boolean =
            getPrivateConversationsCache().contains(conversationIdObfuscated)

        @Synchronized
        fun addPrivateConversation(conversationIdObfuscated: ConversationIdObfuscated) {
            val privateConversationsCacheSnapshot = getPrivateConversationsCache()
            privateConversationsCacheSnapshot.add(conversationIdObfuscated)
            privateConversationsCache = WeakReference(privateConversationsCacheSnapshot)
            preferenceService.setList(
                listName = PREF_LIST_NAME,
                elements = privateConversationsCacheSnapshot
                    .map(ConversationIdObfuscated::value)
                    .toTypedArray(),
            )
        }

        @Synchronized
        fun removePrivateConversation(conversationIdObfuscated: ConversationIdObfuscated) {
            val privateConversationsCacheSnapshot = getPrivateConversationsCache()
            privateConversationsCacheSnapshot.remove(conversationIdObfuscated)
            privateConversationsCache = WeakReference(privateConversationsCacheSnapshot)
            preferenceService.setList(
                listName = PREF_LIST_NAME,
                elements = privateConversationsCacheSnapshot
                    .map(ConversationIdObfuscated::value)
                    .toTypedArray(),
            )
        }

        @Synchronized
        fun hasPrivateConversations(): Boolean = getPrivateConversationsCache().isNotEmpty()

        @Synchronized
        fun invalidate() {
            privateConversationsCache = WeakReference(null)
        }

        private fun getPrivateConversationsCache(): MutableSet<ConversationIdObfuscated> =
            privateConversationsCache.get()
                ?: run {
                    val privateChatsUniqueIds = getFromPreferences()
                    privateConversationsCache = WeakReference(privateChatsUniqueIds)
                    privateChatsUniqueIds
                }

        @Synchronized
        private fun getFromPreferences(): MutableSet<ConversationIdObfuscated> {
            if (preferenceStore.containsKey(LEGACY_PREF_LIST_NAME)) {
                logger.info("Migrating private chats preference from '{}' to '{}'", LEGACY_PREF_LIST_NAME, PREF_LIST_NAME)
                // Previously, the conversation category (private conversations) were saved with a deadline list service that used a map for storing
                // the property. The map used the obfuscated conversation id as key and always had -1 as value, as it was never possible to mark a
                // conversation as private for a limited time.
                val privateConversationIdsObfuscated: MutableSet<String> = preferenceService
                    .getStringMap(LEGACY_PREF_LIST_NAME)
                    .keys
                    .toMutableSet()
                preferenceService.setList(
                    listName = PREF_LIST_NAME,
                    elements = privateConversationIdsObfuscated.toTypedArray(),
                )
                preferenceStore.remove(LEGACY_PREF_LIST_NAME)
                return privateConversationIdsObfuscated
                    .map(::ConversationIdObfuscated)
                    .toMutableSet()
            }
            return preferenceService
                .getList(PREF_LIST_NAME)
                .map(::ConversationIdObfuscated)
                .toMutableSet()
        }
    }

    companion object {
        // Do not change this list name as it is stored in preferences like this.
        private const val LEGACY_PREF_LIST_NAME = "list_hidden_chats"
        private const val PREF_LIST_NAME = "list_private_chats_unique_ids"
    }
}
