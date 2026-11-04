package ch.threema.app.libthreema.conversion

import ch.threema.domain.models.IdentityState
import ch.threema.libthreema.ActivityState as LibthreemaIdentityState

fun IdentityState.toLibthreemaIdentityState() = when (this) {
    IdentityState.ACTIVE -> LibthreemaIdentityState.ACTIVE
    IdentityState.INACTIVE -> LibthreemaIdentityState.INACTIVE
    IdentityState.INVALID -> LibthreemaIdentityState.INVALID
}

fun LibthreemaIdentityState.toIdentityState() = when (this) {
    LibthreemaIdentityState.ACTIVE -> IdentityState.ACTIVE
    LibthreemaIdentityState.INACTIVE -> IdentityState.INACTIVE
    LibthreemaIdentityState.INVALID -> IdentityState.INVALID
}
