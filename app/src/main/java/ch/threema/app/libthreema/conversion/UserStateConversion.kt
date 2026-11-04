package ch.threema.app.libthreema.conversion

import ch.threema.domain.models.UserState
import ch.threema.libthreema.UserState as LibthreemaUserState

fun LibthreemaUserState.toUserState(): UserState = when (this) {
    LibthreemaUserState.MEMBER -> UserState.MEMBER
    LibthreemaUserState.KICKED -> UserState.KICKED
    LibthreemaUserState.LEFT -> UserState.LEFT
}

fun UserState.toLibthreemaUserState(): LibthreemaUserState = when (this) {
    UserState.MEMBER -> LibthreemaUserState.MEMBER
    UserState.KICKED -> LibthreemaUserState.KICKED
    UserState.LEFT -> LibthreemaUserState.LEFT
}
