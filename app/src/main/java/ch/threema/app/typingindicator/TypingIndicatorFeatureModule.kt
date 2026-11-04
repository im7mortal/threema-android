package ch.threema.app.typingindicator

import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val typingIndicatorFeatureModule = module {
    single<TypingIndicatorManager>() bind TypingIndicatorProvider::class
}
