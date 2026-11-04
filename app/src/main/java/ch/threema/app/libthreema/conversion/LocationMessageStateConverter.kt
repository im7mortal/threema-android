package ch.threema.app.libthreema.conversion

import ch.threema.common.takeUnlessEmpty
import ch.threema.domain.protocol.csp.messages.location.Poi
import ch.threema.libthreema.LocationMessageState as LibthreemaLocationMessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.LocationDataModel

class LocationMessageStateConverter {
    fun convertToMessageBodyAndRevisions(locationMessageState: LibthreemaLocationMessageState): MessageBodyAndRevisions {
        val poi = locationMessageState.address.takeUnlessEmpty()?.let { addressNotNull ->
            val poiName = locationMessageState.name.takeUnlessEmpty()
            if (poiName != null) {
                Poi.Named(
                    name = poiName,
                    address = addressNotNull,
                )
            } else {
                Poi.Unnamed(
                    address = addressNotNull,
                )
            }
        }
        val locationDataModel = LocationDataModel(
            latitude = locationMessageState.latitude,
            longitude = locationMessageState.longitude,
            accuracy = locationMessageState.accuracyM,
            poi = poi,
        )
        return MessageBodyAndRevisions(
            messageType = MessageType.LOCATION,
            originalBody = locationDataModel.toString(),
        )
    }
}
