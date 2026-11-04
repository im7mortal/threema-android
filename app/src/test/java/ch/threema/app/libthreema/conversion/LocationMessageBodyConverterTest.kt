package ch.threema.app.libthreema.conversion

import ch.threema.domain.protocol.csp.messages.location.Poi
import ch.threema.libthreema.LocationMessageState as LibthreemaLocationMessageState
import ch.threema.storage.models.ContactMessageModel
import ch.threema.storage.models.DistributionListMessageModel
import ch.threema.storage.models.GroupMessageModel
import ch.threema.storage.models.data.LocationDataModel
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationMessageBodyConverterTest {

    @Test
    fun `location message with latitude and longitude`() {
        val locationDataModel = LocationDataModel(
            latitude = 0.1,
            longitude = 0.2,
            accuracy = null,
            poi = null,
        )
        val expectedLocationMessageState = LibthreemaLocationMessageState(
            latitude = 0.1,
            longitude = 0.2,
            accuracyM = null,
            name = "",
            address = "",
        )
        testLocationDataModelConversion(
            locationDataModel = locationDataModel,
            expectedLocationMessageState = expectedLocationMessageState,
        )
    }

    @Test
    fun `location message with latitude and longitude and accuracy`() {
        val locationDataModel = LocationDataModel(
            latitude = 0.1,
            longitude = 0.2,
            accuracy = 0.3,
            poi = null,
        )
        val expectedLocationMessageState = LibthreemaLocationMessageState(
            latitude = 0.1,
            longitude = 0.2,
            accuracyM = 0.3,
            name = "",
            address = "",
        )
        testLocationDataModelConversion(
            locationDataModel = locationDataModel,
            expectedLocationMessageState = expectedLocationMessageState,
        )
    }

    @Test
    fun `location message with poi named poi`() {
        val locationDataModel = LocationDataModel(
            latitude = 0.1,
            longitude = 0.2,
            accuracy = 0.3,
            poi = Poi.Named(name = "PoiName", address = "Street 1, City"),
        )
        val expectedLocationMessageState = LibthreemaLocationMessageState(
            latitude = 0.1,
            longitude = 0.2,
            accuracyM = 0.3,
            name = "PoiName",
            address = "Street 1, City",
        )
        testLocationDataModelConversion(
            locationDataModel = locationDataModel,
            expectedLocationMessageState = expectedLocationMessageState,
        )
    }

    @Test
    fun `location message with poi unnamed poi`() {
        val locationDataModel = LocationDataModel(
            latitude = 0.1,
            longitude = 0.2,
            accuracy = 0.3,
            poi = Poi.Unnamed(address = "Street 1, City"),
        )
        val expectedLocationMessageState = LibthreemaLocationMessageState(
            latitude = 0.1,
            longitude = 0.2,
            accuracyM = 0.3,
            name = "",
            address = "Street 1, City",
        )
        testLocationDataModelConversion(
            locationDataModel = locationDataModel,
            expectedLocationMessageState = expectedLocationMessageState,
        )
    }

    private fun testLocationDataModelConversion(
        locationDataModel: LocationDataModel,
        expectedLocationMessageState: LibthreemaLocationMessageState,
    ) {
        // Arrange
        val messageModels = listOf(
            ContactMessageModel(),
            GroupMessageModel(),
            DistributionListMessageModel(),
        )
        messageModels.forEach { messageModel ->
            messageModel.locationData = locationDataModel
        }
        val locationMessageBodyConverter = LocationMessageBodyConverter()

        // Act
        val locationMessageStates = messageModels
            .map(locationMessageBodyConverter::convertToLibthreemaLocationMessage)

        // Assert
        locationMessageStates.forEach { locationMessageState ->
            assertEquals(
                expected = expectedLocationMessageState,
                actual = locationMessageState,
            )
        }
    }
}
