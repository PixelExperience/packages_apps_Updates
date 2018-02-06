/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2018 Pixel Experience (jhenrique09)
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */
package org.pixelexperience.ota.receiver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import org.pixelexperience.ota.R;
import org.pixelexperience.ota.misc.Constants;

public class DownloadNotifier {

    private static NotificationManager mNotificationManager;

    private DownloadNotifier() {
        // Don't instantiate me bro
    }

    public static void notifyDownloadComplete(Context context,
                                              Intent updateIntent) {

        Notification.Builder builder = createBaseContentBuilder(context, updateIntent, Color.GREEN)
                .setSmallIcon(R.drawable.ic_system_update)
                .setContentTitle(context.getString(R.string.notif_download_success_title))
                .setContentText(context.getString(R.string.notif_download_success_summary))
                .setTicker(context.getString(R.string.notif_download_success_title));

        getManager(context).notify(R.string.notif_download_success_title, builder.build());
    }

    public static void notifyDownloadError(Context context,
                                           Intent updateIntent, int failureMessageResId) {
        Notification.Builder builder = createBaseContentBuilder(context, updateIntent, Color.RED)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.notif_download_failure_title))
                .setContentText(context.getString(failureMessageResId))
                .setTicker(context.getString(R.string.notif_download_failure_title));

        getManager(context).notify(R.string.notif_download_success_title, builder.build());
    }

    private static Notification.Builder createBaseContentBuilder(Context context,
                                                                 Intent updateIntent, int lightColor) {
        PendingIntent contentIntent = PendingIntent.getActivity(context, 1,
                updateIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);

        CharSequence name = context.getString(R.string.app_name);
        NotificationChannel mChannel = new NotificationChannel(Constants.DOWNLOAD_CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
        mChannel.enableLights(true);
        mChannel.setLightColor(lightColor);
        mChannel.setShowBadge(true);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getManager(context).createNotificationChannel(mChannel);

        return new Notification.Builder(context, Constants.DOWNLOAD_CHANNEL_ID)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(contentIntent)
                .setLocalOnly(true)
                .setAutoCancel(true);
    }

    private static NotificationManager getManager(Context context) {
        if (mNotificationManager == null) {
            mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return mNotificationManager;
    }
}
