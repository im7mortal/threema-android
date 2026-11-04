package ch.threema.app.libthreema.conversion

import ch.threema.domain.protocol.csp.messages.location.Poi
import ch.threema.libthreema.LocationMessageState as LibthreemaLocationMessageState
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.LocationDataModel
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationMessageStateConverterTest {
    @Test
    fun `message with latitude and longitude`() {
        val locationMessageState = LibthreemaLocationMessageState(
            latitude = 0.1,
            longitude = 0.2,
            accuracyM = null,
            name = "",
            address = "",
        )
        val locationDataModel = LocationDataModel(
            latitude = 0.1,
            longitude = 0.2,
            accuracy = null,
            poi = null,
        )
        testLocationMessageConversion(
            locationMessageState = locationMessageState,
            expectedLocationDataModel = locationDataModel,
        )
    }

    @Test
    fun `message with latitude and longitude and accuracy`() {
        val locationMessageState = LibthreemaLocationMessageState(
            latitude = 0.1,
            longitude = 0.2,
            accuracyM = 0.3,
            name = "",
            address = "",
        )
        val locationDataModel = LocationDataModel(
            latitude = 0.1,
            longitude = 0.2,
            accuracy = 0.3,
            poi = null,
        )
        testLocationMessageConversion(
            locationMessageState = locationMessageState,
            expectedLocationDataModel = locationDataModel,
        )
    }

    @Test
    fun `message with name and address`() {
        val locationMessageState = LibthreemaLocationMessageState(
            latitude = 0.1,
            longitude = 0.2,
            accuracyM = 0.3,
            name = "name",
            address = "address",
        )
        val locationDataModel = LocationDataModel(
            latitude = 0.1,
            longitude = 0.2,
            accuracy = 0.3,
            poi = Poi.Named(
                name = "name",
                address = "address",
            ),
        )
        testLocationMessageConversion(
            locationMessageState = locationMessageState,
            expectedLocationDataModel = locationDataModel,
        )
    }

    @Test
    fun `message with name`() {
        val locationMessageState = LibthreemaLocationMessageState(
            latitude = 0.1,
            longitude = 0.2,
            accuracyM = 0.3,
            name = "name",
            address = "",
        )
        val locationDataModel = LocationDataModel(
            latitude = 0.1,
            longitude = 0.2,
            accuracy = 0.3,
            // Note: As the address is compulsory, we cannot restore a poi with only a name
            poi = null,
        )
        testLocationMessageConversion(
            locationMessageState = locationMessageState,
            expectedLocationDataModel = locationDataModel,
        )
    }

    @Test
    fun `message with address`() {
        val locationMessageState = LibthreemaLocationMessageState(
            latitude = 0.1,
            longitude = 0.2,
            accuracyM = 0.3,
            name = "",
            address = "address",
        )
        val locationDataModel = LocationDataModel(
            latitude = 0.1,
            longitude = 0.2,
            accuracy = 0.3,
            poi = Poi.Unnamed(
                address = "address",
            ),
        )
        testLocationMessageConversion(
            locationMessageState = locationMessageState,
            expectedLocationDataModel = locationDataModel,
        )
    }

    private fun testLocationMessageConversion(locationMessageState: LibthreemaLocationMessageState, expectedLocationDataModel: LocationDataModel) {
        // Arrange
        val locationMessageStateConverter = LocationMessageStateConverter()

        // Act
        val messageBodyAndRevisions = locationMessageStateConverter.convertToMessageBodyAndRevisions(locationMessageState)

        // Assert
        assertEquals(
            expected = MessageType.LOCATION,
            actual = messageBodyAndRevisions.messageType,
        )
        assertEquals(
            expected = expectedLocationDataModel.toString(),
            actual = messageBodyAndRevisions.originalBody,
        )
        assertEquals(
            expected = emptyList(),
            actual = messageBodyAndRevisions.messageEdits,
        )
    }
}
