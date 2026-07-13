package ch.threema.app.usecases

import ch.threema.app.BuildFlavor
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.usecases.avatar.GetAndPrepareAvatarUseCase
import ch.threema.app.usecases.contacts.GetPersonUseCase
import ch.threema.app.usecases.conversations.WatchAvatarIterationsUseCase
import ch.threema.app.usecases.groups.GetGroupDisplayNameUseCase
import ch.threema.app.usecases.groups.WatchGroupCallsUseCase
import ch.threema.app.utils.ConfigUtils
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val useCasesModule = module {
    factoryOf(::ExportDebugLogUseCase)
    factoryOf(::GetAndPrepareAvatarUseCase)
    factory<GetDebugMetaDataUseCase> {
        GetDebugMetaDataUseCase(
            identityProvider = get(),
            masterKeyManager = get(),
            appVersionHistoryManager = get(),
            sentryIdProvider = get(),
            appRestrictionService = if (BuildFlavor.current.isWork) {
                get<AppRestrictionService>()
            } else {
                null
            },
            appRestrictions = get(),
            timeProvider = get(),
            isThreemaPushUsed = {
                ConfigUtils.useThreemaPush(get(), get())
            },
        )
    }
    factoryOf(::GetGroupDisplayNameUseCase)
    factoryOf(::GetPersonUseCase)
    factoryOf(::OverrideOneTimeHintsUseCase)
    factoryOf(::ShareDebugLogUseCase)
    factoryOf(::WatchAvatarIterationsUseCase)
    factoryOf(::WatchGroupCallsUseCase)
    factoryOf(::GetBottomSheetAppShareTargetsUseCase)
    factoryOf(::GetInviteFriendIntentUseCase)
    factoryOf(::CheckBackupsFeatureEnabledUseCase)
    factoryOf(::CopyToClipboardUseCase)
    factoryOf(::ShareIdentityUseCase)
}
