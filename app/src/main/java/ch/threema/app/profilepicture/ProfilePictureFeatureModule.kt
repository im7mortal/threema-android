package ch.threema.app.profilepicture

import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val profilePictureFeatureModule = module {
    single<ProfilePictureUpdateMonitor>()
}
