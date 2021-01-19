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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.pixelexperience.ota.download.DownloadClient;
import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.Update;
import org.pixelexperience.ota.model.UpdateInfo;
import org.pixelexperience.ota.model.UpdateStatus;

import java.io.File;
import java.io.IOException;

public class UpdaterController {

    public static final String ACTION_DOWNLOAD_PROGRESS = "action_download_progress";
    public static final String ACTION_INSTALL_PROGRESS = "action_install_progress";
    public static final String ACTION_UPDATE_REMOVED = "action_update_removed";
    public static final String ACTION_UPDATE_STATUS = "action_update_status_change";
    public static final String ACTION_NETWORK_UNAVAILABLE = "action_network_unavailable";
    public static final String EXTRA_STATUS = "extra_status";
    private static final int MAX_REPORT_INTERVAL_MS = 1000;
    @SuppressLint("StaticFieldLeak")
    private static UpdaterController sUpdaterController;
    private final String TAG = "UpdaterController";
    private final Context mContext;
    private final LocalBroadcastManager mBroadcastManager;

    private final PowerManager.WakeLock mWakeLock;

    private final File mDownloadRoot;

    private boolean mVerifyingUpdate = false;
    private DownloadEntry mDownloadEntry = new DownloadEntry();

    private UpdaterController(Context context) {
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mDownloadRoot = Utils.getDownloadPath();
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Updates:UpdaterController");
        mWakeLock.setReferenceCounted(false);
        mContext = context.getApplicationContext();
    }

    static synchronized UpdaterController getInstance(Context context) {
        if (sUpdaterController == null) {
            sUpdaterController = new UpdaterController(context);
        }
        return sUpdaterController;
    }

    void notifyUpdateChange(UpdateStatus status) {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Intent intent = new Intent();
            intent.setAction(ACTION_UPDATE_STATUS);
            intent.putExtra(EXTRA_STATUS, status);
            mBroadcastManager.sendBroadcast(intent);
        }).start();
    }

    public void notifyNetworkUnavailable() {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Intent intent = new Intent();
            intent.setAction(ACTION_NETWORK_UNAVAILABLE);
            mBroadcastManager.sendBroadcast(intent);
        }).start();
    }

    private void notifyUpdateDelete() {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_REMOVED);
        mBroadcastManager.sendBroadcast(intent);
    }

    private void notifyDownloadProgress() {
        Intent intent = new Intent();
        intent.setAction(ACTION_DOWNLOAD_PROGRESS);
        mBroadcastManager.sendBroadcast(intent);
    }

    void notifyInstallProgress() {
        Intent intent = new Intent();
        intent.setAction(ACTION_INSTALL_PROGRESS);
        mBroadcastManager.sendBroadcast(intent);
    }

    private void tryReleaseWakelock() {
        if (!hasActiveDownloads()) {
            mWakeLock.release();
        }
    }

    private void addDownloadClient(DownloadEntry entry, DownloadClient downloadClient) {
        if (entry.mDownloadClient != null) {
            return;
        }
        entry.mDownloadClient = downloadClient;
    }

    private void removeDownloadClient(DownloadEntry entry) {
        if (entry.mDownloadClient == null) {
            return;
        }
        entry.mDownloadClient = null;
    }

    private DownloadClient.DownloadCallback getDownloadCallback() {
        return new DownloadClient.DownloadCallback() {

            @Override
            public void onResponse(int statusCode, String url, DownloadClient.Headers headers) {
                final Update update = mDownloadEntry.mUpdate;
                String contentLength = headers.get("Content-Length");
                if (contentLength != null) {
                    try {
                        long size = Long.parseLong(contentLength);
                        if (update.getFileSize() < size) {
                            update.setFileSize(size);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Could not get content-length");
                    }
                }
                update.setStatus(UpdateStatus.DOWNLOADING);
                Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.DOWNLOADING);
                notifyUpdateChange(UpdateStatus.DOWNLOADING);
            }

            @Override
            public void onSuccess(File destination) {
                Log.d(TAG, "Download complete");
                Update update = mDownloadEntry.mUpdate;
                update.setStatus(UpdateStatus.VERIFYING);
                removeDownloadClient(mDownloadEntry);
                verifyUpdateAsync();
                notifyUpdateChange(UpdateStatus.VERIFYING);
                tryReleaseWakelock();
            }

            @Override
            public void onFailure(boolean cancelled) {
                Update update = mDownloadEntry.mUpdate;
                if (cancelled) {
                    Log.d(TAG, "Download cancelled");
                    // Already notified
                } else {
                    Log.e(TAG, "Download failed");
                    removeDownloadClient(mDownloadEntry);
                    update.setStatus(UpdateStatus.DOWNLOAD_ERROR);
                    notifyUpdateChange(UpdateStatus.DOWNLOAD_ERROR);
                    removeUpdate(true);
                }
                tryReleaseWakelock();
            }
        };
    }

    private DownloadClient.ProgressListener getProgressListener() {
        return new DownloadClient.ProgressListener() {
            private long mLastUpdate = 0;
            private int mProgress = 0;

            @Override
            public void update(long bytesRead, long contentLength, long speed, long eta,
                               boolean done) {
                Update update = mDownloadEntry.mUpdate;
                if (contentLength <= 0) {
                    if (update.getFileSize() <= 0) {
                        return;
                    } else {
                        contentLength = update.getFileSize();
                    }
                }
                if (contentLength <= 0) {
                    return;
                }
                final long now = SystemClock.elapsedRealtime();
                int progress = Math.round(bytesRead * 100 / contentLength);
                if (progress != mProgress || mLastUpdate - now > MAX_REPORT_INTERVAL_MS) {
                    mProgress = progress;
                    mLastUpdate = now;
                    update.setProgress(progress);
                    update.setEta(eta);
                    update.setSpeed(speed);
                    notifyDownloadProgress();
                }
            }
        };
    }

    @SuppressLint("SetWorldReadable")
    private void verifyUpdateAsync() {
        mVerifyingUpdate = true;
        new Thread(() -> {
            Update update = mDownloadEntry.mUpdate;
            File file = update.getFile();
            UpdateStatus status;
            if (file.exists() && verifyPackage(file, update.getHash())) {
                file.setReadable(true, false);
                Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.VERIFIED);
                status = UpdateStatus.VERIFIED;
            } else {
                Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.UNKNOWN);
                update.setProgress(0);
                status = UpdateStatus.VERIFICATION_FAILED;
            }
            update.setStatus(status);
            mVerifyingUpdate = false;
            notifyUpdateChange(status);
        }).start();
    }

    private boolean verifyPackage(File file, String hash) {
        try {
            if (Utils.calculateMD5(file).equals(hash)) {
                Log.d(TAG, "Verification successful");
                return true;
            } else {
                throw new Exception("MD5 mismatch");
            }
        } catch (Exception e) {
            Log.e(TAG, "Verification failed", e);
            if (file.exists()) {
                file.delete();
            } else {
                // The download was probably stopped. Exit silently
                Log.e(TAG, "Error while verifying the file", e);
            }
            return false;
        }
    }

    public boolean addUpdate(final UpdateInfo updateInfo) {
        boolean alreadyExists = false;
        if (mDownloadEntry.isValid()) {
            Log.d(TAG, "Download (" + updateInfo.getDownloadId() + ") already added");
            alreadyExists = true;
        } else {
            Log.d(TAG, "Adding download: " + updateInfo.getDownloadId());
        }
        Update update = new Update(updateInfo);
        mDownloadEntry.setUpdate(update);
        if (Utils.getPersistentStatus(mContext) == UpdateStatus.Persistent.VERIFIED) {
            File destination = new File(mDownloadRoot, update.getName());
            update.setFile(destination);
            if(Utils.isABDevice() && isInstallingABUpdate()){
                update.setStatus(UpdateStatus.INSTALLING);
            }else{
                update.setStatus(UpdateStatus.VERIFIED);
            }
        }else if (Utils.getPersistentStatus(mContext) == UpdateStatus.Persistent.DOWNLOADING && isDownloading()) {
            File destination = new File(mDownloadRoot, update.getName());
            update.setFile(destination);
            update.setStatus(UpdateStatus.DOWNLOADING);
        } else if (Utils.getPersistentStatus(mContext) == UpdateStatus.Persistent.STARTING_DOWNLOAD && isDownloading()) {
            File destination = new File(mDownloadRoot, update.getName());
            update.setFile(destination);
            update.setStatus(UpdateStatus.STARTING);
        } else {
            update.setStatus(UpdateStatus.UNKNOWN);
            Utils.cleanupDownloadsDir(mContext);
            Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.UNKNOWN);
        }
        notifyUpdateChange(update.getStatus());
        return !alreadyExists;
    }

    public void startDownload() {
        if (isDownloading()) {
            Log.d(TAG, "Already started");
            return;
        }
        Log.d(TAG, "Starting download");
        Utils.cleanupDownloadsDir(mContext);
        Update update = mDownloadEntry.mUpdate;
        File destination = new File(mDownloadRoot, update.getName());
        update.setFile(destination);
        DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(update.getDownloadUrl())
                    .setDestination(update.getFile())
                    .setDownloadCallback(getDownloadCallback())
                    .setProgressListener(getProgressListener())
                    .setUseDuplicateLinks(true)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            update.setStatus(UpdateStatus.DOWNLOAD_ERROR);
            notifyUpdateChange(UpdateStatus.DOWNLOAD_ERROR);
            removeUpdate(true);
            return;
        }
        addDownloadClient(mDownloadEntry, downloadClient);
        Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.STARTING_DOWNLOAD);
        update.setStatus(UpdateStatus.STARTING);
        notifyUpdateChange(UpdateStatus.STARTING);
        downloadClient.start();
        mWakeLock.acquire();
    }

    public void resumeDownload() {
        if (isDownloading()) {
            Log.d(TAG, "Already downloading");
            return;
        }
        Log.d(TAG, "Resuming download");
        Update update = mDownloadEntry.mUpdate;
        File file = update.getFile();
        if (file == null || !file.exists()) {
            Log.e(TAG, "The destination file doesn't exist, can't resume");
            update.setStatus(UpdateStatus.DOWNLOAD_ERROR);
            notifyUpdateChange(UpdateStatus.DOWNLOAD_ERROR);
            removeUpdate(true);
            return;
        }
        if (file.exists() && update.getFileSize() > 0 && file.length() >= update.getFileSize()) {
            Log.d(TAG, "File already downloaded, starting verification");
            update.setStatus(UpdateStatus.VERIFYING);
            verifyUpdateAsync();
            notifyUpdateChange(UpdateStatus.VERIFYING);
        } else {
            DownloadClient downloadClient;
            try {
                downloadClient = new DownloadClient.Builder()
                        .setUrl(update.getDownloadUrl())
                        .setDestination(update.getFile())
                        .setDownloadCallback(getDownloadCallback())
                        .setProgressListener(getProgressListener())
                        .setUseDuplicateLinks(true)
                        .build();
            } catch (IOException exception) {
                Log.e(TAG, "Could not build download client");
                update.setStatus(UpdateStatus.DOWNLOAD_ERROR);
                notifyUpdateChange(UpdateStatus.DOWNLOAD_ERROR);
                removeUpdate(true);
                return;
            }
            addDownloadClient(mDownloadEntry, downloadClient);
            Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.STARTING_DOWNLOAD);
            update.setStatus(UpdateStatus.STARTING);
            notifyUpdateChange(UpdateStatus.STARTING);
            downloadClient.resume();
            mWakeLock.acquire();
        }
    }

    public boolean pauseDownload() {
        if (!isDownloading()) {
            Log.d(TAG, "Not downloading");
            return false;
        }
        Log.d(TAG, "Pausing download");
        mDownloadEntry.mDownloadClient.cancel();
        removeDownloadClient(mDownloadEntry);
        mDownloadEntry.mUpdate.setStatus(UpdateStatus.PAUSED);
        mDownloadEntry.mUpdate.setEta(0);
        mDownloadEntry.mUpdate.setSpeed(0);
        notifyUpdateChange(UpdateStatus.PAUSED);
        return true;
    }

    public void removeUpdate(boolean cleanupLocalOnly) {
        Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.UNKNOWN);
        Utils.cleanupDownloadsDir(mContext);
        if (!cleanupLocalOnly){
            mDownloadEntry.mUpdate = null;
        }
    }

    public void removeUpdateAndNotify() {
        removeUpdate(false);
        notifyUpdateDelete();
        notifyUpdateChange(UpdateStatus.UNKNOWN);
    }

    public Update getCurrentUpdate() {
        return mDownloadEntry.mUpdate;
    }

    public boolean isDownloading() {
        return mDownloadEntry.isValid() &&
                hasActiveDownloads();
    }

    public boolean hasActiveDownloads() {
        return mDownloadEntry.mDownloadClient != null;
    }

    public boolean isVerifyingUpdate() {
        return mVerifyingUpdate;
    }

    public boolean isInstallingUpdate() {
        return UpdateInstaller.isInstalling() ||
                ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    public boolean isInstallingABUpdate() {
        return ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    private class DownloadEntry {
        Update mUpdate;
        DownloadClient mDownloadClient;

        private DownloadEntry() {
        }

        private void setUpdate(Update update) {
            mUpdate = update;
        }

        private boolean isValid() {
            return mUpdate != null;
        }
    }
}
