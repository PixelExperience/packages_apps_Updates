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
package org.pixelexperience.ota.controller;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.pixelexperience.ota.R;
import org.pixelexperience.ota.UpdaterReceiver;
import org.pixelexperience.ota.UpdatesActivity;
import org.pixelexperience.ota.misc.StringGenerator;
import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.UpdateInfo;
import org.pixelexperience.ota.model.UpdateStatus;

import java.io.IOException;
import java.text.NumberFormat;

public class UpdaterService extends Service {

    public static final String ACTION_DOWNLOAD_CONTROL = "action_download_control";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";
    public static final String EXTRA_DOWNLOAD_CONTROL = "extra_download_control";
    public static final String ACTION_INSTALL_UPDATE = "action_install_update";
    public static final String ACTION_INSTALL_STOP = "action_install_stop";

    public static final String ACTION_INSTALL_SUSPEND = "action_install_suspend";
    public static final String ACTION_INSTALL_RESUME = "action_install_resume";

    public static final int DOWNLOAD_RESUME = 0;
    public static final int DOWNLOAD_PAUSE = 1;
    private static final String TAG = "UpdaterService";
    private static final String ONGOING_NOTIFICATION_CHANNEL =
            "ongoing_notification_channel";
    private static final int NOTIFICATION_ID = 10;

    private final IBinder mBinder = new LocalBinder();
    private boolean mHasClients;

    private BroadcastReceiver mBroadcastReceiver;
    private NotificationCompat.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;
    private NotificationCompat.BigTextStyle mNotificationStyle;

    private UpdaterController mUpdaterController;

    @Override
    public void onCreate() {
        super.onCreate();

        mUpdaterController = UpdaterController.getInstance(this);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(
                ONGOING_NOTIFICATION_CHANNEL,
                getString(R.string.ongoing_channel_title),
                NotificationManager.IMPORTANCE_LOW);
        mNotificationManager.createNotificationChannel(notificationChannel);
        mNotificationBuilder = new NotificationCompat.Builder(this,
                ONGOING_NOTIFICATION_CHANNEL);
        mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
        mNotificationBuilder.setShowWhen(false);
        mNotificationStyle = new NotificationCompat.BigTextStyle();
        mNotificationBuilder.setStyle(mNotificationStyle);

        Intent notificationIntent = new Intent(this, UpdatesActivity.class);
        PendingIntent intent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        mNotificationBuilder.setContentIntent(intent);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                    Bundle extras = new Bundle();
                    extras.putString(UpdaterController.EXTRA_DOWNLOAD_ID, downloadId);
                    mNotificationBuilder.setExtras(extras);
                    handleUpdateStatusChange(update);
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction())) {
                    UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                    handleDownloadProgressChange(update);
                } else if (UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                    handleInstallProgress(update);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    mNotificationBuilder.setExtras(null);
                    mNotificationManager.cancel(NOTIFICATION_ID);
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);

    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        mHasClients = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mHasClients = false;
        tryStopSelf();
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting service");

        if (intent == null || intent.getAction() == null) {
            if (ABUpdateInstaller.isInstallingUpdate(this)) {
                new Thread(() -> {
                    // The service is being restarted.
                    ABUpdateInstaller installer = ABUpdateInstaller.getInstance(UpdaterService.this,
                            mUpdaterController);
                    installer.reconnect();
                }).start();
            }
        } else if (ACTION_DOWNLOAD_CONTROL.equals(intent.getAction())) {
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            int action = intent.getIntExtra(EXTRA_DOWNLOAD_CONTROL, -1);
            if (action == DOWNLOAD_RESUME) {
                mUpdaterController.resumeDownload(downloadId);
            } else if (action == DOWNLOAD_PAUSE) {
                mUpdaterController.pauseDownload(downloadId);
            } else {
                Log.e(TAG, "Unknown download action");
            }
        } else if (ACTION_INSTALL_UPDATE.equals(intent.getAction())) {
            String downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID);
            UpdateInfo update = mUpdaterController.getUpdate(downloadId);
            if (Utils.getPersistentStatus(this) != UpdateStatus.Persistent.VERIFIED) {
                throw new IllegalArgumentException(update.getDownloadId() + " is not verified");
            }
            try {
                if (Utils.isABUpdate(update.getFile())) {
                    ABUpdateInstaller installer = ABUpdateInstaller.getInstance(this,
                            mUpdaterController);
                    installer.install(downloadId);
                } else {
                    UpdateInstaller installer = UpdateInstaller.getInstance(this,
                            mUpdaterController);
                    installer.install(downloadId);
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not install update", e);
                mUpdaterController.getActualUpdate(downloadId)
                        .setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(downloadId, UpdateStatus.INSTALLATION_FAILED);
            }
        } else if (ACTION_INSTALL_STOP.equals(intent.getAction())) {
            if (UpdateInstaller.isInstalling()) {
                UpdateInstaller installer = UpdateInstaller.getInstance(this,
                        mUpdaterController);
                installer.cancel();
            } else if (ABUpdateInstaller.isInstallingUpdate(this)) {
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(this,
                        mUpdaterController);
                installer.reconnect();
                installer.cancel();
            }
        } else if (ACTION_INSTALL_SUSPEND.equals(intent.getAction())) {
            if (ABUpdateInstaller.isInstallingUpdate(this)) {
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(this,
                        mUpdaterController);
                installer.reconnect();
                installer.suspend();
            }
        } else if (ACTION_INSTALL_RESUME.equals(intent.getAction())) {
            if (ABUpdateInstaller.isInstallingUpdateSuspended(this)) {
                ABUpdateInstaller installer = ABUpdateInstaller.getInstance(this,
                        mUpdaterController);
                installer.reconnect();
                installer.resume();
            }
        }
        return ABUpdateInstaller.isInstallingUpdate(this) ? START_STICKY : START_NOT_STICKY;
    }

    public UpdaterController getUpdaterController() {
        return mUpdaterController;
    }

    private void tryStopSelf() {
        if (!mHasClients && !mUpdaterController.hasActiveDownloads() &&
                !mUpdaterController.isInstallingUpdate()) {
            Log.d(TAG, "Service no longer needed, stopping");
            stopSelf();
        }
    }

    private void handleUpdateStatusChange(UpdateInfo update) {
        switch (update.getStatus()) {
            case DELETED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.cancel(NOTIFICATION_ID);
                tryStopSelf();
                break;
            }
            case STARTING: {
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setProgress(0, 0, true);
                String text = getString(R.string.download_starting_notification);
                setNotificationTitle(text);
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case DOWNLOADING: {
                String text = getString(R.string.downloading_notification);
                setNotificationTitle(text);
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.addAction(R.drawable.ic_pause,
                        getString(R.string.pause_button),
                        getPausePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case PAUSED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                // In case we pause before the first progress update
                mNotificationBuilder.setProgress(100, update.getProgress(), false);
                mNotificationBuilder.mActions.clear();
                String text = getString(R.string.download_paused_notification);
                setNotificationTitle(text);
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_pause);
                mNotificationBuilder.addAction(R.drawable.ic_updateui_resume,
                        getString(R.string.resume_button),
                        getResumePendingIntent(update.getDownloadId()));
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case DOWNLOAD_ERROR: {
                stopForeground(STOP_FOREGROUND_DETACH);
                int progress = update.getProgress();
                // In case we pause before the first progress update
                mNotificationBuilder.setProgress(progress > 0 ? 100 : 0, progress, false);
                mNotificationBuilder.mActions.clear();
                String text = getString(R.string.download_paused_error_notification);
                setNotificationTitle(text);
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_warning);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case VERIFYING: {
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.cancel(NOTIFICATION_ID);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setProgress(0, 0, true);
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                String text = getString(R.string.verifying_download_notification);
                setNotificationTitle(text);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case VERIFIED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.cancel(NOTIFICATION_ID);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setStyle(null);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.download_completed_notification);
                setNotificationTitle(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case VERIFICATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setOngoing(false);
                mNotificationManager.cancel(NOTIFICATION_ID);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setStyle(null);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_warning);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.verification_failed_notification);
                setNotificationTitle(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLING: {
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = UpdateInstaller.isInstalling() ?
                        getString(R.string.dialog_prepare_zip_message) :
                        getString(R.string.installing_update);
                setNotificationTitle(text);
                if (ABUpdateInstaller.isInstallingUpdate(this)) {
                    mNotificationBuilder.addAction(R.drawable.ic_pause,
                            getString(R.string.suspend_button),
                            getSuspendInstallationPendingIntent());
                }

                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                startForeground(NOTIFICATION_ID, mNotificationBuilder.build());
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                break;
            }
            case INSTALLED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.setStyle(null);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_system_update);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.installing_update_finished);
                setNotificationTitle(text);
                mNotificationBuilder.mActions.clear();
                mNotificationBuilder.addAction(R.drawable.ic_system_update,
                        getString(R.string.reboot),
                        getRebootPendingIntent());
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLATION_FAILED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                mNotificationBuilder.setStyle(null);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_warning);
                mNotificationBuilder.setProgress(0, 0, false);
                String text = getString(R.string.installing_update_error);
                setNotificationTitle(text);
                mNotificationBuilder.setOngoing(false);
                mNotificationBuilder.setAutoCancel(true);
                mNotificationBuilder.mActions.clear();
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
            case INSTALLATION_CANCELLED: {
                stopForeground(true);
                tryStopSelf();
                break;
            }
            case INSTALLATION_SUSPENDED: {
                stopForeground(STOP_FOREGROUND_DETACH);
                // In case we pause before the first progress update
                mNotificationBuilder.setProgress(100, update.getProgress(), false);
                mNotificationBuilder.mActions.clear();
                String text = getString(R.string.installation_suspended_notification);
                setNotificationTitle(text);
                mNotificationBuilder.setStyle(mNotificationStyle);
                mNotificationBuilder.setSmallIcon(R.drawable.ic_pause);
                mNotificationBuilder.addAction(R.drawable.ic_updateui_resume,
                        getString(R.string.resume_button),
                        getResumeInstallationPendingIntent());
                mNotificationBuilder.setOngoing(true);
                mNotificationBuilder.setAutoCancel(false);
                mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
                tryStopSelf();
                break;
            }
        }
    }

    private void handleDownloadProgressChange(UpdateInfo update) {
        int progress = update.getProgress();
        mNotificationBuilder.setProgress(100, progress, false);
        String percentage = NumberFormat.getPercentInstance().format(
                progress / 100.f);
        setNotificationTitle(getString(R.string.downloading_notification) );
        mNotificationStyle.setSummaryText(percentage);
        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void handleInstallProgress(UpdateInfo update) {
        int progress = update.getInstallProgress();
        mNotificationBuilder.setProgress(100, progress, false);
        String percent = NumberFormat.getPercentInstance().format(progress / 100.f);
        boolean notAB = UpdateInstaller.isInstalling();
        setNotificationTitle(notAB ? getString(R.string.dialog_prepare_zip_message) :
                update.getFinalizing() ?
                        getString(R.string.finalizing_package) :
                        getString(R.string.preparing_ota_first_boot));
        mNotificationStyle.setSummaryText(percent);
        mNotificationManager.notify(NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void setNotificationTitle(String title) {
        mNotificationStyle.setSummaryText(null);
        mNotificationBuilder.setContentTitle(title);
    }

    private PendingIntent getResumePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_DOWNLOAD_CONTROL);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_RESUME);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getPausePendingIntent(String downloadId) {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_DOWNLOAD_CONTROL);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        intent.putExtra(EXTRA_DOWNLOAD_CONTROL, DOWNLOAD_PAUSE);
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getRebootPendingIntent() {
        final Intent intent = new Intent(this, UpdaterReceiver.class);
        intent.setAction(UpdaterReceiver.ACTION_INSTALL_REBOOT);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getSuspendInstallationPendingIntent() {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_INSTALL_SUSPEND);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent getResumeInstallationPendingIntent() {
        final Intent intent = new Intent(this, UpdaterService.class);
        intent.setAction(ACTION_INSTALL_RESUME);
        return PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public class LocalBinder extends Binder {
        public UpdaterService getService() {
            return UpdaterService.this;
        }
    }
}
