package ch.threema.app.libthreema.provider

import ch.threema.domain.types.IdentityString
import ch.threema.libthreema.ProviderException as LibthreemaProviderException

/**
 * Providers that can only be instantiated when there is an identity can extend this class.
 */
abstract class IdentityAwareProvider {
    protected abstract val guaranteedIdentityProvider: GuaranteedIdentityProvider

    /**
     * Check that the [identity] is not the user's identity.
     *
     * @throws LibthreemaProviderException.ContractViolation if [identity] is the user's identity
     */
    fun requireIdentityIsNotUserIdentity(identity: IdentityString) {
        if (guaranteedIdentityProvider.getIdentityString() == identity) {
            throw LibthreemaProviderException.ContractViolation("Provided identity is the user's identity")
        }
    }

    /**
     * Check that the [identities] do not contain the user's identity.
     *
     * @throws LibthreemaProviderException.ContractViolation if [identities] contain the user's identity
     */
    fun requireUserIdentityIsNotIn(identities: Iterable<IdentityString>) {
        if (identities.contains(guaranteedIdentityProvider.getIdentityString())) {
            throw LibthreemaProviderException.ContractViolation("Provided identities contain user's identity")
        }
    }
}
