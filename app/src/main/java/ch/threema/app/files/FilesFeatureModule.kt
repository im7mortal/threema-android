package ch.threema.app.files

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val filesFeatureModule = module {
    single<AppDirectoryProvider>()
    factory<AppLogoFileHandleProvider>()
    factory<MessageFileHandleProvider>()
    factory<GroupProfilePictureFileHandleProvider>()
    factory<ProfilePictureFileHandleProvider>()
    factory<WallpaperFileHandleProvider>()
    factory<TempFilesCleanupWorker.Scheduler>()
}
