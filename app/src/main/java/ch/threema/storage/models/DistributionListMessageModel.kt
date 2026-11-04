package ch.threema.storage.models

class DistributionListMessageModel : MessageModel() {
    var distributionListId: Long = 0

    override fun toString() = "DistributionListMessageModel(id=$id)"

    companion object {
        const val TABLE = "distribution_list_message"
        const val COLUMN_DISTRIBUTION_LIST_ID = "distributionListId"
    }
}
