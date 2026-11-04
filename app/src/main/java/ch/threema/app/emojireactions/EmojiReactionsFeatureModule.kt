package ch.threema.app.emojireactions

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val emojiReactionsFeatureModule = module {
    viewModel<EmojiReactionsViewModel>()
}
