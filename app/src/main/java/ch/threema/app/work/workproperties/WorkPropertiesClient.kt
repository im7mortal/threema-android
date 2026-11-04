package ch.threema.app.work.workproperties

import ch.threema.data.datatypes.AvailabilityStatus

interface WorkPropertiesClient {
    suspend fun updateAvailabilityStatus(availabilityStatus: AvailabilityStatus): Result<Unit>
}
