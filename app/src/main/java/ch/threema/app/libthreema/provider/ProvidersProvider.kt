package ch.threema.app.libthreema.provider

import ch.threema.libthreema.DataBackupProviders
import kotlin.getValue
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ProvidersProvider : KoinComponent {
    private val userProfileProvider: UserProfileProvider by inject()
    private val settingsProvider: SettingsProvider by inject()
    private val cspNoncesProvider: CspNonceProvider by inject()
    private val contactProvider: ContactProvider by inject()
    private val groupProvider: GroupProvider by inject()
    private val distributionListProvider: DistributionListProvider by inject()
    private val pollProvider: PollProvider by inject()
    private val conversationProvider: ConversationProvider by inject()

    fun createBackupRestoreProviders(): DataBackupProviders = DataBackupProviders(
        userProfile = userProfileProvider,
        settings = settingsProvider,
        cspNonces = cspNoncesProvider,
        contacts = contactProvider,
        groups = groupProvider,
        distributionLists = distributionListProvider,
        polls = pollProvider,
        conversations = conversationProvider,
    )
}
