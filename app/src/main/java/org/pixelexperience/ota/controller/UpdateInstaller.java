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
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.misc.FileUtils;
import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.UpdateInfo;
import org.pixelexperience.ota.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

class UpdateInstaller {

    private static final String TAG = "UpdateInstaller";

    @SuppressLint("StaticFieldLeak")
    private static UpdateInstaller sInstance = null;
    private static String sInstallingUpdate = null;
    private final Context mContext;
    private final UpdaterController mUpdaterController;
    private Thread mPrepareUpdateThread;
    private volatile boolean mCanCancel;

    private UpdateInstaller(Context context, UpdaterController controller) {
        mContext = context.getApplicationContext();
        mUpdaterController = controller;
    }

    static synchronized UpdateInstaller getInstance(Context context,
                                                    UpdaterController updaterController) {
        if (sInstance == null) {
            sInstance = new UpdateInstaller(context, updaterController);
        }
        return sInstance;
    }

    static synchronized boolean isInstalling() {
        return sInstallingUpdate != null;
    }

    void install() {
        if (isInstalling()) {
            Log.e(TAG, "Already installing an update");
            return;
        }

        UpdateInfo update = mUpdaterController.getCurrentUpdate();
        if (SystemProperties.get(Constants.PROP_RECOVERY_UPDATE, "").equals("true") && Utils.isEncrypted(mContext, update.getFile())) {
            // uncrypt rewrites the file so that it can be read without mounting
            // the filesystem, so create a copy of it.
            prepareForUncryptAndInstall(update);
        } else {
            installPackage(update.getFile());
        }
    }

    private void installPackage(File update) {
        try {
            android.os.RecoverySystem.installPackage(mContext, update);
        } catch (IOException e) {
            Log.e(TAG, "Could not install update", e);
            mUpdaterController.setStatus(UpdateStatus.INSTALLATION_FAILED);
            mUpdaterController.notifyUpdateChange(UpdateStatus.INSTALLATION_FAILED);
        }
    }

    private synchronized void prepareForUncryptAndInstall(UpdateInfo update) {
        String uncryptFilePath = update.getFile().getAbsolutePath() + Constants.UNCRYPT_FILE_EXT;
        File uncryptFile = new File(uncryptFilePath);

        Runnable copyUpdateRunnable = new Runnable() {
            private long mLastUpdate = -1;

            FileUtils.ProgressCallBack mProgressCallBack = new FileUtils.ProgressCallBack() {
                @Override
                public void update(int progress) {
                    long now = SystemClock.elapsedRealtime();
                    if (mLastUpdate < 0 || now - mLastUpdate > 500) {
                        mUpdaterController.getInstallInfo().setProgress(progress);
                        mUpdaterController.notifyInstallProgress();
                        mLastUpdate = now;
                    }
                }
            };

            @Override
            public void run() {
                UpdateStatus status = UpdateStatus.INSTALLING;
                try {
                    mCanCancel = true;
                    FileUtils.copyFile(update.getFile(), uncryptFile, mProgressCallBack);
                    try {
                        Set<PosixFilePermission> perms = new HashSet<>();
                        perms.add(PosixFilePermission.OWNER_READ);
                        perms.add(PosixFilePermission.OWNER_WRITE);
                        perms.add(PosixFilePermission.OTHERS_READ);
                        perms.add(PosixFilePermission.GROUP_READ);
                        Files.setPosixFilePermissions(uncryptFile.toPath(), perms);
                    } catch (IOException exception) {}

                    mCanCancel = false;
                    if (mPrepareUpdateThread.isInterrupted()) {
                        status = UpdateStatus.INSTALLATION_FAILED;
                        mUpdaterController.setStatus(UpdateStatus.INSTALLATION_FAILED);
                        mUpdaterController.getInstallInfo().setProgress(0);
                        uncryptFile.delete();
                    } else {
                        installPackage(uncryptFile);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not copy update", e);
                    uncryptFile.delete();
                    status = UpdateStatus.INSTALLATION_FAILED;
                    mUpdaterController.setStatus(UpdateStatus.INSTALLATION_FAILED);
                } finally {
                    synchronized (UpdateInstaller.this) {
                        mCanCancel = false;
                        mPrepareUpdateThread = null;
                        sInstallingUpdate = null;
                    }
                    mUpdaterController.notifyUpdateChange(status);
                }
            }
        };

        mPrepareUpdateThread = new Thread(copyUpdateRunnable);
        mPrepareUpdateThread.start();
        sInstallingUpdate = update.getDownloadId();
        mCanCancel = false;

        mUpdaterController.setStatus(UpdateStatus.INSTALLING);
        mUpdaterController.notifyUpdateChange(UpdateStatus.INSTALLING);
    }

    public synchronized void cancel() {
        if (!mCanCancel) {
            Log.d(TAG, "Nothing to cancel");
            return;
        }
        mPrepareUpdateThread.interrupt();
    }
}
