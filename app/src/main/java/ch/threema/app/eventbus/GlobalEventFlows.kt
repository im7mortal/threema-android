package ch.threema.app.eventbus

import ch.threema.app.eventbus.events.ActionEvent
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.eventbus.events.ConversationEvent
import ch.threema.app.eventbus.events.DistributionListEvent
import ch.threema.app.eventbus.events.GroupEvent
import ch.threema.app.eventbus.events.MessageEvent
import ch.threema.app.eventbus.events.PollEvent
import ch.threema.app.eventbus.events.ProfileEvent
import ch.threema.app.eventbus.events.VoipCallEvent
import kotlinx.coroutines.flow.Flow

interface GlobalEventFlows {
    val actions: Flow<ActionEvent>
    val contacts: Flow<ContactEvent>
    val distributionLists: Flow<DistributionListEvent>
    val conversations: Flow<ConversationEvent>
    val groups: Flow<GroupEvent>
    val messages: Flow<MessageEvent>
    val polls: Flow<PollEvent>
    val profiles: Flow<ProfileEvent>
    val voipCalls: Flow<VoipCallEvent>
}
