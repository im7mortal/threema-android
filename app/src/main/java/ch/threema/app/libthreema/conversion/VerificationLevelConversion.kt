package ch.threema.app.libthreema.conversion

import ch.threema.domain.models.VerificationLevel
import ch.threema.libthreema.VerificationLevel as LibthreemaVerificationLevel

fun VerificationLevel.toLibthreemaVerificationLevel() = when (this) {
    VerificationLevel.FULLY_VERIFIED -> LibthreemaVerificationLevel.FULLY_VERIFIED
    VerificationLevel.SERVER_VERIFIED -> LibthreemaVerificationLevel.SERVER_VERIFIED
    VerificationLevel.UNVERIFIED -> LibthreemaVerificationLevel.UNVERIFIED
}

fun LibthreemaVerificationLevel.toVerificationLevel() = when (this) {
    LibthreemaVerificationLevel.FULLY_VERIFIED -> VerificationLevel.FULLY_VERIFIED
    LibthreemaVerificationLevel.SERVER_VERIFIED -> VerificationLevel.SERVER_VERIFIED
    LibthreemaVerificationLevel.UNVERIFIED -> VerificationLevel.UNVERIFIED
}
