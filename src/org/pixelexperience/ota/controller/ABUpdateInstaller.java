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
import android.content.SharedPreferences;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.text.TextUtils;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.Update;
import org.pixelexperience.ota.model.UpdateStatus;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ABUpdateInstaller {

    private static final String TAG = "ABUpdateInstaller";

    private static final String PREF_INSTALLING_AB_ID = "installing_ab_id";
    private static final String PREF_INSTALLING_SUSPENDED_AB_ID = "installing_suspended_ab_id";

    @SuppressLint("StaticFieldLeak")
    private static ABUpdateInstaller sInstance = null;

    private final UpdaterController mUpdaterController;
    private final Context mContext;
    private String mDownloadId;

    private boolean mFinalizing;
    private int mProgress;
    private boolean mCanceling = false;

    private final UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback() {

        @Override
        public void onStatusUpdate(int status, float percent) {
            Update update = mUpdaterController.getActualUpdate(mDownloadId);
            if (update == null) {
                // We read the id from a preference, the update could no longer exist
                installationDone(status == UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT);
                return;
            }

            switch (status) {
                case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
                case UpdateEngine.UpdateStatusConstants.FINALIZING: {
                    if (update.getStatus() != UpdateStatus.INSTALLING) {
                        update.setStatus(UpdateStatus.INSTALLING);
                        mUpdaterController.notifyUpdateChange(mDownloadId, UpdateStatus.INSTALLING);
                    }
                    mProgress = Math.round(percent * 100);
                    mUpdaterController.getActualUpdate(mDownloadId).setInstallProgress(mProgress);
                    mFinalizing = status == UpdateEngine.UpdateStatusConstants.FINALIZING;
                    mUpdaterController.getActualUpdate(mDownloadId).setFinalizing(mFinalizing);
                    mUpdaterController.notifyInstallProgress(mDownloadId);
                }
                break;

                case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT: {
                    installationDone(true);
                    update.setInstallProgress(0);
                    update.setStatus(UpdateStatus.INSTALLED);
                    mUpdaterController.notifyUpdateChange(mDownloadId, UpdateStatus.INSTALLED);
                    mUpdaterController.deleteUpdate(mDownloadId);
                }
                break;

                case UpdateEngine.UpdateStatusConstants.IDLE: {
                    // The service was restarted because we thought we were installing an
                    // update, but we aren't, so clear everything.
                    installationDone(false);
                }
                break;
            }
        }

        @Override
        public void onPayloadApplicationComplete(int errorCode) {
            if (errorCode != UpdateEngine.ErrorCodeConstants.SUCCESS) {
                Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.UNKNOWN);
                installationDone(false);
                Update update = mUpdaterController.getActualUpdate(mDownloadId);
                update.setInstallProgress(0);
                if (!mCanceling){
                    update.setStatus(UpdateStatus.INSTALLATION_FAILED);
                }
                mUpdaterController.notifyUpdateChange(mDownloadId, UpdateStatus.INSTALLATION_FAILED);
            }
        }
    };
    private UpdateEngine mUpdateEngine;
    private boolean mBound;

    private ABUpdateInstaller(Context context, UpdaterController updaterController) {
        mUpdaterController = updaterController;
        mContext = context.getApplicationContext();
        mUpdateEngine = new UpdateEngine();
    }

    static synchronized boolean isInstallingUpdate(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(ABUpdateInstaller.PREF_INSTALLING_AB_ID, null) != null ||
                pref.getString(Constants.PREF_NEEDS_REBOOT_ID, null) != null;
    }

    static synchronized boolean isInstallingUpdate(Context context, String downloadId) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return downloadId.equals(pref.getString(ABUpdateInstaller.PREF_INSTALLING_AB_ID, null)) ||
                TextUtils.equals(pref.getString(Constants.PREF_NEEDS_REBOOT_ID, null), downloadId);
    }

    static synchronized boolean isInstallingUpdateSuspended(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(ABUpdateInstaller.PREF_INSTALLING_SUSPENDED_AB_ID, null) != null;
    }

    static synchronized boolean isInstallingUpdateSuspended(Context context, String downloadId) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return downloadId.equals(pref.getString(ABUpdateInstaller.PREF_INSTALLING_SUSPENDED_AB_ID, null));
    }

    static synchronized boolean isWaitingForReboot(Context context, String downloadId) {
        String waitingId = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(Constants.PREF_NEEDS_REBOOT_ID, null);
        return TextUtils.equals(waitingId, downloadId);
    }

    static synchronized ABUpdateInstaller getInstance(Context context,
                                                      UpdaterController updaterController) {
        if (sInstance == null) {
            sInstance = new ABUpdateInstaller(context, updaterController);
        }
        return sInstance;
    }

    void install(String downloadId) {
        if (isInstallingUpdate(mContext)) {
            Log.e(TAG, "Already installing an update");
            return;
        }

        mDownloadId = downloadId;

        File file = mUpdaterController.getActualUpdate(mDownloadId).getFile();
        if (!file.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            mUpdaterController.getActualUpdate(downloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED);
            mUpdaterController.notifyUpdateChange(downloadId, UpdateStatus.INSTALLATION_FAILED);
            return;
        }

        long offset;
        String[] headerKeyValuePairs;
        try {
            ZipFile zipFile = new ZipFile(file);
            offset = Utils.getZipEntryOffset(zipFile, Constants.AB_PAYLOAD_BIN_PATH);
            ZipEntry payloadPropEntry = zipFile.getEntry(Constants.AB_PAYLOAD_PROPERTIES_PATH);
            try (InputStream is = zipFile.getInputStream(payloadPropEntry);
                 InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader br = new BufferedReader(isr)) {
                List<String> lines = new ArrayList<>();
                for (String line; (line = br.readLine()) != null; ) {
                    lines.add(line);
                }
                headerKeyValuePairs = new String[lines.size()];
                headerKeyValuePairs = lines.toArray(headerKeyValuePairs);
            }
            zipFile.close();
        } catch (IOException | IllegalArgumentException e) {
            Log.e(TAG, "Could not prepare " + file, e);
            mUpdaterController.getActualUpdate(mDownloadId)
                    .setStatus(UpdateStatus.INSTALLATION_FAILED);
            mUpdaterController.notifyUpdateChange(mDownloadId, UpdateStatus.INSTALLATION_FAILED);
            return;
        }

        if (!mBound) {
            try{
                mBound = mUpdateEngine.bind(mUpdateEngineCallback);
            }catch (NullPointerException e){
                Log.e(TAG, "Failed to bind", e);
                mBound = false;
            }
            if (!mBound) {
                Log.e(TAG, "Could not bind");
                mUpdaterController.getActualUpdate(downloadId)
                        .setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(downloadId, UpdateStatus.INSTALLATION_FAILED);
                return;
            }
        }

        try{
            mUpdateEngine.setPerformanceMode(true);
        }catch (NoSuchMethodError ignored){

        }

        String zipFileUri = "file://" + file.getAbsolutePath();
        mUpdateEngine.applyPayload(zipFileUri, offset, 0, headerKeyValuePairs);

        mUpdaterController.getActualUpdate(mDownloadId).setStatus(UpdateStatus.INSTALLING);
        mUpdaterController.notifyUpdateChange(mDownloadId, UpdateStatus.INSTALLING);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(PREF_INSTALLING_AB_ID, mDownloadId)
                .apply();

        Utils.setPersistentStatus(mContext, UpdateStatus.Persistent.INSTALLING_UPDATE);
    }

    void reconnect() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "reconnect: Not installing any update");
            return;
        }

        if (mBound) {
            return;
        }

        mDownloadId = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(PREF_INSTALLING_AB_ID, null);

        // We will get a status notification as soon as we are connected
        mBound = mUpdateEngine.bind(mUpdateEngineCallback);
        if (!mBound) {
            Log.e(TAG, "Could not bind");
        }

    }

    private void installationDone(boolean needsReboot) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String id = needsReboot ? prefs.getString(PREF_INSTALLING_AB_ID, null) : null;
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(Constants.PREF_NEEDS_REBOOT_ID, id)
                .remove(PREF_INSTALLING_AB_ID)
                .apply();
    }

    public boolean cancel() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "cancel: Not installing any update");
            return false;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return false;
        }

        mCanceling = true;

        mUpdateEngine.cancel();
        installationDone(false);

        mUpdaterController.getActualUpdate(mDownloadId)
                .setStatus(UpdateStatus.INSTALLATION_CANCELLED);
        mUpdaterController.notifyUpdateChange(mDownloadId, UpdateStatus.INSTALLATION_CANCELLED);

        mCanceling = false;

        return true;
    }

    public boolean suspend() {
        if (!isInstallingUpdate(mContext)) {
            Log.e(TAG, "cancel: Not installing any update");
            return false;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return false;
        }

        mUpdateEngine.suspend();

        mUpdaterController.getActualUpdate(mDownloadId)
                .setStatus(UpdateStatus.INSTALLATION_SUSPENDED);
        mUpdaterController.notifyUpdateChange(mDownloadId, UpdateStatus.INSTALLATION_SUSPENDED);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(PREF_INSTALLING_SUSPENDED_AB_ID, mDownloadId)
                .apply();

        return true;
    }

    public boolean resume() {
        if (!isInstallingUpdateSuspended(mContext)) {
            Log.e(TAG, "cancel: No update is suspended");
            return false;
        }

        if (!mBound) {
            Log.e(TAG, "Not connected to update engine");
            return false;
        }

        mUpdateEngine.resume();

        mUpdaterController.getActualUpdate(mDownloadId).setStatus(UpdateStatus.INSTALLING);
        mUpdaterController.notifyUpdateChange(mDownloadId, UpdateStatus.INSTALLING);
        mUpdaterController.getActualUpdate(mDownloadId).setInstallProgress(mProgress);
        mUpdaterController.getActualUpdate(mDownloadId).setFinalizing(mFinalizing);
        mUpdaterController.notifyInstallProgress(mDownloadId);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .remove(PREF_INSTALLING_SUSPENDED_AB_ID)
                .apply();

        return true;
    }
}
