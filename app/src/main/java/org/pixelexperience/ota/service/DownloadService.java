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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;

import org.json.JSONObject;
import org.pixelexperience.ota.R;
import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.misc.UpdateInfo;
import org.pixelexperience.ota.receiver.DownloadReceiver;
import org.pixelexperience.ota.utils.Utils;

public class DownloadService extends IntentService
        implements Response.Listener<JSONObject>, Response.ErrorListener {
    private static final String TAG = DownloadService.class.getSimpleName();

    private static final String EXTRA_UPDATE_INFO = "update_info";

    private SharedPreferences mPrefs;
    private UpdateInfo mInfo = null;

    public DownloadService() {
        super(TAG);
    }

    public static void start(Context context, UpdateInfo ui) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.putExtra(EXTRA_UPDATE_INFO, (Parcelable) ui);
        context.startForegroundService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification dummy = Utils.createDownloadNotificationChannel(this);
        if (dummy != null) {
            startForeground(1, dummy);
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mInfo = intent.getParcelableExtra(EXTRA_UPDATE_INFO);

        if (mInfo == null) {
            Log.e(TAG, "Intent UpdateInfo extras were null");
            return;
        }

        downloadFullZip();
    }

    private long enqueueDownload(String downloadUrl) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        String userAgent = Utils.getUserAgentString(this);
        if (userAgent != null) {
            request.addRequestHeader("User-Agent", userAgent);
        }
        request.setTitle(getString(R.string.app_name));
        request.setVisibleInDownloadsUi(false);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        final DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        return dm.enqueue(request);
    }

    private void downloadFullZip() {
        Log.v(TAG, "Downloading full zip");

        long downloadId = enqueueDownload(mInfo.getDownloadUrl());

        // Store in shared preferences
        mPrefs.edit()
                .putLong(Constants.DOWNLOAD_ID, downloadId)
                .putString(Constants.DOWNLOAD_NAME, mInfo.getFileName())
                .putString(Constants.DOWNLOAD_MD5, mInfo.getMD5())
                .apply();

        Utils.cancelNotification(this);

        Intent intent = new Intent(DownloadReceiver.ACTION_DOWNLOAD_STARTED);
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId);
        sendBroadcast(intent);
    }

    @Override
    public void onErrorResponse(VolleyError error) {
        VolleyLog.e("Error: ", error.getMessage());
    }

    @Override
    public void onResponse(JSONObject response) {
        VolleyLog.v("Response:%n %s", response);
        downloadFullZip();
    }
}