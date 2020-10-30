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
import android.content.SharedPreferences;
import android.os.UpdateEngine;
import android.os.UpdateEngineCallback;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
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

public class ABUpdateInstaller {

    private static final String TAG = "ABUpdateInstaller";
    public static final String ACTION_RESTART_PENDING = "action_restart_pending";

    @SuppressLint("StaticFieldLeak")
    private static ABUpdateInstaller sInstance = null;

    public static boolean sNeedsReboot = false;

    private final UpdaterController mUpdaterController;
    private final Context mContext;
    private String mDownloadId;

    private final LocalBroadcastManager mBroadcastManager;
    private final UpdateEngineCallback mUpdateEngineCallback = new UpdateEngineCallback() {

        @Override
        public void onStatusUpdate(int status, float percent) {
            boolean isOperationOnProgress = status != UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT &&
                    status != UpdateEngine.UpdateStatusConstants.IDLE;
            Update update = mUpdaterController.getCurrentUpdate();
            if (isOperationOnProgress && update == null) {
                Log.d(TAG, "There's a installation running, waiting before getting info");
                for (int i = 0; i <= 10; i++) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                    update = mUpdaterController.getCurrentUpdate();
                    if (update != null) {
                        Log.d(TAG, "Update found!");
                        break;
                    }
                }
            }

            if (update == null && status == UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT) {
                Log.d(TAG, "Pending reboot found");
                installationDone(true);
                return;
            }

            if (update == null) {
                // We read the id from a preference, the update could no longer exist
                installationDone(false);
                return;
            }

            switch (status) {
                case UpdateEngine.UpdateStatusConstants.DOWNLOADING:
                case UpdateEngine.UpdateStatusConstants.FINALIZING: {
                    if (update.getStatus() != UpdateStatus.INSTALLING) {
                        update.setStatus(UpdateStatus.INSTALLING);
                        mUpdaterController.notifyUpdateChange(UpdateStatus.INSTALLING);
                    }
                    int progress = Math.round(percent * 100);
                    mUpdaterController.getCurrentUpdate().setInstallProgress(progress);
                    boolean finalizing = status == UpdateEngine.UpdateStatusConstants.FINALIZING;
                    mUpdaterController.getCurrentUpdate().setFinalizing(finalizing);
                    mUpdaterController.notifyInstallProgress();
                }
                break;

                case UpdateEngine.UpdateStatusConstants.UPDATED_NEED_REBOOT: {
                    installationDone(true);
                    update.setInstallProgress(0);
                    mUpdaterController.removeUpdate(false);
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
                Update update = mUpdaterController.getCurrentUpdate();
                update.setInstallProgress(0);
                update.setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(UpdateStatus.INSTALLATION_FAILED);
            }
        }
    };
    private UpdateEngine mUpdateEngine;
    private boolean mBound;

    private ABUpdateInstaller(Context context, UpdaterController updaterController) {
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
        mUpdaterController = updaterController;
        mContext = context.getApplicationContext();
        mUpdateEngine = new UpdateEngine();
    }

    static synchronized boolean isInstallingUpdate(Context context) {
        if (needsReboot()) {
            return true;
        }
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        return pref.getString(Constants.PREF_INSTALLING_AB_ID, null) != null;
    }

    public static synchronized boolean needsReboot() {
        return sNeedsReboot;
    }

    static synchronized ABUpdateInstaller getInstance(Context context,
                                                      UpdaterController updaterController) {
        if (sInstance == null) {
            sInstance = new ABUpdateInstaller(context, updaterController);
        }
        return sInstance;
    }

    void install() {
        if (isInstallingUpdate(mContext)) {
            Log.e(TAG, "Already installing an update");
            return;
        }

        mDownloadId = mUpdaterController.getCurrentUpdate().getDownloadId();

        File file = mUpdaterController.getCurrentUpdate().getFile();
        if (!file.exists()) {
            Log.e(TAG, "The given update doesn't exist");
            mUpdaterController.getCurrentUpdate()
                    .setStatus(UpdateStatus.INSTALLATION_FAILED);
            mUpdaterController.notifyUpdateChange(UpdateStatus.INSTALLATION_FAILED);
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
            mUpdaterController.getCurrentUpdate()
                    .setStatus(UpdateStatus.INSTALLATION_FAILED);
            mUpdaterController.notifyUpdateChange(UpdateStatus.INSTALLATION_FAILED);
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
                mUpdaterController.getCurrentUpdate()
                        .setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(UpdateStatus.INSTALLATION_FAILED);
                return;
            }
        }

        mUpdaterController.getCurrentUpdate().setStatus(UpdateStatus.INSTALLING);
        mUpdaterController.notifyUpdateChange(UpdateStatus.INSTALLING);

        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(Constants.PREF_INSTALLING_AB_ID, mDownloadId)
                .apply();

        String[] finalHeaderKeyValuePairs = headerKeyValuePairs;
        new Thread(() -> {
            try {
                mUpdateEngine.setPerformanceMode(true);
            } catch (NoSuchMethodError ignored) {

            }
            try {
                String zipFileUri = "file://" + file.getAbsolutePath();
                mUpdateEngine.applyPayload(zipFileUri, offset, 0, finalHeaderKeyValuePairs);
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply payload", e);
                installationDone(false);
                mUpdaterController.getCurrentUpdate()
                        .setStatus(UpdateStatus.INSTALLATION_FAILED);
                mUpdaterController.notifyUpdateChange(UpdateStatus.INSTALLATION_FAILED);
            }
        }).start();
    }

    void reconnect() {
        if (mBound) {
            return;
        }

        mDownloadId = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(Constants.PREF_INSTALLING_AB_ID, null);

        // We will get a status notification as soon as we are connected
        mBound = mUpdateEngine.bind(mUpdateEngineCallback);
        if (!mBound) {
            Log.e(TAG, "Could not bind");
        }
    }

    private void installationDone(boolean needsReboot) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .remove(Constants.PREF_INSTALLING_AB_ID)
                .apply();
        if (needsReboot) {
            sNeedsReboot = true;
            Intent intent = new Intent();
            intent.setAction(ACTION_RESTART_PENDING);
            mBroadcastManager.sendBroadcast(intent);
        }
    }
}
