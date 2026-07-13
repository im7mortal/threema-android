package ch.threema.app.conversation

import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.DistributionListEvent
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.DistributionListService
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.utils.NameUtil
import ch.threema.data.datatypes.DistributionListConversationId
import ch.threema.storage.models.DistributionListModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

class WatchConversationDistributionListReceiverStateUseCase(
    private val globalEventFlows: GlobalEventFlows,
    private val watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    private val distributionListService: DistributionListService,
    private val preferenceService: PreferenceService,
) {

    fun call(distributionListConversationId: DistributionListConversationId): Flow<ConversationReceiverState> = flow {
        emit(getCurrentValue(distributionListConversationId))
        emitAll(getOngoingValues(distributionListConversationId))
    }

    private fun getCurrentValue(distributionListConversationId: DistributionListConversationId): ConversationReceiverState {
        val distributionListModel = distributionListService.getById(distributionListConversationId.distributionListId)
            ?: return ConversationReceiverState.Unknown
        val displayName = NameUtil.getDistributionListDisplayName(
            /* distributionListModel = */
            distributionListModel,
            /* distributionListService = */
            distributionListService,
            /* contactNameFormat = */
            preferenceService.getContactNameFormat(),
        )
        val members = distributionListService.getMembersString(distributionListModel)
        return ConversationReceiverState.DistributionList(
            displayName = displayName,
            members = members,
            isAdHoc = distributionListModel.isAdHocDistributionList,
        )
    }

    private fun getOngoingValues(distributionListConversationId: DistributionListConversationId): Flow<ConversationReceiverState> {
        return combine(
            watchDistributionListList(distributionListConversationId.distributionListId),
            watchContactNameFormatSettingUseCase.call(),
        ) { distributionListModel, contactNameFormat ->
            val displayName = NameUtil.getDistributionListDisplayName(
                /* distributionListModel = */
                distributionListModel,
                /* distributionListService = */
                distributionListService,
                /* contactNameFormat = */
                contactNameFormat,
            )
            val members = distributionListService.getMembersString(distributionListModel)
            ConversationReceiverState.DistributionList(
                displayName = displayName,
                members = members,
                isAdHoc = distributionListModel.isAdHocDistributionList,
            )
        }
    }

    private fun watchDistributionListList(distributionListId: Long): Flow<DistributionListModel> =
        globalEventFlows
            .distributionLists
            .mapNotNull { distributionListEvent ->
                when (distributionListEvent) {
                    is DistributionListEvent.DistributionListUpdated -> distributionListEvent.distributionList
                    is DistributionListEvent.NewDistributionList -> null
                    is DistributionListEvent.DistributionListRemoved -> null
                }
            }.filter { distributionListModel ->
                distributionListModel.id == distributionListId
            }
}
