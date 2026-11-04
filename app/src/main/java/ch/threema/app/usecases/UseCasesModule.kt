package ch.threema.app.usecases

import ch.threema.app.usecases.avatar.GetAndPrepareAvatarUseCase
import ch.threema.app.usecases.contacts.GetPersonUseCase
import ch.threema.app.usecases.conversation.GetConversationCategoryUseCase
import ch.threema.app.usecases.conversation.GetConversationCategoryUseCaseImpl
import ch.threema.app.usecases.conversation.SetConversationCategoryUseCase
import ch.threema.app.usecases.conversations.WatchAvatarIterationsUseCase
import ch.threema.app.usecases.groups.GetGroupDisplayNameUseCase
import ch.threema.app.usecases.groups.WatchGroupCallsUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val useCasesModule = module {
    factory<CheckLicenseUseCase>()
    factory<ExportDebugLogUseCase>()
    factory<GetAndPrepareAvatarUseCase>()
    factory<GetDebugMetaDataUseCase>()
    factory<GetConversationCategoryUseCaseImpl>() bind GetConversationCategoryUseCase::class
    factory<SetConversationCategoryUseCase>()
    factory<GetGroupDisplayNameUseCase>()
    factory<GetPersonUseCase>()
    factory<OverrideOneTimeHintsUseCase>()
    factory<ShareDebugLogUseCase>()
    factory<WatchAvatarIterationsUseCase>()
    factory<WatchGroupCallsUseCase>()
    factory<GetBottomSheetAppShareTargetsUseCase>()
    factory<GetInviteFriendIntentUseCase>()
    factory<CheckBackupsFeatureEnabledUseCase>()
    factory<CopyToClipboardUseCase>()
    factory<ShareIdentityUseCase>()
}
