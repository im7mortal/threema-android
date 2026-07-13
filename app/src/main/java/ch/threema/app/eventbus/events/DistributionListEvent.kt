package ch.threema.app.eventbus.events

import ch.threema.storage.models.DistributionListModel

sealed class DistributionListEvent {
    data class NewDistributionList(val distributionList: DistributionListModel) : DistributionListEvent()

    data class DistributionListUpdated(val distributionList: DistributionListModel) : DistributionListEvent()

    data class DistributionListRemoved(val distributionList: DistributionListModel) : DistributionListEvent()
}
