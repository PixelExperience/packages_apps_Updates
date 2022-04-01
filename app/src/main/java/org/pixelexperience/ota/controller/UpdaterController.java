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
import java.util.Objects;

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
    private final DownloadInfo mDownloadInfo = new DownloadInfo();
    private final InstallInfo mInstallInfo = new InstallInfo();

    private final PowerManager.WakeLock mWakeLock;

    private final File mDownloadRoot;

    private boolean mVerifyingUpdate = false;
    private final DownloadEntry mDownloadEntry = new DownloadEntry();

    private UpdaterController(Context context) {
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mDownloadRoot = Utils.getDownloadPath();
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Updates:UpdaterController");
        mWakeLock.setReferenceCounted(false);
        mContext = context.getApplicationContext();
    }

    public static synchronized UpdaterController getInstance(Context context) {
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
                String contentLength = headers.get("Content-Length");
                if (contentLength != null) {
                    try {
                        long size = Long.parseLong(contentLength);
                        if (mDownloadEntry.mUpdate.getFileSize() < size) {
                            mDownloadEntry.mUpdate.setFileSize(size);
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Could not get content-length");
                    }
                }
                mDownloadEntry.mUpdate.setStatus(UpdateStatus.DOWNLOADING);
                Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.DOWNLOADING);
                notifyUpdateChange(UpdateStatus.DOWNLOADING);
            }

            @Override
            public void onSuccess(File destination) {
                Log.d(TAG, "Download complete");
                mDownloadEntry.mUpdate.setStatus(UpdateStatus.VERIFYING);
                removeDownloadClient(mDownloadEntry);
                verifyUpdateAsync();
                notifyUpdateChange(UpdateStatus.VERIFYING);
                tryReleaseWakelock();
            }

            @Override
            public void onFailure(boolean cancelled) {
                if (cancelled) {
                    Log.d(TAG, "Download cancelled");
                    // Already notified
                } else {
                    Log.e(TAG, "Download failed");
                    removeDownloadClient(mDownloadEntry);
                    mDownloadEntry.mUpdate.setStatus(UpdateStatus.DOWNLOAD_ERROR);
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
                if (contentLength <= 0) {
                    if (mDownloadEntry.mUpdate.getFileSize() <= 0) {
                        return;
                    } else {
                        contentLength = mDownloadEntry.mUpdate.getFileSize();
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
                    mDownloadInfo.setProgress(progress);
                    mDownloadInfo.setEta(eta);
                    notifyDownloadProgress();
                }
            }
        };
    }

    public DownloadInfo getDownloadInfo() {
        return mDownloadInfo;
    }

    public InstallInfo getInstallInfo() {
        return mInstallInfo;
    }

    @SuppressLint("SetWorldReadable")
    private void verifyUpdateAsync() {
        mVerifyingUpdate = true;
        new Thread(() -> {
            File file = mDownloadEntry.mUpdate.getFile();
            UpdateStatus status;
            if (file.exists() && verifyPackage(file, mDownloadEntry.mUpdate.getHash())) {
                file.setReadable(true, false);
                Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.VERIFIED);
                status = UpdateStatus.VERIFIED;
            } else {
                Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.UNKNOWN);
                resetDownloadInfo();
                status = UpdateStatus.VERIFICATION_FAILED;
            }
            mDownloadEntry.mUpdate.setStatus(status);
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

    public void addUpdate(final UpdateInfo updateInfo) {
        if (mDownloadEntry.isValid()) {
            Log.d(TAG, "Download (" + updateInfo.getDownloadId() + ") already added");
        } else {
            Log.d(TAG, "Adding download: " + updateInfo.getDownloadId());
        }
        Update update = new Update(updateInfo);
        mDownloadEntry.setUpdate(update);
        if (Utils.getPersistentStatus(mContext) == UpdateStatus.Persistent.VERIFIED) {
            File destination = new File(mDownloadRoot, update.getName());
            if (destination.exists()){
                update.setFile(destination);
                if(Utils.isABDevice() && (isInstallingABUpdate() || update.getDownloadId().equals(Update.LOCAL_ID))){
                    update.setStatus(UpdateStatus.INSTALLING);
                }else{
                    verifyUpdateAsync();
                    Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.DOWNLOADING);
                    update.setStatus(UpdateStatus.DOWNLOADED);
                    notifyUpdateChange(UpdateStatus.VERIFYING);
                }
            }else{
                update.setStatus(UpdateStatus.UNKNOWN);
                Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.UNKNOWN);
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
            Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.UNKNOWN);
        }
        notifyUpdateChange(update.getStatus());
    }

    private void resetDownloadInfo() {
        mDownloadInfo.setProgress(0);
        mDownloadInfo.setEta(0);
    }

    public void startDownload() {
        if (isDownloading()) {
            Log.d(TAG, "Already started");
            return;
        }
        Log.d(TAG, "Starting download");
        resetDownloadInfo();
        Utils.cleanupDownloadsDir(mContext);
        File destination = new File(mDownloadRoot, mDownloadEntry.mUpdate.getName());
        mDownloadEntry.mUpdate.setFile(destination);
        DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(mDownloadEntry.mUpdate.getDownloadUrl())
                    .setDestination(mDownloadEntry.mUpdate.getFile())
                    .setDownloadCallback(getDownloadCallback())
                    .setProgressListener(getProgressListener())
                    .setUseDuplicateLinks(true)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            mDownloadEntry.mUpdate.setStatus(UpdateStatus.DOWNLOAD_ERROR);
            notifyUpdateChange(UpdateStatus.DOWNLOAD_ERROR);
            removeUpdate(true);
            return;
        }
        addDownloadClient(mDownloadEntry, downloadClient);
        Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.STARTING_DOWNLOAD);
        mDownloadEntry.mUpdate.setStatus(UpdateStatus.STARTING);
        notifyUpdateChange(UpdateStatus.STARTING);
        downloadClient.start();
        mWakeLock.acquire();
    }

    public void setStatus(UpdateStatus status) {
        mDownloadEntry.mUpdate.setStatus(status);
    }

    public void resumeDownload() {
        if (isDownloading()) {
            Log.d(TAG, "Already downloading");
            return;
        }
        Log.d(TAG, "Resuming download");
        File file = mDownloadEntry.mUpdate.getFile();
        if (file == null || !file.exists()) {
            Log.e(TAG, "The destination file doesn't exist, can't resume");
            mDownloadEntry.mUpdate.setStatus(UpdateStatus.DOWNLOAD_ERROR);
            notifyUpdateChange(UpdateStatus.DOWNLOAD_ERROR);
            removeUpdate(true);
            return;
        }
        if (file.exists() && mDownloadEntry.mUpdate.getFileSize() > 0 && file.length() >= mDownloadEntry.mUpdate.getFileSize()) {
            Log.d(TAG, "File already downloaded, starting verification");
            mDownloadEntry.mUpdate.setStatus(UpdateStatus.VERIFYING);
            verifyUpdateAsync();
            notifyUpdateChange(UpdateStatus.VERIFYING);
        } else {
            DownloadClient downloadClient;
            try {
                downloadClient = new DownloadClient.Builder()
                        .setUrl(mDownloadEntry.mUpdate.getDownloadUrl())
                        .setDestination(mDownloadEntry.mUpdate.getFile())
                        .setDownloadCallback(getDownloadCallback())
                        .setProgressListener(getProgressListener())
                        .setUseDuplicateLinks(true)
                        .build();
            } catch (IOException exception) {
                Log.e(TAG, "Could not build download client");
                mDownloadEntry.mUpdate.setStatus(UpdateStatus.DOWNLOAD_ERROR);
                notifyUpdateChange(UpdateStatus.DOWNLOAD_ERROR);
                removeUpdate(true);
                return;
            }
            addDownloadClient(mDownloadEntry, downloadClient);
            Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.STARTING_DOWNLOAD);
            mDownloadEntry.mUpdate.setStatus(UpdateStatus.STARTING);
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
        mDownloadInfo.setEta(0);
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

    public class DownloadInfo {
        long eta;
        int progress;

        public DownloadInfo() {
        }

        public long getEta() {
            return eta;
        }

        public void setEta(long eta) {
            this.eta = eta;
        }

        public int getProgress() {
            return progress;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }
    }

    public class InstallInfo {
        int progress;
        boolean finalizing;

        public InstallInfo() {
        }

        public int getProgress() {
            return progress;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }

        public boolean isFinalizing() {
            return finalizing;
        }

        public void setFinalizing(boolean finalizing) {
            this.finalizing = finalizing;
        }
    }
}
