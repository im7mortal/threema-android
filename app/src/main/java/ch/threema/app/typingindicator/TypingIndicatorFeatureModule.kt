package ch.threema.app.typingindicator

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val typingIndicatorFeatureModule = module {
    singleOf(::TypingIndicatorManager) bind TypingIndicatorProvider::class
}
