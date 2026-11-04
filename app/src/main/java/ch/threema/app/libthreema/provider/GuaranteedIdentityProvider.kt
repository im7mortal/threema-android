package ch.threema.app.libthreema.provider

import ch.threema.data.IdentityProvider
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString

/**
 * The [GuaranteedIdentityProvider] guarantees that an identity exists.
 *
 * @throws [IllegalStateException] in case this is instantiated when no identity exists
 */
class GuaranteedIdentityProvider(
    identityProvider: IdentityProvider,
) {
    private val identity = identityProvider.getIdentity()
        ?: error("Cannot instantiate guaranteed identity provider without identity")

    fun getIdentity(): Identity = identity

    fun getIdentityString(): IdentityString = identity.value
}
