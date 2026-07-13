package ch.threema.app.eventbus.events

import ch.threema.data.datatypes.GroupIdentity
import ch.threema.data.datatypes.GroupState
import ch.threema.domain.types.GroupDatabaseId
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString

sealed class GroupEvent {
    data class NewGroup(val groupIdentity: GroupIdentity) : GroupEvent()

    data class GroupRenamed(val groupIdentity: GroupIdentity, val newName: String) : GroupEvent()

    data class GroupUpdated(val groupIdentity: GroupIdentity) : GroupEvent()

    data class GroupProfilePictureUpdated(val groupIdentity: GroupIdentity) : GroupEvent()

    data class GroupRemoved(val groupDbId: GroupDatabaseId) : GroupEvent()

    data class NewMember(val groupIdentity: GroupIdentity, val identity: Identity) : GroupEvent() {
        val identityString: IdentityString
            get() = identity.value
    }

    data class MemberLeft(val groupIdentity: GroupIdentity, val identity: Identity) : GroupEvent() {
        val identityString: IdentityString
            get() = identity.value
    }

    data class MemberKicked(val groupIdentity: GroupIdentity, val identity: Identity) : GroupEvent() {
        val identityString: IdentityString
            get() = identity.value
    }

    data class UserLeftGroup(val groupIdentity: GroupIdentity) : GroupEvent()

    data class GroupStateChanged(val groupIdentity: GroupIdentity, val newState: GroupState) : GroupEvent()
}
