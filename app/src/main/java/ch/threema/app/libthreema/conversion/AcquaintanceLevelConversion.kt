package ch.threema.app.libthreema.conversion

import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.libthreema.AcquaintanceLevel as LibthreemaAcquaintanceLevel

fun AcquaintanceLevel.toLibthreemaAcquaintanceLevel() = when (this) {
    AcquaintanceLevel.DIRECT -> LibthreemaAcquaintanceLevel.DIRECT
    AcquaintanceLevel.GROUP_OR_DELETED -> LibthreemaAcquaintanceLevel.GROUP_OR_DELETED
}

fun LibthreemaAcquaintanceLevel.toAcquaintanceLevel() = when (this) {
    LibthreemaAcquaintanceLevel.DIRECT -> AcquaintanceLevel.DIRECT
    LibthreemaAcquaintanceLevel.GROUP_OR_DELETED -> AcquaintanceLevel.GROUP_OR_DELETED
}
