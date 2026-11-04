package ch.threema.app.libthreema.conversion

import ch.threema.data.datatypes.GroupIdentity
import ch.threema.libthreema.GroupIdentity as LibthreemaGroupIdentity

fun LibthreemaGroupIdentity.toGroupIdentity() = GroupIdentity(
    creatorIdentity = creatorIdentity,
    groupId = groupId.toLong(),
)

fun GroupIdentity.toLibthreemaGroupIdentity() = LibthreemaGroupIdentity(
    groupId = groupId.toULong(),
    creatorIdentity = creatorIdentity,
)
