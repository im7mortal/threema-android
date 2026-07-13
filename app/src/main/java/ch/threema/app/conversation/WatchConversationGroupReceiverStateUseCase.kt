package ch.threema.app.conversation

import ch.threema.app.services.GroupService
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.groups.GetGroupDisplayNameUseCase
import ch.threema.app.usecases.groups.WatchGroupAvatarIterationUseCase
import ch.threema.data.datatypes.GroupConversationId
import ch.threema.data.repositories.GroupModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class WatchConversationGroupReceiverStateUseCase(
    private val groupModelRepository: GroupModelRepository,
    private val groupService: GroupService,
    private val watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    private val getGroupDisplayNameUseCase: GetGroupDisplayNameUseCase,
    private val watchGroupAvatarIterationUseCase: WatchGroupAvatarIterationUseCase,
) {

    fun call(groupConversationId: GroupConversationId): Flow<ConversationReceiverState> {
        val groupModel = groupModelRepository
            .getByGroupDatabaseId(groupConversationId.groupDatabaseId)
            ?: return flowOf(ConversationReceiverState.Unknown)
        return combine(
            groupModel.dataFlow,
            watchContactNameFormatSettingUseCase.call(),
            watchGroupAvatarIterationUseCase.call(groupConversationId),
        ) { groupModelData, contactNameFormat, avatarIteration ->
            groupModelData?.let {
                ConversationReceiverState.Group(
                    displayName = getGroupDisplayNameUseCase.call(groupModel, contactNameFormat),
                    members = groupService.getMembersString(groupModel),
                    userIsMember = groupModelData.isMember,
                    avatarIteration = avatarIteration,
                )
            } ?: ConversationReceiverState.Unknown
        }
    }
}
