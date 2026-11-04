package ch.threema.app.androidcontactsync

import ch.threema.app.androidcontactsync.read.AndroidContactReader
import ch.threema.app.androidcontactsync.read.LookupInfoReader
import ch.threema.app.androidcontactsync.read.RawContactCursorProvider
import ch.threema.app.androidcontactsync.read.RawContactReader
import ch.threema.app.androidcontactsync.read.ThreemaRawContactReader
import ch.threema.app.androidcontactsync.synchronization.AndroidContactMatcher
import ch.threema.app.androidcontactsync.synchronization.IdentityLookupClient
import ch.threema.app.androidcontactsync.synchronization.ThreemaRawContactManager
import ch.threema.app.androidcontactsync.usecases.GetAndroidContactNameUseCase
import ch.threema.app.androidcontactsync.usecases.GetRawContactNameUseCase
import ch.threema.app.androidcontactsync.usecases.SynchronizeAndroidContactsUseCase
import ch.threema.app.androidcontactsync.usecases.UpdateContactNameUseCase
import ch.threema.app.androidcontactsync.write.ThreemaRawContactWriter
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val androidContactFeatureModule = module {
    single<AndroidContactChangeMonitor>()
    factory<AndroidContactMatcher>()
    single<AndroidContactReader>()
    single<GetAndroidContactNameUseCase>()
    single<GetRawContactNameUseCase>()
    factory<IdentityLookupClient>()
    single<LookupInfoReader>()
    single<RawContactCursorProvider>()
    single<RawContactReader>()
    factory<SynchronizeAndroidContactsUseCase>()
    factory<ThreemaRawContactManager>()
    factory<ThreemaRawContactReader>()
    factory<ThreemaRawContactWriter>()
    single<UpdateContactNameUseCase>()
}
