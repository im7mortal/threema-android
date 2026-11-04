package ch.threema.data

import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString

interface IdentityProvider {
    fun getIdentity(): Identity?

    fun getIdentityString(): IdentityString?

    fun requireIdentity(): Identity =
        getIdentity() ?: error("Identity not available")

    /**
     * Returns the current identity.
     *
     * @throws IllegalStateException if no identity is currently available.
     */
    fun requireIdentityString(): IdentityString

    fun hasIdentity() =
        getIdentity() != null
}
