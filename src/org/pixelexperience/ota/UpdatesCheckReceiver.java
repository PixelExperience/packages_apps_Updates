/*
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2019 The PixelExperience Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pixelexperience.ota;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.pixelexperience.ota.controller.ABUpdateInstaller;
import org.pixelexperience.ota.download.DownloadClient;
import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

public class UpdatesCheckReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdatesCheckReceiver";

    private static final String DAILY_CHECK_ACTION = "daily_check_action";
    private static final String ONESHOT_CHECK_ACTION = "oneshot_check_action";

    private static final String NEW_UPDATES_NOTIFICATION_CHANNEL =
            "new_updates_notification_channel";
    public static final int NOTIFICATION_ID = 20;

    private static void showNotification(Context context) {
        if (ABUpdateInstaller.needsReboot() || Utils.getPersistentStatus(context) != UpdateStatus.Persistent.UNKNOWN) {
            return;
        }
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(
                NEW_UPDATES_NOTIFICATION_CHANNEL,
                context.getString(R.string.new_updates_channel_title),
                NotificationManager.IMPORTANCE_LOW);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context,
                NEW_UPDATES_NOTIFICATION_CHANNEL);
        notificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        Intent notificationIntent = new Intent(context, UpdatesActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(intent);
        notificationBuilder.setContentTitle(context.getString(R.string.update_found_notification));
        notificationBuilder.setAutoCancel(true);
        notificationManager.createNotificationChannel(notificationChannel);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private static PendingIntent getRepeatingUpdatesCheckIntent(Context context) {
        Intent intent = new Intent(context, UpdatesCheckReceiver.class);
        intent.setAction(DAILY_CHECK_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    public static void updateRepeatingUpdatesCheck(Context context) {
        cancelRepeatingUpdatesCheck(context);
        scheduleRepeatingUpdatesCheck(context);
    }

    public static void scheduleRepeatingUpdatesCheck(Context context) {
        PendingIntent updateCheckIntent = getRepeatingUpdatesCheckIntent(context);
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.setRepeating(AlarmManager.RTC, System.currentTimeMillis() +
                        Utils.getUpdateCheckInterval(context), Utils.getUpdateCheckInterval(context),
                updateCheckIntent);

        Date nextCheckDate = new Date(System.currentTimeMillis() +
                Utils.getUpdateCheckInterval(context));
        Log.d(TAG, "Setting automatic updates check: " + nextCheckDate);
    }

    public static void cancelRepeatingUpdatesCheck(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getRepeatingUpdatesCheckIntent(context));
    }

    private static PendingIntent getUpdatesCheckIntent(Context context) {
        Intent intent = new Intent(context, UpdatesCheckReceiver.class);
        intent.setAction(ONESHOT_CHECK_ACTION);
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    public static void scheduleUpdatesCheck(Context context) {
        long millisToNextCheck = AlarmManager.INTERVAL_HOUR * 2;
        PendingIntent updateCheckIntent = getUpdatesCheckIntent(context);
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.set(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + millisToNextCheck,
                updateCheckIntent);

        Date nextCheckDate = new Date(System.currentTimeMillis() + millisToNextCheck);
        Log.d(TAG, "Setting one-shot updates check: " + nextCheckDate);
    }

    public static void cancelUpdatesCheck(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getUpdatesCheckIntent(context));
        Log.d(TAG, "Cancelling pending one-shot check");
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Set a repeating alarm on boot to check for new updates once per day
            scheduleRepeatingUpdatesCheck(context);
        }

        if (!Utils.isNetworkAvailable(context)) {
            Log.d(TAG, "Network not available, scheduling new check");
            scheduleUpdatesCheck(context);
            return;
        }

        final File json = Utils.getCachedUpdateList(context);
        final File jsonNew = new File(json.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL();
        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(boolean cancelled) {
                Log.e(TAG, "Could not download updates list, scheduling new check");
                scheduleUpdatesCheck(context);
            }

            @Override
            public void onResponse(int statusCode, String url,
                                   DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                try {
                    if (Utils.checkForNewUpdates(json, jsonNew, true, context)) {
                        showNotification(context);
                        updateRepeatingUpdatesCheck(context);
                    }
                    jsonNew.renameTo(json);
                    // In case we set a one-shot check because of a previous failure
                    cancelUpdatesCheck(context);
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Could not parse list, scheduling new check", e);
                    scheduleUpdatesCheck(context);
                }
            }
        };

        try {
            DownloadClient downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonNew)
                    .setDownloadCallback(callback)
                    .setUseIncremental(Utils.shouldUseIncremental(context))
                    .build();
            downloadClient.start();
        } catch (IOException e) {
            Log.e(TAG, "Could not fetch list, scheduling new check", e);
            scheduleUpdatesCheck(context);
        }
    }
}
