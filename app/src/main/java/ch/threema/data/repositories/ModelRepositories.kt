package ch.threema.data.repositories

import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.base.crypto.NonceFactory
import ch.threema.common.DispatcherProvider
import ch.threema.data.IdentityProvider
import ch.threema.data.ModelCache
import ch.threema.data.storage.EditHistoryDaoImpl
import ch.threema.data.storage.EmojiReactionsDaoImpl
import ch.threema.data.storage.SqliteDatabaseBackend
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.storage.DatabaseProvider

class ModelRepositories(
    databaseProvider: DatabaseProvider,
    identityProvider: IdentityProvider,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskManager: TaskManager,
    private val nonceFactory: NonceFactory,
    globalEventBuses: GlobalEventBuses,
    globalEventFlows: GlobalEventFlows,
    dispatcherProvider: DispatcherProvider,
) {
    private val cache = ModelCache()
    private val databaseBackend = SqliteDatabaseBackend(
        databaseProvider = databaseProvider,
        identityProvider = identityProvider,
    )
    private val editHistoryDao = EditHistoryDaoImpl(databaseProvider)
    private val emojiReactionDao = EmojiReactionsDaoImpl(databaseProvider)

    val contacts = ContactModelRepository(
        cache = cache.contacts,
        databaseBackend = databaseBackend,
        identityProvider = identityProvider,
        multiDeviceManager = multiDeviceManager,
        taskManager = taskManager,
        nonceFactory = nonceFactory,
        globalEventBuses = globalEventBuses,
        globalEventFlows = globalEventFlows,
        dispatcherProvider = dispatcherProvider,
    )
    val groups = GroupModelRepository(
        cache = cache.groups,
        databaseBackend = databaseBackend,
        identityProvider = identityProvider,
        multiDeviceManager = multiDeviceManager,
        taskManager = taskManager,
        globalEventBuses = globalEventBuses,
        globalEventFlows = globalEventFlows,
        dispatcherProvider = dispatcherProvider,
    )
    val editHistory = EditHistoryRepository(
        cache = cache.editHistory,
        editHistoryDao = editHistoryDao,
        multiDeviceManager = multiDeviceManager,
        taskManager = taskManager,
    )
    val emojiReaction = EmojiReactionsRepository(
        cache = cache.emojiReaction,
        emojiReactionDao = emojiReactionDao,
        identityProvider = identityProvider,
        multiDeviceManager = multiDeviceManager,
        taskManager = taskManager,
    )
}
