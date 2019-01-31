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
package org.pixelexperience.ota.service;

import android.app.DownloadManager;
import android.app.IntentService;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.pixelexperience.ota.R;
import org.pixelexperience.ota.UpdaterApplication;
import org.pixelexperience.ota.activities.UpdaterActivity;
import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.receiver.DownloadNotifier;
import org.pixelexperience.ota.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import org.pixelexperience.ota.utils.MD5;

public class DownloadCompleteIntentService extends IntentService {

    private static final String TAG = "DownloadComplete";

    private DownloadManager mDm;

    public DownloadCompleteIntentService() {
        super(DownloadCompleteIntentService.class.getSimpleName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mDm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Notification dummy = Utils.createDownloadNotificationChannel(this);
        if (dummy != null) {
            startForeground(1, dummy);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!intent.hasExtra(Constants.DOWNLOAD_ID) || !intent.hasExtra(Constants.DOWNLOAD_NAME) || !intent.hasExtra(Constants.DOWNLOAD_MD5)) {
            Log.e(TAG, "Missing intent extra data");
            return;
        }

        long id = intent.getLongExtra(Constants.DOWNLOAD_ID, -1);
        final String destName = intent.getStringExtra(Constants.DOWNLOAD_NAME);
        final String md5 = intent.getStringExtra(Constants.DOWNLOAD_MD5);

        Intent updateIntent = new Intent(this, UpdaterActivity.class);
        updateIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        int status = fetchDownloadStatus(id);
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            String destPath = Utils.makeUpdateFolder().getPath() + "/"
                    + destName;
            File destFileTmp = new File(destPath + Constants.DOWNLOAD_TMP_EXT);
            FileOutputStream outStream = null;
            ParcelFileDescriptor file = null;
            FileInputStream inStream = null;
            FileChannel inChannel = null;
            FileChannel outChannel = null;
            try {
                if (!MD5.checkMD5(md5, destFileTmp)){
                    throw new Exception("Invalid md5"); 
                }
                outStream = new FileOutputStream(destFileTmp);
                file = mDm.openDownloadedFile(id);
                inStream = new FileInputStream(file.getFileDescriptor());
                inChannel = inStream.getChannel();
                outChannel = outStream.getChannel();
                inChannel.transferTo(0, file.getStatSize(), outChannel);
            } catch (Exception e) {
                Log.e(TAG, "Copy of download failed", e);
                displayErrorResult(updateIntent, R.string.unable_to_download_file);
                if (destFileTmp.exists()) {
                    destFileTmp.delete();
                }
                return;
            } finally {
                mDm.remove(id);
                if(outStream != null) {
                    try {
                        outStream.close();
                    } catch (Exception e) {
                    }
                }
                if(file != null) {
                    try {
                        file.close();
                    } catch (Exception e) {
                    }
                }
                if(inStream != null) {
                    try {
                        inStream.close();
                    } catch (Exception e) {
                    }
                }
                if(inChannel != null) {
                    try {
                        inChannel.close();
                    } catch (Exception e) {
                    }
                }
                if(outChannel != null) {
                    try {
                        outChannel.close();
                    } catch (Exception e) {
                    }
                }
            }

            if (!destFileTmp.exists()) {
                // The download was probably stopped. Exit silently
                Log.d(TAG, "File not found, can't verify it");
                return;
            }

            File destFile = new File(destPath);
            if (destFile.exists()) {
                destFile.delete();
            }
            if (!destFileTmp.exists()) {
                // The download was probably stopped. Exit silently
                Log.d(TAG, "File not found, can't rename it");
                return;
            }
            destFileTmp.renameTo(destFile);

            // We passed. Bring the main app to the foreground and trigger download completed
            updateIntent.putExtra(Constants.EXTRA_FINISHED_DOWNLOAD_ID, id);
            updateIntent.putExtra(Constants.EXTRA_FINISHED_DOWNLOAD_PATH,
                    destPath);
            displaySuccessResult(updateIntent);
        } else if (status == DownloadManager.STATUS_FAILED) {
            Log.e(TAG, "Download failed");
            // The download failed, reset
            mDm.remove(id);
            displayErrorResult(updateIntent, R.string.unable_to_download_file);
        }
    }

    private int fetchDownloadStatus(long id) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);
        try (Cursor c = mDm.query(query)) {
            if (c.moveToFirst()) {
                return c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
            }
        }
        return DownloadManager.STATUS_FAILED;
    }

    private void displayErrorResult(Intent updateIntent, int failureMessageResId) {
        DownloadNotifier.notifyDownloadError(this, updateIntent, failureMessageResId);
    }

    private void displaySuccessResult(Intent updateIntent) {
        final UpdaterApplication app = (UpdaterApplication) getApplicationContext();
        if (app.isMainActivityActive()) {
            startActivity(updateIntent);
        } else {
            DownloadNotifier.notifyDownloadComplete(this, updateIntent);
        }
    }
}