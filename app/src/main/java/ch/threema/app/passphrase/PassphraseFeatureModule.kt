package ch.threema.app.passphrase

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val passphraseFeatureModule = module {
    factory<PassphraseStateMonitor>()
}
