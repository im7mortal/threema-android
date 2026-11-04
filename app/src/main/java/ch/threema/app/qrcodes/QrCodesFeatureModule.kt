package ch.threema.app.qrcodes

import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val qrCodesFeatureModule = module {
    factory<ContactUrlUtil>()
    factory<QrCodeGenerator>()
}
