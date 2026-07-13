package ch.threema.app.servermessage

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val serverMessageFeatureModule = module {
    viewModelOf(::ServerMessageViewModel)
    factoryOf(::ServerMessageMonitor)
}
