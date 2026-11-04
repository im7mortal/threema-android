package ch.threema.app.libthreema.conversion

import ch.threema.domain.models.IdentityType
import ch.threema.libthreema.IdentityType as LibthreemaIdentityType

fun IdentityType.toLibthreemaIdentityType() = when (this) {
    IdentityType.REGULAR -> LibthreemaIdentityType.REGULAR
    IdentityType.WORK -> LibthreemaIdentityType.WORK
}

fun LibthreemaIdentityType.toIdentityType() = when (this) {
    LibthreemaIdentityType.REGULAR -> IdentityType.REGULAR
    LibthreemaIdentityType.WORK -> IdentityType.WORK
}
