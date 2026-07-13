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

interface GlobalEventBuses {
    val actions: EventBus<ActionEvent>
    val contacts: EventBus<ContactEvent>
    val distributionLists: EventBus<DistributionListEvent>
    val conversations: EventBus<ConversationEvent>
    val groups: EventBus<GroupEvent>
    val messages: EventBus<MessageEvent>
    val polls: EventBus<PollEvent>
    val profiles: EventBus<ProfileEvent>
    val voipCalls: EventBus<VoipCallEvent>
}
