package ch.threema.app.libthreema.conversion

import ch.threema.domain.models.ContactSyncState
import ch.threema.libthreema.SyncState as LibthreemaContactSyncState

fun ContactSyncState.toLibthreemaContactSyncState() = when (this) {
    ContactSyncState.INITIAL -> LibthreemaContactSyncState.INITIAL
    ContactSyncState.IMPORTED -> LibthreemaContactSyncState.IMPORTED
    ContactSyncState.CUSTOM -> LibthreemaContactSyncState.CUSTOM
}

fun LibthreemaContactSyncState.toContactSyncState() = when (this) {
    LibthreemaContactSyncState.INITIAL -> ContactSyncState.INITIAL
    LibthreemaContactSyncState.IMPORTED -> ContactSyncState.IMPORTED
    LibthreemaContactSyncState.CUSTOM -> ContactSyncState.CUSTOM
}
