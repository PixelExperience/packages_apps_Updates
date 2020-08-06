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

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.pixelexperience.ota.misc.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;

public class ExportUpdateService extends Service {

    public static final String ACTION_EXPORT_STATUS = "action_export_status";
    public static final String EXTRA_EXPORT_STATUS = "extra_export_status";
    public static final int EXPORT_STATUS_RUNNING = 0;
    public static final int EXPORT_STATUS_ALREADY_RUNNING = 1;
    public static final int EXPORT_STATUS_SUCCESS = 2;
    public static final int EXPORT_STATUS_FAILED = 3;

    public static final int EXPORT_STATUS_PERMISSION_REQUEST_CODE = 869821;

    public static final String ACTION_START_EXPORTING = "start_exporting";
    public static final String ACTION_STOP_EXPORTING = "stop_exporting";
    public static final String EXTRA_SOURCE_FILE = "source_file";
    public static final String EXTRA_DEST_FILE = "dest_file";
    private static final String TAG = "ExportUpdateService";
    private static final int NOTIFICATION_ID = 16;
    private static final String EXPORT_NOTIFICATION_CHANNEL =
            "export_notification_channel";

    private volatile boolean mIsExporting = false;

    private Thread mExportThread;
    private LocalBroadcastManager mBroadcastManager;
    private ExportRunnable mExportRunnable;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mBroadcastManager = LocalBroadcastManager.getInstance(this);
        if (ACTION_START_EXPORTING.equals(intent.getAction())) {
            if (mIsExporting) {
                Log.e(TAG, "Already exporting an update");
                notifyExportStatusChanged(EXPORT_STATUS_ALREADY_RUNNING);
                return START_NOT_STICKY;
            }
            mIsExporting = true;
            File source = (File) intent.getSerializableExtra(EXTRA_SOURCE_FILE);
            File destination = (File) intent.getSerializableExtra(EXTRA_DEST_FILE);
            startExporting(source, destination);
        } else if (ACTION_STOP_EXPORTING.equals(intent.getAction())) {
            if (mIsExporting) {
                mExportThread.interrupt();
                stopForeground(true);
                try {
                    mExportThread.join();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error while waiting for thread");
                }
                mExportRunnable.cleanUp();
                mIsExporting = false;
            }
        } else {
            Log.e(TAG, "No action specified");
        }

        if (!mIsExporting) {
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    void notifyExportStatusChanged(int status) {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Intent intent = new Intent();
            intent.setAction(ACTION_EXPORT_STATUS);
            intent.putExtra(EXTRA_EXPORT_STATUS, status);
            mBroadcastManager.sendBroadcast(intent);
        }).start();
    }

    private void startExporting(File source, File destination) {
        notifyExportStatusChanged(EXPORT_STATUS_RUNNING);
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(
                EXPORT_NOTIFICATION_CHANNEL,
                getString(R.string.export_channel_title),
                NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(notificationChannel);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this,
                EXPORT_NOTIFICATION_CHANNEL);
        NotificationCompat.BigTextStyle notificationStyle = new NotificationCompat.BigTextStyle();
        notificationBuilder.setContentTitle(getString(R.string.dialog_export_title));
        notificationStyle.setBigContentTitle(getString(R.string.dialog_export_title));
        notificationStyle.bigText(destination.getName());
        notificationBuilder.setStyle(notificationStyle);
        notificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        notificationBuilder.addAction(R.drawable.ic_pause,
                getString(android.R.string.cancel),
                getStopPendingIntent());

        FileUtils.ProgressCallBack progressCallBack = new FileUtils.ProgressCallBack() {
            private long mLastUpdate = -1;

            @Override
            public void update(int progress) {
                long now = SystemClock.elapsedRealtime();
                if (mLastUpdate < 0 || now - mLastUpdate > 500) {
                    String percent = NumberFormat.getPercentInstance().format(progress / 100.f);
                    notificationStyle.setSummaryText(percent);
                    notificationBuilder.setProgress(100, progress, progress == 0);
                    notificationManager.notify(NOTIFICATION_ID,
                            notificationBuilder.build());
                    mLastUpdate = now;
                }
            }
        };

        startForeground(NOTIFICATION_ID, notificationBuilder.build());
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());

        @SuppressLint("RestrictedApi") Runnable runnableComplete = () -> {
            notifyExportStatusChanged(EXPORT_STATUS_SUCCESS);
            notificationStyle.setSummaryText(null);
            notificationStyle.setBigContentTitle(
                    getString(R.string.notification_export_success));
            notificationBuilder.setContentTitle(
                    getString(R.string.notification_export_success));
            notificationBuilder.setProgress(0, 0, false);
            notificationBuilder.setContentText(destination.getName());
            notificationBuilder.mActions.clear();
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            stopForeground(STOP_FOREGROUND_DETACH);
        };

        @SuppressLint("RestrictedApi") Runnable runnableFailed = () -> {
            notifyExportStatusChanged(EXPORT_STATUS_FAILED);
            notificationStyle.setSummaryText(null);
            notificationStyle.setBigContentTitle(
                    getString(R.string.notification_export_fail));
            notificationBuilder.setContentTitle(
                    getString(R.string.notification_export_fail));
            notificationBuilder.setProgress(0, 0, false);
            notificationBuilder.setContentText(null);
            notificationBuilder.mActions.clear();
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            stopForeground(STOP_FOREGROUND_DETACH);
        };

        mExportRunnable = new ExportRunnable(source, destination, progressCallBack,
                runnableComplete, runnableFailed);
        mExportThread = new Thread(mExportRunnable);
        mExportThread.start();
    }

    private PendingIntent getStopPendingIntent() {
        final Intent intent = new Intent(this, ExportUpdateService.class);
        intent.setAction(ACTION_STOP_EXPORTING);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private class ExportRunnable implements Runnable {
        private File mSource;
        private File mDestination;
        private FileUtils.ProgressCallBack mProgressCallBack;
        private Runnable mRunnableComplete;
        private Runnable mRunnableFailed;

        private ExportRunnable(File source, File destination,
                               FileUtils.ProgressCallBack progressCallBack,
                               Runnable runnableComplete, Runnable runnableFailed) {
            mSource = source;
            mDestination = destination;
            mProgressCallBack = progressCallBack;
            mRunnableComplete = runnableComplete;
            mRunnableFailed = runnableFailed;
        }

        @Override
        public void run() {
            try {
                cleanUp();
                FileUtils.copyFile(mSource, mDestination, mProgressCallBack);
                mIsExporting = false;
                if (!mExportThread.isInterrupted()) {
                    Log.d(TAG, "Completed");
                    mRunnableComplete.run();
                } else {
                    Log.d(TAG, "Aborted");
                }
            } catch (IOException e) {
                mIsExporting = false;
                Log.e(TAG, "Could not copy file", e);
                mRunnableFailed.run();
            } finally {
                stopSelf();
            }
        }

        private void cleanUp() {
            if (mDestination.exists()){
                mDestination.delete();
            }
        }
    }
}
