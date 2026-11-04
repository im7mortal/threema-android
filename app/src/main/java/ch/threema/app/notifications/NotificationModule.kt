package ch.threema.app.notifications

import androidx.core.app.NotificationManagerCompat
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory

val notificationModule = module {
    factory<CallNotificationManagerImpl>() bind CallNotificationManager::class
    factory { NotificationManagerCompat.from(androidContext()) }
    factory<ConversationNotificationUpdaterMonitor>()
    factory<ConversationNotificationConverter>()
}
