package ch.threema.app.libthreema.conversion

import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.libthreema.WorkAvailabilityStatus as LibthreemaWorkAvailabilityStatus

fun AvailabilityStatus.toLibthreemaWorkAvailabilityStatus(): LibthreemaWorkAvailabilityStatus = when (this) {
    is AvailabilityStatus.None -> LibthreemaWorkAvailabilityStatus.None
    is AvailabilityStatus.Unavailable -> LibthreemaWorkAvailabilityStatus.Unavailable(
        v1 = description,
    )
    is AvailabilityStatus.Busy -> LibthreemaWorkAvailabilityStatus.Busy(
        v1 = description,
    )
}

fun LibthreemaWorkAvailabilityStatus.toAvailabilityStatus(): AvailabilityStatus = when (this) {
    is LibthreemaWorkAvailabilityStatus.None -> AvailabilityStatus.None
    is LibthreemaWorkAvailabilityStatus.Unavailable -> AvailabilityStatus.Unavailable(
        description = v1,
    )
    is LibthreemaWorkAvailabilityStatus.Busy -> AvailabilityStatus.Busy(
        description = v1,
    )
}
