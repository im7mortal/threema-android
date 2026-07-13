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
import ch.threema.common.DispatcherProvider
import kotlinx.coroutines.CoroutineScope

class GlobalEventBusesImpl(
    dispatcherProvider: DispatcherProvider,
) : GlobalEventBuses, GlobalEventFlows {
    private val coroutineScope = CoroutineScope(dispatcherProvider.worker)

    override val actions = EventBus<ActionEvent>(coroutineScope)
    override val contacts = EventBus<ContactEvent>(coroutineScope)
    override val distributionLists = EventBus<DistributionListEvent>(coroutineScope)
    override val conversations = EventBus<ConversationEvent>(coroutineScope)
    override val groups = EventBus<GroupEvent>(coroutineScope)
    override val messages = EventBus<MessageEvent>(coroutineScope)
    override val polls = EventBus<PollEvent>(coroutineScope)
    override val profiles = EventBus<ProfileEvent>(coroutineScope)
    override val voipCalls = EventBus<VoipCallEvent>(coroutineScope)
}
