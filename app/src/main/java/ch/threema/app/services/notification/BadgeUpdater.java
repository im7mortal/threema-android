package ch.threema.app.services.notification;

import static ch.threema.android.ThreadUtilKt.isMainThread;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import android.content.AsyncQueryHandler;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

import org.slf4j.Logger;

import ch.threema.app.BuildConfig;
import ch.threema.app.utils.ConfigUtils;

/**
 * This class tries to display a notification badge on the app's launcher icon, using vendor-specific approaches.
 * We can likely fully remove this class once we drop support for Android 7, as starting with Android 8.0 (API level 26), this is supported natively.
 */
public class BadgeUpdater {
    private static final Logger logger = getThreemaLogger("BadgeUpdater");

    private final @NonNull Context appContext;
    private AsyncQueryHandler queryHandler;

    public BadgeUpdater(
        @NonNull Context appContext
    ) {
        this.appContext = appContext;
    }

    public void showIconBadge(int unreadMessages) {
        logger.info("Badge: showing {} unread", unreadMessages);

        if (appContext.getPackageManager().resolveContentProvider("com.teslacoilsw.notifier", 0) != null) {
            // nova launcher / teslaunread
            try {
                String launcherClassName = appContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
                final ContentValues contentValues = new ContentValues();
                contentValues.put("tag", BuildConfig.APPLICATION_ID + "/" + launcherClassName);
                contentValues.put("count", unreadMessages);

                appContext.getApplicationContext().getContentResolver().insert(Uri.parse("content://com.teslacoilsw.notifier/unread_count"), contentValues);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        } else if (ConfigUtils.isHuaweiDevice()) {
            try {
                String launcherClassName = appContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
                Bundle localBundle = new Bundle();
                localBundle.putString("package", BuildConfig.APPLICATION_ID);
                localBundle.putString("class", launcherClassName);
                localBundle.putInt("badgenumber", unreadMessages);
                appContext.getContentResolver().call(Uri.parse("content://com.huawei.android.launcher.settings/badge/"), "change_badge", null, localBundle);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        } else if (ConfigUtils.isSonyDevice()) {
            try {
                String launcherClassName = appContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
                if (appContext.getPackageManager().resolveContentProvider("com.sonymobile.home.resourceprovider", 0) != null) {
                    // use content provider
                    final ContentValues contentValues = new ContentValues();
                    contentValues.put("badge_count", unreadMessages);
                    contentValues.put("package_name", BuildConfig.APPLICATION_ID);
                    contentValues.put("activity_name", launcherClassName);

                    if (isMainThread()) {
                        if (queryHandler == null) {
                            queryHandler = new AsyncQueryHandler(
                                appContext.getApplicationContext().getContentResolver()) {
                            };
                        }
                        queryHandler.startInsert(0, null, Uri.parse("content://com.sonymobile.home.resourceprovider/badge"), contentValues);
                    } else {
                        appContext.getApplicationContext().getContentResolver().insert(Uri.parse("content://com.sonymobile.home.resourceprovider/badge"), contentValues);
                    }
                } else {
                    // use broadcast
                    Intent intent = new Intent("com.sonyericsson.home.action.UPDATE_BADGE");
                    intent.putExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME", BuildConfig.APPLICATION_ID);
                    intent.putExtra("com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME", launcherClassName);
                    intent.putExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE", String.valueOf(unreadMessages));
                    intent.putExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", unreadMessages > 0);
                    appContext.sendBroadcast(intent);
                }
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        } else {
            // also works on LG and later HTC devices
            try {
                String launcherClassName = appContext.getPackageManager().getLaunchIntentForPackage(BuildConfig.APPLICATION_ID).getComponent().getClassName();
                Intent intent = new Intent("android.intent.action.BADGE_COUNT_UPDATE");
                intent.putExtra("badge_count", unreadMessages);
                intent.putExtra("badge_count_package_name", BuildConfig.APPLICATION_ID);
                intent.putExtra("badge_count_class_name", launcherClassName);
                appContext.sendBroadcast(intent);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
    }
}
