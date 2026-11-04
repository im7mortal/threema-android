package ch.threema.app.di.modules

import org.koin.dsl.module

val appTypeSpecificModule = module {
    includes(
        onlyPrivateModule,
        onlyWorkModule,
        onlyOnPremModule,
        privateAndWorkModule,
        workAndOnPremModule,
    )
}
