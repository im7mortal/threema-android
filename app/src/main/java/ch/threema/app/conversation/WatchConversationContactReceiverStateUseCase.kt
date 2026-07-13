package ch.threema.app.conversation

import ch.threema.app.usecases.contacts.WatchContactAvatarIterationUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.utils.ConfigUtils
import ch.threema.data.datatypes.ContactConversationId
import ch.threema.data.repositories.ContactModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class WatchConversationContactReceiverStateUseCase(
    private val contactModelRepository: ContactModelRepository,
    private val watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    private val watchContactAvatarIterationUseCase: WatchContactAvatarIterationUseCase,
) {

    fun call(contactConversationId: ContactConversationId): Flow<ConversationReceiverState> {
        val contactModel = contactModelRepository
            .getByIdentity(contactConversationId.identity)
            ?: return flowOf(ConversationReceiverState.Unknown)
        return combine(
            flow = contactModel.dataFlow,
            flow2 = watchContactNameFormatSettingUseCase.call(),
            flow3 = watchContactAvatarIterationUseCase.call(contactConversationId),
        ) { contactModelData, contactNameFormat, avatarIteration ->
            contactModelData?.let {
                ConversationReceiverState.Contact(
                    displayName = contactModelData.getDisplayName(contactNameFormat),
                    showIdentityTypeBadge = contactModelData.showIdentityTypeBadge(
                        isWorkBuild = ConfigUtils.isWorkBuild(),
                    ),
                    verificationLevel = contactModelData.verificationLevel,
                    workVerificationLevel = contactModelData.workVerificationLevel,
                    avatarIteration = avatarIteration,
                    availabilityStatus = contactModelData.availabilityStatus,
                )
            } ?: ConversationReceiverState.Unknown
        }
    }
}
