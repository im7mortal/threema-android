package ch.threema.app.activities.referral

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val referralFeatureModule = module {
    viewModel<ReferralViewModel>()
}
