package ch.threema.app.libthreema.conversion

import ch.threema.libthreema.LocationMessageState as LibthreemaLocationMessageState
import ch.threema.storage.models.MessageModel

class LocationMessageBodyConverter {
    fun convertToLibthreemaLocationMessage(messageModel: MessageModel): LibthreemaLocationMessageState {
        val locationDataModel = messageModel.locationData

        return LibthreemaLocationMessageState(
            latitude = locationDataModel.latitude,
            longitude = locationDataModel.longitude,
            accuracyM = locationDataModel.accuracy,
            name = locationDataModel.poiNameOrNull ?: "",
            address = locationDataModel.poiAddressOrNull ?: "",
        )
    }
}
