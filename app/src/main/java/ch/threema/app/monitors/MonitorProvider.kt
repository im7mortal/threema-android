package ch.threema.app.monitors

import ch.threema.app.applock.AppLockNotificationUpdaterMonitor
import ch.threema.app.apptaskexecutor.AppTaskExecutor
import ch.threema.app.conversation.ConversationRefreshMonitor
import ch.threema.app.conversation.GroupStatusMessageMonitor
import ch.threema.app.di.getOrNull
import ch.threema.app.notifications.ConversationNotificationUpdaterMonitor
import ch.threema.app.passphrase.PassphraseStateMonitor
import ch.threema.app.poll.PollGroupStatusMonitor
import ch.threema.app.profilepicture.ProfilePictureUpdateMonitor
import ch.threema.app.servermessage.ServerMessageMonitor
import ch.threema.app.shortcuts.ShortcutsUpdaterMonitor
import ch.threema.app.startup.MasterKeyEventMonitor
import ch.threema.app.startup.RemoteSecretProtectionStateMonitor
import ch.threema.app.voip.VoipCallStatusMonitor
import ch.threema.app.widget.WidgetUpdaterMonitor
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class MonitorProvider : KoinComponent {
    val monitors: List<Monitor>
        get() = listOfNotNull(
            get<MasterKeyEventMonitor>(),
            get<PassphraseStateMonitor>(),
            // Remote secret protection state monitor is null in flavors which do not support remote secrets
            getOrNull<RemoteSecretProtectionStateMonitor>(),
            get<ConversationNotificationUpdaterMonitor>(),
            get<AppTaskExecutor>(),
            get<WidgetUpdaterMonitor>(),
            get<ServerMessageMonitor>(),
            get<ConversationRefreshMonitor>(),
            get<ShortcutsUpdaterMonitor>(),
            get<AppLockNotificationUpdaterMonitor>(),
            get<GroupStatusMessageMonitor>(),
            get<PollGroupStatusMonitor>(),
            get<ProfilePictureUpdateMonitor>(),
            get<VoipCallStatusMonitor>(),
        )
}
