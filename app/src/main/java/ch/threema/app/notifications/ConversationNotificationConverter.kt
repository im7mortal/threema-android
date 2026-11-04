package ch.threema.app.notifications

import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.services.GroupService
import ch.threema.app.services.notification.ConversationMessageNotification
import ch.threema.app.services.notification.ConversationReactionNotification
import ch.threema.app.usecases.groups.GetGroupDisplayNameUseCase
import ch.threema.app.utils.ConversationNotificationUtil
import ch.threema.data.IdentityProvider
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.data.repositories.EmojiReactionsRepository
import ch.threema.data.repositories.GroupModelRepository
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.MessageModel
import org.koin.core.component.KoinComponent

// TODO(ANDR-4691): This class needs refactoring
class ConversationNotificationConverter(
    private val preferenceService: PreferenceService,
    private val identityProvider: IdentityProvider,
) : KoinComponent {
    private val contactModelRepository: ContactModelRepository? by injectNullableNonBinding()
    private val groupModelRepository: GroupModelRepository? by injectNullableNonBinding()
    private val contactService: ContactService? by injectNullableNonBinding()
    private val groupService: GroupService? by injectNullableNonBinding()
    private val getGroupDisplayNameUseCase: GetGroupDisplayNameUseCase? by injectNullableNonBinding()
    private val emojiReactionsRepository: EmojiReactionsRepository? by injectNullableNonBinding()

    fun convertMessageNotification(message: MessageModel): ConversationMessageNotification? =
        ConversationNotificationUtil.convertMessageNotification(
            /* messageModel = */
            message.takeIf { it.messageId != null } ?: return null,
            /* contactModelRepository = */
            contactModelRepository ?: return null,
            /* groupModelRepository = */
            groupModelRepository ?: return null,
            /* contactService = */
            contactService ?: return null,
            /* groupService = */
            groupService ?: return null,
            /* contactNameFormat = */
            preferenceService.getContactNameFormat(),
            /* getGroupDisplayNameUseCase = */
            getGroupDisplayNameUseCase ?: return null,
        )

    fun convertReactionNotification(targetMessage: MessageModel): ConversationReactionNotification? =
        when (targetMessage) {
            is ContactMessageModel -> ConversationNotificationUtil.createContactReactionNotification(
                /* targetMessage = */
                targetMessage,
                /* emojiReactionsRepository = */
                emojiReactionsRepository ?: return null,
                /* contactModelRepository = */
                contactModelRepository ?: return null,
                /* contactService = */
                contactService ?: return null,
                /* contactNameFormat = */
                preferenceService.getContactNameFormat(),
                /* identityProvider = */
                identityProvider,
            )
            is GroupMessageModel -> ConversationNotificationUtil.createGroupReactionNotification(
                /* targetMessage = */
                targetMessage,
                /* emojiReactionsRepository = */
                emojiReactionsRepository ?: return null,
                /* groupModelRepository = */
                groupModelRepository ?: return null,
                /* getGroupDisplayNameUseCase = */
                getGroupDisplayNameUseCase ?: return null,
                /* contactNameFormat = */
                preferenceService.getContactNameFormat(),
                /* groupService = */
                groupService ?: return null,
                /* identityProvider = */
                identityProvider,
            )
            is DistributionListMessageModel -> null
        }
}
