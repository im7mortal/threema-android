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
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidContactFeatureModule = module {
    singleOf(::AndroidContactChangeMonitor)
    factoryOf(::AndroidContactMatcher)
    singleOf(::AndroidContactReader)
    singleOf(::GetAndroidContactNameUseCase)
    singleOf(::GetRawContactNameUseCase)
    factoryOf(::IdentityLookupClient)
    singleOf(::LookupInfoReader)
    factoryOf(::RawContactCursorProvider)
    singleOf(::RawContactReader)
    factoryOf(::SynchronizeAndroidContactsUseCase)
    factoryOf(::ThreemaRawContactManager)
    factoryOf(::ThreemaRawContactReader)
    factoryOf(::ThreemaRawContactWriter)
    singleOf(::UpdateContactNameUseCase)
}
