package ch.threema.app.libthreema.conversion

import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.libthreema.WorkVerificationLevel as LibthreemaWorkVerificationLevel

fun WorkVerificationLevel.toLibthreemaWorkVerificationLevel() = when (this) {
    WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> LibthreemaWorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
    WorkVerificationLevel.NONE -> LibthreemaWorkVerificationLevel.NONE
}

fun LibthreemaWorkVerificationLevel.toWorkVerificationLevel() = when (this) {
    LibthreemaWorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED -> WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED
    LibthreemaWorkVerificationLevel.NONE -> WorkVerificationLevel.NONE
}
