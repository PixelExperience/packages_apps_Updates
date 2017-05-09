/*
 * Copyright (C) 2014 The CyanogenMod Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.purenexus.ota.receiver;

import android.app.DownloadManager;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.purenexus.ota.R;
import com.purenexus.ota.misc.Constants;
import com.purenexus.ota.misc.UpdateInfo;
import com.purenexus.ota.service.DownloadCompleteIntentService;
import com.purenexus.ota.service.DownloadService;
import com.purenexus.ota.utils.Utils;

import java.io.IOException;

public class DownloadReceiver extends BroadcastReceiver{
    private static final String TAG = "DownloadReceiver";

    public static final String ACTION_START_DOWNLOAD = "com.purenexus.ota.action.START_DOWNLOAD";
    public static final String EXTRA_UPDATE_INFO = "update_info";

    public static final String ACTION_DOWNLOAD_STARTED = "com.purenexus.ota.action.DOWNLOAD_STARTED";
    static final String EXTRA_FILENAME = "filename";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (ACTION_START_DOWNLOAD.equals(action)) {
            UpdateInfo ui = (UpdateInfo) intent.getParcelableExtra(EXTRA_UPDATE_INFO);
            handleStartDownload(context, ui);
        } else if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            handleDownloadComplete(context, id);
        }
    }

    private void handleStartDownload(Context context, UpdateInfo ui) {
        DownloadService.start(context, ui);
    }

    private void handleDownloadComplete(Context context, long id) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long enqueued = prefs.getLong(Constants.DOWNLOAD_ID, -1);
        String fileName = prefs.getString(Constants.DOWNLOAD_NAME, null);
        if (enqueued < 0 || id < 0 || id != enqueued || fileName == null) {
            return;
        }

        // Send off to DownloadCompleteIntentService
        Intent intent = new Intent(context, DownloadCompleteIntentService.class);
        intent.putExtra(Constants.DOWNLOAD_ID, id);
        intent.putExtra(Constants.DOWNLOAD_NAME, fileName);
        context.startService(intent);

        // Clear the shared prefs
        prefs.edit()
                .remove(Constants.DOWNLOAD_ID)
                .remove(Constants.DOWNLOAD_NAME)
                .apply();
    }
}
