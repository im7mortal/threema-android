package ch.threema.app.voicemessage

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val voiceMessageFeatureModule = module {
    viewModel { params ->
        VoiceRecorderViewModel(
            appContext = get(),
            messageService = get(),
            preferenceService = get(),
            fileService = get(),
            messageReceiver = params.get(),
        )
    }
}
