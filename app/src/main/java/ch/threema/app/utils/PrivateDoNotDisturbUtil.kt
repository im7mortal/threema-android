package ch.threema.app.utils

import ch.threema.common.TimeProvider
import ch.threema.data.IdentityProvider

class PrivateDoNotDisturbUtil(
    identityProvider: IdentityProvider,
    timeProvider: TimeProvider,
) : DoNotDisturbUtil(identityProvider, timeProvider) {
    override fun isDoNotDisturbActive() = false
}
