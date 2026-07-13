package ch.threema.test

import ch.threema.data.IdentityProvider
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString

class TestIdentityProvider(
    private val identity: Identity?,
) : IdentityProvider {
    override fun getIdentity(): Identity? =
        identity

    override fun getIdentityString(): IdentityString? =
        identity?.value

    companion object {
        @JvmStatic
        fun javaCreate(identity: IdentityString?) =
            TestIdentityProvider(identity?.let(::Identity))
    }
}
