package ch.threema.app.typingindicator

import ch.threema.common.mapState
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import kotlinx.coroutines.flow.StateFlow

interface TypingIndicatorProvider {
    fun isTyping(identity: Identity): Boolean

    fun watchTypingIdentities(): StateFlow<Set<Identity>>

    fun isTypingWithIdentityString(identity: IdentityString): Boolean =
        isTyping(Identity(identity))

    fun watchTypingIdentityStrings(): StateFlow<Set<IdentityString>> =
        watchTypingIdentities().mapState { identities ->
            identities.map(Identity::value).toSet()
        }
}
