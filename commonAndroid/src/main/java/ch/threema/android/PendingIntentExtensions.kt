package ch.threema.android

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Gets or creates a [PendingIntent] for the given [intent] to start an activity.
 *
 * @param flags must not include FLAG_NO_CREATE
 */
fun getPendingIntentForActivity(
    context: Context,
    requestCode: Int,
    intent: Intent,
    flags: Int,
): PendingIntent =
    PendingIntent.getActivity(context, requestCode, intent, flags)

fun cancelPendingIntentForActivity(
    context: Context,
    requestCode: Int,
    intent: Intent,
    flags: Int = PendingIntent.FLAG_IMMUTABLE,
) {
    PendingIntent.getActivity(context, requestCode, intent, flags or PendingIntent.FLAG_NO_CREATE)?.cancel()
}

/**
 * Gets or creates a [PendingIntent] for the given [intent] to start a service.
 *
 * @param flags must not include FLAG_NO_CREATE
 */
fun getPendingIntentForService(
    context: Context,
    requestCode: Int,
    intent: Intent,
    flags: Int,
): PendingIntent =
    PendingIntent.getService(context, requestCode, intent, flags)

fun cancelPendingIntentForService(
    context: Context,
    requestCode: Int,
    intent: Intent,
    flags: Int = PendingIntent.FLAG_IMMUTABLE,
) {
    PendingIntent.getService(context, requestCode, intent, flags or PendingIntent.FLAG_NO_CREATE)?.cancel()
}

/**
 * Gets or creates a [PendingIntent] for the given [intent] to start a foreground service.
 *
 * @param flags must not include FLAG_NO_CREATE
 */
@RequiresApi(Build.VERSION_CODES.O)
fun getPendingIntentForForegroundService(
    context: Context,
    requestCode: Int,
    intent: Intent,
    flags: Int,
): PendingIntent =
    PendingIntent.getForegroundService(context, requestCode, intent, flags)

/**
 * Gets or creates a [PendingIntent] for the given [intent] to start an broadcast receiver.
 *
 * @param flags must not include FLAG_NO_CREATE
 */
fun getPendingIntentForBroadcast(
    context: Context,
    requestCode: Int,
    intent: Intent,
    flags: Int,
): PendingIntent =
    PendingIntent.getBroadcast(context, requestCode, intent, flags)
