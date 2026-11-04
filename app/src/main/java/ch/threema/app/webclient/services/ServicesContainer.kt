package ch.threema.app.webclient.services

import android.content.Context
import androidx.annotation.AnyThread
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationService
import ch.threema.app.services.ConversationTagService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.FileService
import ch.threema.app.services.GroupFlowDispatcher
import ch.threema.app.services.GroupService
import ch.threema.app.services.LifetimeService
import ch.threema.app.services.MessageService
import ch.threema.app.services.SynchronizeContactsService
import ch.threema.app.services.UserService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.messageplayer.MessageFileDecryptionHelper
import ch.threema.app.services.notification.NotificationService
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.storage.factories.ContactModelFactory
import ch.threema.storage.factories.WebClientSessionModelFactory

/**
 * Contains all necessary services used by the web client.
 */
@AnyThread
class ServicesContainer(
    @JvmField val appContext: Context,
    @JvmField val lifetime: LifetimeService,
    @JvmField val contact: ContactService,
    @JvmField val group: GroupService,
    @JvmField val distributionList: DistributionListService,
    @JvmField val conversation: ConversationService,
    @JvmField val conversationTag: ConversationTagService,
    @JvmField val message: MessageService,
    @JvmField val notification: NotificationService,
    @JvmField val contactModelFactory: ContactModelFactory,
    @JvmField val webClientSessionModelFactory: WebClientSessionModelFactory,
    @JvmField val blockedIdentitiesService: BlockedIdentitiesService,
    @JvmField val preference: PreferenceService,
    @JvmField val user: UserService,
    @JvmField val file: FileService,
    @JvmField val messageFileDecryptionHelper: MessageFileDecryptionHelper,
    @JvmField val synchronizeContacts: SynchronizeContactsService,
    @JvmField val license: LicenseService<*>,
    @JvmField val sessionWakeUp: SessionWakeUpService,
    @JvmField val contactModelRepository: ContactModelRepository,
    @JvmField val groupModelRepository: GroupModelRepository,
    @JvmField val groupFlowDispatcher: GroupFlowDispatcher,
    @JvmField val appRestrictions: AppRestrictions,
) {
    @JvmField
    val wakeLock: WakeLockService = WakeLockServiceImpl(appContext, lifetime)

    @JvmField
    val batteryStatus: BatteryStatusService = BatteryStatusServiceImpl(appContext)
}
