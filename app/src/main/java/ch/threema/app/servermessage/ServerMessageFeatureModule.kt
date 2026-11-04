package ch.threema.app.servermessage

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.viewModel

val serverMessageFeatureModule = module {
    viewModel<ServerMessageViewModel>()
    factory<ServerMessageMonitor>()
}
