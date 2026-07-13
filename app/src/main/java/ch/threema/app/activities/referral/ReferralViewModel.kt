package ch.threema.app.activities.referral

import ch.threema.app.framework.BaseViewModel
import ch.threema.data.IdentityProvider

class ReferralViewModel(
    private val identityProvider: IdentityProvider,
) : BaseViewModel<Unit, ReferralScreenEvent>() {

    override suspend fun initialize() = Unit

    fun onClickShareInvitationLink() = runAction {
        val ownIdentity = identityProvider.getIdentity()
        emitEvent(
            if (ownIdentity != null) {
                ReferralScreenEvent.ShareInvitationLink(ownIdentity)
            } else {
                ReferralScreenEvent.Error
            },
        )
    }
}
