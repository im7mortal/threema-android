package ch.threema.app.di.modules

import ch.threema.app.connection.ServerConnectionManager
import ch.threema.app.di.DependencyContainer
import ch.threema.app.di.SessionScopeContainer
import ch.threema.app.di.SessionScopeContainerHolder
import ch.threema.app.di.isSessionScopeReady
import ch.threema.app.emojis.EmojiService
import ch.threema.app.managers.ServiceManager
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.profilepicture.GroupProfilePictureUploader
import ch.threema.app.services.ApiService
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.ConversationTagService
import ch.threema.app.services.DeviceService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.DownloadService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.LocaleService
import ch.threema.app.services.MessageService
import ch.threema.app.services.ProfilePictureRecipientsService
import ch.threema.app.services.SensorService
import ch.threema.app.services.ServerAddressProviderService
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.app.services.UserService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.messageplayer.MessagePlayerService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.services.poll.PollService
import ch.threema.app.tasks.TaskCreator
import ch.threema.app.threemasafe.ThreemaSafeService
import ch.threema.app.utils.OutgoingCspMessageServices
import ch.threema.app.voip.groupcall.GroupCallManager
import ch.threema.app.voip.groupcall.sfu.SfuConnection
import ch.threema.app.voip.services.VoipStateService
import ch.threema.app.webclient.manager.WebClientServiceManager
import ch.threema.app.webclient.services.SessionService
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.crypto.NonceStore
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.EditHistoryRepository
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.data.repositories.ModelRepositories
import ch.threema.domain.models.LicenseCredentials
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.connection.ServerConnection
import ch.threema.domain.protocol.csp.fs.ForwardSecurityMessageProcessor
import ch.threema.domain.stores.ContactStore
import ch.threema.domain.stores.DHSessionStore
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TaskManager
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

/**
 * Provides the common components which depend (directly or indirectly) on the master key being unlocked.
 *
 * To check whether a component from this module is available, use [isSessionScopeReady] or use Koin's `getOrNull`.
 * All components in this module should be annotated with [ch.threema.base.SessionScoped], if possible.
 */
val sessionScopedModule = module {
    factory<SessionService> { get<WebClientServiceManager>().sessionService }
    factory<OutgoingCspMessageServices>()

    factory<ServiceManager> { sessionScoped { serviceManager } }

    factory<APIConnector> { sessionScoped { apiConnector } }
    factory<ApiService> { sessionScoped { apiService } }
    factory<BlockedIdentitiesService> { sessionScoped { blockedIdentitiesService } }
    factory<ContactModelRepository> { sessionScoped { modelRepositories.contacts } }
    factory<ContactService> { sessionScoped { contactService } }
    factory<ContactStore> { sessionScoped { contactStore } }
    factory<ConversationService> { sessionScoped { conversationService } }
    factory<ConversationTagService> { sessionScoped { conversationTagService } }
    factory<DHSessionStore> { sessionScoped { dhSessionStore } }
    factory<DeviceService> { sessionScoped { deviceService } }
    factory<DistributionListService> { sessionScoped { distributionListService } }
    factory<DownloadService> { sessionScoped { downloadService } }
    factory<EditHistoryRepository> { sessionScoped { modelRepositories.editHistory } }
    factory<EmojiReactionsRepository> { sessionScoped { modelRepositories.emojiReaction } }
    factory<EmojiService> { sessionScoped { emojiService } }
    factory<ExcludedSyncIdentitiesService> { sessionScoped { excludedSyncIdentitiesService } }
    factory<ForwardSecurityMessageProcessor> { sessionScoped { forwardSecurityMessageProcessor } }
    factory<GroupCallManager> { sessionScoped { groupCallManager } }
    factory<GroupFlowDispatcher> { sessionScoped { groupFlowDispatcher } }
    factory<GroupModelRepository> { sessionScoped { modelRepositories.groups } }
    factory<GroupProfilePictureUploader> { sessionScoped { groupProfilePictureUploader } }
    factory<GroupService> { sessionScoped { groupService } }
    factory<IdentityStore> { sessionScoped { identityStore } }
    factory<LicenseService<out LicenseCredentials>> { sessionScoped { licenseService } }
    factory<LifetimeService> { sessionScoped { lifetimeService } }
    factory<LocaleService> { sessionScoped { localeService } }
    factory<MessagePlayerService> { sessionScoped { messagePlayerService } }
    factory<MessageService> { sessionScoped { messageService } }
    factory<ModelRepositories> { sessionScoped { modelRepositories } }
    factory<MultiDeviceManager> { sessionScoped { multiDeviceManager } }
    factory<NonceFactory> { sessionScoped { nonceFactory } }
    factory<NonceStore> { sessionScoped { nonceStore } }
    factory<NotificationService> { sessionScoped { notificationService } }
    factory<PollService> { sessionScoped { pollService } }
    factory<ProfilePictureRecipientsService> { sessionScoped { profilePicRecipientsService } }
    factory<SensorService> { sessionScoped { sensorService } }
    factory<ServerAddressProviderService> { sessionScoped { serverAddressProviderService } }
    factory<ServerConnection> { sessionScoped { connection } }
    factory<ServerConnectionManager> { sessionScoped { serverConnectionManager } }
    factory<SfuConnection> { sessionScoped { sfuConnection } }
    factory<SynchronizeContactsService> { sessionScoped { synchronizeContactsService } }
    factory<SynchronizedSettingsService> { sessionScoped { synchronizedSettingsService } }
    factory<TaskCreator> { sessionScoped { taskCreator } }
    factory<TaskManager> { sessionScoped { taskManager } }
    factory<ThreemaSafeService> { sessionScoped { threemaSafeService } }
    factory<UserService> { sessionScoped { userService } }
    factory<VoipStateService> { sessionScoped { voipStateService } }
    factory<WebClientServiceManager> { sessionScoped { webClientServiceManager } }

    factory<DependencyContainer>()
}

class SessionScopeUnavailableException : Exception()

private inline fun <reified T : Any> Scope.sessionScoped(noinline bind: SessionScopeContainer.() -> T): T =
    (get<SessionScopeContainerHolder>().sessionScopeContainer ?: throw SessionScopeUnavailableException()).bind()
