package ch.threema.app.voicemessage

import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val voiceMessageFeatureModule = module {
    viewModel<VoiceRecorderViewModel>()
}
