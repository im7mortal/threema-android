package ch.threema.android

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person

fun NotificationManagerCompat.exists(channelId: String): Boolean =
    getNotificationChannelCompat(channelId) != null

fun NotificationChannelCompat.Builder.setSound(sound: Uri?, usage: Int) {
    val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
        .setUsage(usage)
        .build()
    setSound(sound, audioAttributes)
}

fun buildNotificationAction(
    @DrawableRes icon: Int,
    title: CharSequence?,
    intent: PendingIntent?,
    block: NotificationCompat.Action.Builder.() -> Unit = {},
): NotificationCompat.Action =
    NotificationCompat.Action.Builder(icon, title, intent)
        .apply(block)
        .build()

fun buildNotification(context: Context, channelId: String, block: NotificationCompat.Builder.() -> Unit): Notification =
    NotificationCompat.Builder(context, channelId)
        .apply(block)
        .build()

fun buildPerson(block: Person.Builder.() -> Unit): Person =
    Person.Builder()
        .apply(block)
        .build()
