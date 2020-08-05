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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UpdaterController {

    public static final String ACTION_DOWNLOAD_PROGRESS = "action_download_progress";
    public static final String ACTION_INSTALL_PROGRESS = "action_install_progress";
    public static final String ACTION_UPDATE_REMOVED = "action_update_removed";
    public static final String ACTION_UPDATE_STATUS = "action_update_status_change";
    public static final String ACTION_NETWORK_UNAVAILABLE = "action_network_unavailable";
    public static final String ACTION_UPDATE_CLEANUP_IN_PROGRESS = "action_incremental_pref_changing";
    public static final String ACTION_UPDATE_CLEANUP_DONE = "action_incremental_pref_changed";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";
    public static final String EXTRA_STATUS = "extra_status";
    private static final int MAX_REPORT_INTERVAL_MS = 1000;
    @SuppressLint("StaticFieldLeak")
    private static UpdaterController sUpdaterController;
    private final String TAG = "UpdaterController";
    private final Context mContext;
    private final LocalBroadcastManager mBroadcastManager;

    private final PowerManager.WakeLock mWakeLock;

    private final File mDownloadRoot;

    private int mActiveDownloads = 0;
    private Set<String> mVerifyingUpdates = new HashSet<>();
    private Map<String, DownloadEntry> mDownloads = new HashMap<>();

    private UpdaterController(Context context) {
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mDownloadRoot = Utils.getDownloadPath();
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Updates:UpdaterController");
        mWakeLock.setReferenceCounted(false);
        mContext = context.getApplicationContext();

        Utils.cleanupDownloadsDir(context);
    }

    static synchronized UpdaterController getInstance(Context context) {
        if (sUpdaterController == null) {
            sUpdaterController = new UpdaterController(context);
        }
        return sUpdaterController;
    }

    void notifyUpdateChange(String downloadId, UpdateStatus status) {
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Intent intent = new Intent();
            intent.setAction(ACTION_UPDATE_STATUS);
            intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
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

    private void notifyUpdateDelete(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_REMOVED);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mBroadcastManager.sendBroadcast(intent);
    }

    private void notifyDownloadProgress(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
        mBroadcastManager.sendBroadcast(intent);
    }

    void notifyInstallProgress(String downloadId) {
        Intent intent = new Intent();
        intent.setAction(ACTION_INSTALL_PROGRESS);
        intent.putExtra(EXTRA_DOWNLOAD_ID, downloadId);
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
        mActiveDownloads++;
    }

    private void removeDownloadClient(DownloadEntry entry) {
        if (entry.mDownloadClient == null) {
            return;
        }
        entry.mDownloadClient = null;
        mActiveDownloads--;
    }

    private DownloadClient.DownloadCallback getDownloadCallback(final String downloadId) {
        return new DownloadClient.DownloadCallback() {

            @Override
            public void onResponse(int statusCode, String url, DownloadClient.Headers headers) {
                final Update update = mDownloads.get(downloadId).mUpdate;
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
                update.setPersistentStatus(UpdateStatus.Persistent.INCOMPLETE);
                notifyUpdateChange(downloadId, UpdateStatus.DOWNLOADING);
            }

            @Override
            public void onSuccess(File destination) {
                Log.d(TAG, "Download complete");
                Update update = mDownloads.get(downloadId).mUpdate;
                update.setStatus(UpdateStatus.VERIFYING);
                removeDownloadClient(mDownloads.get(downloadId));
                verifyUpdateAsync(downloadId);
                notifyUpdateChange(downloadId, UpdateStatus.VERIFYING);
                tryReleaseWakelock();
            }

            @Override
            public void onFailure(boolean cancelled) {
                Update update = mDownloads.get(downloadId).mUpdate;
                if (cancelled) {
                    Log.d(TAG, "Download cancelled");
                    // Already notified
                } else {
                    Log.e(TAG, "Download failed");
                    removeDownloadClient(mDownloads.get(downloadId));
                    update.setStatus(UpdateStatus.DOWNLOAD_ERROR);
                    notifyUpdateChange(downloadId, UpdateStatus.DOWNLOAD_ERROR);
                    cleanupUpdates();
                }
                tryReleaseWakelock();
            }
        };
    }

    private DownloadClient.ProgressListener getProgressListener(final String downloadId) {
        return new DownloadClient.ProgressListener() {
            private long mLastUpdate = 0;
            private int mProgress = 0;

            @Override
            public void update(long bytesRead, long contentLength, long speed, long eta,
                               boolean done) {
                Update update = mDownloads.get(downloadId).mUpdate;
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
                    notifyDownloadProgress(downloadId);
                }
            }
        };
    }

    @SuppressLint("SetWorldReadable")
    private void verifyUpdateAsync(final String downloadId) {
        mVerifyingUpdates.add(downloadId);
        new Thread(() -> {
            Update update = mDownloads.get(downloadId).mUpdate;
            File file = update.getFile();
            UpdateStatus status = null;
            if (file.exists() && verifyPackage(file, update.getHash())) {
                file.setReadable(true, false);
                update.setPersistentStatus(UpdateStatus.Persistent.VERIFIED);
                status = UpdateStatus.VERIFIED;
            } else {
                update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
                update.setProgress(0);
                status = UpdateStatus.VERIFICATION_FAILED;
            }
            update.setStatus(status);
            mVerifyingUpdates.remove(downloadId);
            notifyUpdateChange(downloadId, status);
        }).start();
    }

    private boolean verifyPackage(File file, String hash) {
        try {
            if (Utils.calculateMD5(file).equals(hash)) {
                Log.e(TAG, "Verification successful");
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

    private boolean fixUpdateStatus(Update update) {
        switch (update.getPersistentStatus()) {
            case UpdateStatus.Persistent.VERIFIED:
            case UpdateStatus.Persistent.INCOMPLETE:
                if (update.getFile() == null || !update.getFile().exists()) {
                    update.setStatus(UpdateStatus.UNKNOWN);
                    return false;
                } else if (update.getFileSize() > 0) {
                    update.setStatus(UpdateStatus.PAUSED);
                    int progress = Math.round(
                            update.getFile().length() * 100 / update.getFileSize());
                    update.setProgress(progress);
                }
                break;
        }
        return true;
    }

    public boolean addUpdate(UpdateInfo update) {
        return addUpdate(update, true);
    }

    private boolean addUpdate(final UpdateInfo updateInfo, boolean availableOnline) {
        if (mDownloads.containsKey(updateInfo.getDownloadId())) {
            Log.d(TAG, "Download (" + updateInfo.getDownloadId() + ") already added");
            Update updateAdded = mDownloads.get(updateInfo.getDownloadId()).mUpdate;
            updateAdded.setAvailableOnline(availableOnline && updateAdded.getAvailableOnline());
            updateAdded.setDownloadUrl(updateInfo.getDownloadUrl());
            return false;
        }
        Log.d(TAG, "Adding download: " + updateInfo.getDownloadId());
        Update update = new Update(updateInfo);
        if (!fixUpdateStatus(update) && !availableOnline) {
            update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
            deleteUpdateAsync(update);
            Log.d(TAG, update.getDownloadId() + " had an invalid status and is not online");
            return false;
        }
        update.setAvailableOnline(availableOnline);
        mDownloads.clear();
        mDownloads.put(update.getDownloadId(), new DownloadEntry(update));
        return true;
    }

    public void startDownload(String downloadId) {
        Log.d(TAG, "Starting " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        Update update = mDownloads.get(downloadId).mUpdate;
        File destination = new File(mDownloadRoot, update.getName());
        if (destination.exists()) {
            destination = Utils.appendSequentialNumber(destination);
            Log.d(TAG, "Changing name with " + destination.getName());
        }
        update.setFile(destination);
        DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(update.getDownloadUrl())
                    .setDestination(update.getFile())
                    .setDownloadCallback(getDownloadCallback(downloadId))
                    .setProgressListener(getProgressListener(downloadId))
                    .setUseDuplicateLinks(true)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            update.setStatus(UpdateStatus.DOWNLOAD_ERROR);
            notifyUpdateChange(downloadId, UpdateStatus.DOWNLOAD_ERROR);
            cleanupUpdates();
            return;
        }
        addDownloadClient(mDownloads.get(downloadId), downloadClient);
        update.setStatus(UpdateStatus.STARTING);
        notifyUpdateChange(downloadId, UpdateStatus.STARTING);
        downloadClient.start();
        mWakeLock.acquire();
    }

    public void resumeDownload(String downloadId) {
        Log.d(TAG, "Resuming " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        Update update = mDownloads.get(downloadId).mUpdate;
        File file = update.getFile();
        if (file == null || !file.exists()) {
            Log.e(TAG, "The destination file of " + downloadId + " doesn't exist, can't resume");
            update.setStatus(UpdateStatus.DOWNLOAD_ERROR);
            notifyUpdateChange(downloadId, UpdateStatus.DOWNLOAD_ERROR);
            cleanupUpdates();
            return;
        }
        if (file.exists() && update.getFileSize() > 0 && file.length() >= update.getFileSize()) {
            Log.d(TAG, "File already downloaded, starting verification");
            update.setStatus(UpdateStatus.VERIFYING);
            verifyUpdateAsync(downloadId);
            notifyUpdateChange(downloadId, UpdateStatus.VERIFYING);
        } else {
            DownloadClient downloadClient;
            try {
                downloadClient = new DownloadClient.Builder()
                        .setUrl(update.getDownloadUrl())
                        .setDestination(update.getFile())
                        .setDownloadCallback(getDownloadCallback(downloadId))
                        .setProgressListener(getProgressListener(downloadId))
                        .setUseDuplicateLinks(true)
                        .build();
            } catch (IOException exception) {
                Log.e(TAG, "Could not build download client");
                update.setStatus(UpdateStatus.DOWNLOAD_ERROR);
                notifyUpdateChange(downloadId, UpdateStatus.DOWNLOAD_ERROR);
                cleanupUpdates();
                return;
            }
            addDownloadClient(mDownloads.get(downloadId), downloadClient);
            update.setStatus(UpdateStatus.STARTING);
            notifyUpdateChange(downloadId, UpdateStatus.STARTING);
            downloadClient.resume();
            mWakeLock.acquire();
        }
    }

    public boolean pauseDownload(String downloadId) {
        Log.d(TAG, "Pausing " + downloadId);
        if (!isDownloading(downloadId)) {
            return false;
        }

        DownloadEntry entry = mDownloads.get(downloadId);
        entry.mDownloadClient.cancel();
        removeDownloadClient(entry);
        entry.mUpdate.setStatus(UpdateStatus.PAUSED);
        entry.mUpdate.setEta(0);
        entry.mUpdate.setSpeed(0);
        notifyUpdateChange(downloadId, UpdateStatus.PAUSED);
        return true;
    }

    private void deleteUpdateAsync(final Update update) {
        new Thread(() -> {
            File file = update.getFile();
            if (file != null && file.exists() && !file.delete()) {
                Log.e(TAG, "Could not delete " + file.getAbsolutePath());
            }
        }).start();
    }

    private void deleteUpdate(final Update update) {
        File file = update.getFile();
        if (file != null && file.exists() && !file.delete()) {
            Log.e(TAG, "Could not delete " + file.getAbsolutePath());
        }
    }

    public void deleteUpdate(String downloadId) {
        Log.d(TAG, "Cancelling " + downloadId);
        if (!mDownloads.containsKey(downloadId) || isDownloading(downloadId)) {
            return;
        }
        Update update = mDownloads.get(downloadId).mUpdate;
        update.setStatus(UpdateStatus.DELETED);
        update.setProgress(0);
        update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
        deleteUpdateAsync(update);

        if (!update.getAvailableOnline()) {
            Log.d(TAG, "Download no longer available online, removing");
            mDownloads.remove(downloadId);
            notifyUpdateDelete(downloadId);
        } else {
            notifyUpdateChange(downloadId, UpdateStatus.DELETED);
        }

    }

    public void setShouldUseIncremental(boolean shouldUse) {
        Intent intent = new Intent();
        intent.setAction(ACTION_UPDATE_CLEANUP_IN_PROGRESS);
        mBroadcastManager.sendBroadcast(intent);
        Utils.setShouldUseIncremental(mContext, shouldUse);
        new Thread(() -> {
            for (String id : mDownloads.keySet()) {
                try {
                    Update update = mDownloads.get(id).mUpdate;
                    update.setStatus(UpdateStatus.DELETED);
                    update.setProgress(0);
                    update.setPersistentStatus(UpdateStatus.Persistent.UNKNOWN);
                    deleteUpdate(update);
                } catch (Exception ignored) {
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            intent.setAction(ACTION_UPDATE_CLEANUP_DONE);
            mBroadcastManager.sendBroadcast(intent);
        }).start();
    }

    public void cleanupUpdates() {
        setShouldUseIncremental(Utils.shouldUseIncremental(mContext));
    }

    public List<UpdateInfo> getUpdates() {
        List<UpdateInfo> updates = new ArrayList<>();
        for (DownloadEntry entry : mDownloads.values()) {
            if (Utils.isCompatible(entry.mUpdate)) {
                updates.add(entry.mUpdate);
            }
        }
        return updates;
    }

    public UpdateInfo getUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? entry.mUpdate : null;
    }

    Update getActualUpdate(String downloadId) {
        DownloadEntry entry = mDownloads.get(downloadId);
        return entry != null ? entry.mUpdate : null;
    }

    public boolean isDownloading(String downloadId) {
        return mDownloads.containsKey(downloadId) &&
                mDownloads.get(downloadId).mDownloadClient != null;
    }

    public boolean hasActiveDownloads() {
        return mActiveDownloads > 0;
    }

    public boolean isVerifyingUpdate() {
        return mVerifyingUpdates.size() > 0;
    }

    public boolean isVerifyingUpdate(String downloadId) {
        return mVerifyingUpdates.contains(downloadId);
    }

    public boolean isInstallingUpdate() {
        return UpdateInstaller.isInstalling() ||
                ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    public boolean isInstallingUpdate(String downloadId) {
        return UpdateInstaller.isInstalling(downloadId) ||
                ABUpdateInstaller.isInstallingUpdate(mContext, downloadId);
    }

    public boolean isInstallingABUpdate() {
        return ABUpdateInstaller.isInstallingUpdate(mContext);
    }

    public boolean isWaitingForReboot(String downloadId) {
        return ABUpdateInstaller.isWaitingForReboot(mContext, downloadId);
    }

    private class DownloadEntry {
        final Update mUpdate;
        DownloadClient mDownloadClient;

        private DownloadEntry(Update update) {
            mUpdate = update;
        }
    }
}
