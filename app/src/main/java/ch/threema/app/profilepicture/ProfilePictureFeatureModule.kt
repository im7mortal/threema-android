package ch.threema.app.profilepicture

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val profilePictureFeatureModule = module {
    singleOf(::ProfilePictureUpdateMonitor)
}
