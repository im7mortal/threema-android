package ch.threema.app.threemasafe

import ch.threema.app.threemasafe.usecases.CheckBadPasswordUseCase
import ch.threema.app.threemasafe.usecases.GetLastThreemaSafeBackupDataUseCase
import ch.threema.app.threemasafe.usecases.GetThreemaSafeConfigurationUseCase
import ch.threema.app.utils.ConfigUtils
import java.util.Locale
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val threemaSafeFeatureModule = module {
    viewModel<ThreemaSafeViewModel>()
    factory<GetLastThreemaSafeBackupDataUseCase>()
    factory<GetThreemaSafeConfigurationUseCase>()
    factory<ThreemaSafeMDMConfig> {
        @Suppress("DEPRECATION")
        ThreemaSafeMDMConfig.getInstance()
    }
    factory<CheckBadPasswordUseCase>()
    factory<ThreemaSafeBackupCreator>()
    factory<ThreemaSafeInfoSectionCreator> {
        ThreemaSafeInfoSectionCreator(
            appVersion = ConfigUtils.getAppVersion(),
            locale = Locale.getDefault().toString(),
        )
    }
    factory<ThreemaSafeUserSectionCreator>()
    factory<ThreemaSafeContactsSectionCreator>()
    factory<ThreemaSafeGroupsSectionCreator>()
    factory<ThreemaSafeDistributionListsSectionCreator>()
    factory<ThreemaSafeSettingsSectionCreator>()
}
