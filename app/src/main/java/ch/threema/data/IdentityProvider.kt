package ch.threema.data

import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString

interface IdentityProvider {
    fun getIdentity(): Identity?

    fun getIdentityString(): IdentityString?

    fun hasIdentity() =
        getIdentity() != null
}
