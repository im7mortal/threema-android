package ch.threema.app.usecases.conversations

import ch.threema.app.drafts.DraftManager
import ch.threema.app.services.ContactService
import ch.threema.app.services.ConversationCategoryService
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.GroupService
import ch.threema.app.services.RingtoneService
import ch.threema.app.typingindicator.TypingIndicatorProvider
import ch.threema.app.usecases.availabilitystatus.WatchAllContactAvailabilityStatusesUseCase
import ch.threema.app.usecases.contacts.WatchAllMentionNamesUseCase
import ch.threema.app.usecases.contacts.WatchContactNameFormatSettingUseCase
import ch.threema.app.usecases.groups.WatchGroupCallsUseCase
import ch.threema.common.TimeProvider

class WatchUnarchivedConversationListItemsUseCase(
    watchUnarchivedConversationsUseCase: WatchUnarchivedConversationsUseCase,
    watchGroupCallsUseCase: WatchGroupCallsUseCase,
    conversationCategoryService: ConversationCategoryService,
    typingIndicatorProvider: TypingIndicatorProvider,
    contactService: ContactService,
    groupService: GroupService,
    distributionListService: DistributionListService,
    ringtoneService: RingtoneService,
    watchAvatarIterationsUseCase: WatchAvatarIterationsUseCase,
    watchContactNameFormatSettingUseCase: WatchContactNameFormatSettingUseCase,
    watchAllMentionNamesUseCase: WatchAllMentionNamesUseCase,
    watchAllContactAvailabilityStatusesUseCase: WatchAllContactAvailabilityStatusesUseCase,
    draftManager: DraftManager,
    timeProvider: TimeProvider,
) : WatchConversationListItemsUseCase(
    watchConversationsUseCase = watchUnarchivedConversationsUseCase,
    watchGroupCallsUseCase = watchGroupCallsUseCase,
    typingIndicatorProvider = typingIndicatorProvider,
    watchAvatarIterationsUseCase = watchAvatarIterationsUseCase,
    watchContactNameFormatSettingUseCase = watchContactNameFormatSettingUseCase,
    watchAllMentionNamesUseCase = watchAllMentionNamesUseCase,
    watchAllContactAvailabilityStatusesUseCase = watchAllContactAvailabilityStatusesUseCase,
    draftManager = draftManager,
    conversationCategoryService = conversationCategoryService,
    contactService = contactService,
    groupService = groupService,
    distributionListService = distributionListService,
    ringtoneService = ringtoneService,
    timeProvider = timeProvider,
)
