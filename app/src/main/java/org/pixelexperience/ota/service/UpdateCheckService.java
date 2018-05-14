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

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;

import org.json.JSONException;
import org.json.JSONObject;
import org.pixelexperience.ota.R;
import org.pixelexperience.ota.UpdaterApplication;
import org.pixelexperience.ota.activities.UpdaterActivity;
import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.misc.State;
import org.pixelexperience.ota.misc.UpdateInfo;
import org.pixelexperience.ota.requests.UpdatesJsonObjectRequest;
import org.pixelexperience.ota.utils.Utils;

import java.net.URI;
import java.util.Date;

public class UpdateCheckService extends IntentService
        implements Response.ErrorListener, Response.Listener<JSONObject> {

    // request actions
    public static final String ACTION_CHECK = "org.pixelexperience.ota.action.CHECK";
    public static final String ACTION_CANCEL_CHECK = "org.pixelexperience.ota.action.CANCEL_CHECK";
    // broadcast actions
    public static final String ACTION_CHECK_FINISHED = "org.pixelexperience.ota.action.UPDATE_CHECK_FINISHED";
    // extra for ACTION_CHECK_FINISHED: is update available?
    public static final String EXTRA_UPDATE_AVAILABLE = "update_available";
    public static final String EXTRA_CHECK_RESULT = "check_result";
    private static final String TAG = "UpdateCheckService";

    // DefaultRetryPolicy values for Volley
    private static final int UPDATE_REQUEST_TIMEOUT = 15000; // 15 seconds
    private static final int UPDATE_REQUEST_MAX_RETRIES = 0;

    public UpdateCheckService() {
        super("UpdateCheckService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (TextUtils.equals(intent.getAction(), ACTION_CANCEL_CHECK)) {
            ((UpdaterApplication) getApplicationContext()).getQueue().cancelAll(TAG);
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!Utils.isOnline(this)) {
            // Only check for updates if the device is actually connected to a network
            Log.i(TAG, "Could not check for updates. Not connected to the network.");
            return;
        }
        getAvailableUpdates();
    }

    private void recordAvailableUpdate(UpdateInfo availableUpdate, Intent finishedIntent) {

        if (availableUpdate == null) {
            sendBroadcast(finishedIntent);
            return;
        }

        // Store the last update check time and ensure boot check completed is true
        Date d = new Date();
        PreferenceManager.getDefaultSharedPreferences(UpdateCheckService.this).edit()
                .putLong(Constants.LAST_UPDATE_CHECK_PREF, d.getTime())
                .apply();

        UpdaterApplication app = (UpdaterApplication) getApplicationContext();

        if (!app.isMainActivityActive()) {
            // There are updates available
            // The notification should launch the main app
            Intent i = new Intent(this, UpdaterActivity.class);
            i.putExtra(Constants.EXTRA_UPDATE_LIST_UPDATED, true);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_ONE_SHOT);

            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            Resources res = getResources();
            String text = getString(R.string.update_found_notification);

            // Get the notification ready
            CharSequence name = getString(R.string.app_name);
            NotificationChannel mChannel = new NotificationChannel(Constants.DOWNLOAD_CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
            mChannel.enableLights(true);
            mChannel.setLightColor(Color.GREEN);
            mChannel.setShowBadge(false);
            mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(mChannel);
            Notification.Builder builder = new Notification.Builder(this, Constants.DOWNLOAD_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_system_update)
                    .setWhen(System.currentTimeMillis())
                    .setTicker(text)
                    .setContentTitle(text)
                    .setContentText(getString(R.string.update_found_notification_desc))
                    .setContentIntent(contentIntent)
                    .setLocalOnly(true)
                    .setAutoCancel(true);


            Notification.InboxStyle inbox = new Notification.InboxStyle()
                    .setBigContentTitle(text);
            inbox.addLine(availableUpdate.getFileName());
            builder.setStyle(inbox);
            builder.setNumber(1);

            // Trigger the notification
            nm.notify(R.string.update_found_notification, builder.build());
        }

        sendBroadcast(finishedIntent);
    }

    private URI getServerURI() {
        return URI.create(String.format(Constants.OTA_URL, Utils.getDeviceName(), Utils.getOTAVersionCode()));
    }

    private void getAvailableUpdates() {

        // Get the actual ROM Update Server URL
        URI updateServerUri = getServerURI();
        UpdatesJsonObjectRequest request;
        request = new UpdatesJsonObjectRequest(updateServerUri.toASCIIString(),
                Utils.getUserAgentString(this), null, this, this);
        // Improve request error tolerance
        request.setRetryPolicy(new DefaultRetryPolicy(UPDATE_REQUEST_TIMEOUT,
                UPDATE_REQUEST_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        // Set the tag for the request, reuse logging tag
        request.setTag(TAG);

        ((UpdaterApplication) getApplicationContext()).getQueue().add(request);

        Date d = new Date();
        PreferenceManager.getDefaultSharedPreferences(UpdateCheckService.this).edit().putLong(Constants.LAST_UPDATE_CHECK_PREF, d.getTime()).apply();
    }

    private UpdateInfo parseJSON(String jsonString) {
        try {
            JSONObject obj = new JSONObject(jsonString);

            String addons;

            try {
                addons = obj.getJSONArray("addons").toString();
            } catch (Exception e2) {
                addons = "[]";
            }

            UpdateInfo ui = new UpdateInfo.Builder()
                    .setFileName(obj.getString("filename"))
                    .setFilesize(obj.getLong("filesize"))
                    .setBuildDate(obj.getString("build_date"))
                    .setMD5(obj.getString("md5"))
                    .setDeveloper(obj.isNull("developer") ? "" : obj.getString("developer"))
                    .setDeveloperUrl(obj.isNull("developer_url") ? "" : obj.getString("developer_url"))
                    .setDownloadUrl(obj.getString("url"))
                    .setChangelog(obj.isNull("changelog") ? "" : obj.getString("changelog"))
                    .setDonateUrl(obj.isNull("donate_url") ? "" : obj.getString("donate_url"))
                    .setForumUrl(obj.isNull("forum_url") ? "" : obj.getString("forum_url"))
                    .setWebsiteUrl(obj.isNull("website_url") ? "" : obj.getString("website_url"))
                    .setNewsUrl(obj.isNull("news_url") ? "" : obj.getString("news_url"))
                    .setAddons(addons)
                    .build();
            return ui;
        } catch (JSONException e) {
            Log.e(TAG, "Error in JSON result", e);
        }
        return null;
    }

    @Override
    public void onErrorResponse(VolleyError volleyError) {
        VolleyLog.e("Error: ", volleyError.getMessage());
        VolleyLog.e("Error type: " + volleyError.toString());
        Intent intent = new Intent(ACTION_CHECK_FINISHED);
        intent.putExtra(EXTRA_CHECK_RESULT, 0);
        sendBroadcast(intent);
    }

    @Override
    public void onResponse(JSONObject jsonObject) {
        UpdateInfo update = parseJSON(jsonObject.toString());
        Intent intent = new Intent(ACTION_CHECK_FINISHED);
        intent.putExtra(EXTRA_CHECK_RESULT, 1);
        if (update.isNewerThanInstalled()) {
            intent.putExtra(EXTRA_UPDATE_AVAILABLE, true);
        }
        recordAvailableUpdate(update, intent);
        State.saveState(this, update);
    }
}
