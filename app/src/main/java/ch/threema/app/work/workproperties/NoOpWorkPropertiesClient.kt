package ch.threema.app.work.workproperties

import ch.threema.data.datatypes.AvailabilityStatus

@Suppress("unused")
class NoOpWorkPropertiesClient : WorkPropertiesClient {
    override suspend fun updateAvailabilityStatus(availabilityStatus: AvailabilityStatus): Result<Unit> =
        Result.success(Unit)
}
