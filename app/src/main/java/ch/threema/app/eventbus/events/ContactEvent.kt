package ch.threema.app.eventbus.events

import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString

sealed class ContactEvent {
    abstract val identity: Identity

    val identityString: IdentityString
        get() = identity.value

    data class NewContact(override val identity: Identity) : ContactEvent()

    data class ContactUpdated(override val identity: Identity) : ContactEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(identityString: IdentityString) =
                ContactUpdated(Identity(identityString))
        }
    }

    data class ContactProfilePictureUpdated(override val identity: Identity) : ContactEvent() {
        companion object {
            @JvmStatic
            fun javaCreate(identityString: IdentityString) =
                ContactProfilePictureUpdated(Identity(identityString))
        }
    }

    data class ContactRemoved(override val identity: Identity) : ContactEvent()
}
