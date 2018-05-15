/*
 * Copyright (C) 2018 Pixel Experience (jhenrique09)
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */
package org.pixelexperience.ota.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.preference.PreferenceManager;
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
import org.pixelexperience.ota.service.UpdateCheckService;

import java.net.URI;
import java.util.Date;

import static android.content.Context.NOTIFICATION_SERVICE;

public class UpdateChecker implements Response.ErrorListener, Response.Listener<JSONObject> {

    // broadcast actions
    public static final String ACTION_CHECK_FINISHED = "org.pixelexperience.ota.action.UPDATE_CHECK_FINISHED";
    // extra for ACTION_CHECK_FINISHED: is update available?
    public static final String EXTRA_UPDATE_AVAILABLE = "update_available";
    public static final String EXTRA_CHECK_RESULT = "check_result";
    private static final String TAG = "UpdateChecker";
    // DefaultRetryPolicy values for Volley
    private static final int UPDATE_REQUEST_TIMEOUT = 15000; // 15 seconds
    private static final int UPDATE_REQUEST_MAX_RETRIES = 0;

    private Context mContext;
    private UpdaterCheckerResult mResultListener;

    public UpdateChecker(Context context, UpdaterCheckerResult resultListener) {
        mContext = context;
        mResultListener = resultListener;
    }

    private static UpdateInfo parseJSON(String jsonString) {
        try {
            JSONObject obj = new JSONObject(jsonString);

            String addons;

            try {
                addons = obj.getJSONArray("addons").toString();
            } catch (Exception e2) {
                addons = "[]";
            }

            return new UpdateInfo.Builder()
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
        } catch (JSONException e) {
            Log.e(TAG, "Error in JSON result", e);
        }
        return null;
    }

    public static void cancelAllRequests(Context context){
        ((UpdaterApplication) context.getApplicationContext()).getQueue().cancelAll(TAG);
    }

    public static void scheduleUpdateService(Context context) {
        scheduleUpdateService(context, false);
    }

    public static void scheduleUpdateService(Context context, Boolean onBoot) {
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();
        if (onBoot){
            jobScheduler.schedule(new JobInfo.Builder(0, new ComponentName(context, UpdateCheckService.class))
                    .setPeriodic(Constants.UPDATE_DEFAULT_FREQ)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build());
        }else{
            jobScheduler.schedule(new JobInfo.Builder(0, new ComponentName(context, UpdateCheckService.class))
                    .setMinimumLatency(Constants.UPDATE_DEFAULT_FREQ)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build());
        }
    }

    private void recordAvailableUpdate(UpdateInfo availableUpdate, Intent finishedIntent) {

        if (availableUpdate == null) {
            mContext.sendBroadcast(finishedIntent);
            return;
        }

        // Store the last update check time and ensure boot check completed is true
        Date d = new Date();
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putLong(Constants.LAST_UPDATE_CHECK_PREF, d.getTime())
                .apply();

        mContext.sendBroadcast(finishedIntent);
    }

    private URI getServerURI() {
        return URI.create(String.format(Constants.OTA_URL, Utils.getDeviceName(), Utils.getOTAVersionCode()));
    }

    public void check() {
        // Get the actual ROM Update Server URL
        URI updateServerUri = getServerURI();
        UpdatesJsonObjectRequest request;
        request = new UpdatesJsonObjectRequest(updateServerUri.toASCIIString(),
                Utils.getUserAgentString(mContext), null, this, this);
        // Improve request error tolerance
        request.setRetryPolicy(new DefaultRetryPolicy(UPDATE_REQUEST_TIMEOUT,
                UPDATE_REQUEST_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        // Set the tag for the request, reuse logging tag
        request.setTag(TAG);

        ((UpdaterApplication) mContext.getApplicationContext()).getQueue().add(request);

        Date d = new Date();
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putLong(Constants.LAST_UPDATE_CHECK_PREF, d.getTime()).apply();
    }

    @Override
    public void onErrorResponse(VolleyError volleyError) {
        VolleyLog.e("Error: ", volleyError.getMessage());
        VolleyLog.e("Error type: " + volleyError.toString());
        Intent intent = new Intent(ACTION_CHECK_FINISHED);
        intent.putExtra(EXTRA_CHECK_RESULT, 0);
        mContext.sendBroadcast(intent);
        if (mResultListener != null) {
            mResultListener.onResult(false);
        }
    }

    @Override
    public void onResponse(JSONObject jsonObject) {
        UpdateInfo update = UpdateChecker.parseJSON(jsonObject.toString());
        Intent intent = new Intent(ACTION_CHECK_FINISHED);
        intent.putExtra(EXTRA_CHECK_RESULT, 1);
        if (update != null && update.isNewerThanInstalled()) {
            intent.putExtra(EXTRA_UPDATE_AVAILABLE, true);
            UpdaterApplication app = (UpdaterApplication) mContext.getApplicationContext();
            if (!app.isMainActivityActive()) {
                // There are updates available
                // The notification should launch the main app
                Intent i = new Intent(mContext, UpdaterActivity.class);
                i.putExtra(Constants.EXTRA_UPDATE_LIST_UPDATED, true);
                PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, i,
                        PendingIntent.FLAG_ONE_SHOT);

                NotificationManager nm = (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);

                String text = mContext.getString(R.string.update_found_notification);

                // Get the notification ready
                CharSequence name = mContext.getString(R.string.app_name);
                NotificationChannel mChannel = new NotificationChannel(Constants.DOWNLOAD_CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
                mChannel.enableLights(true);
                mChannel.setLightColor(Color.GREEN);
                mChannel.setShowBadge(false);
                mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                try {
                    nm.createNotificationChannel(mChannel);
                } catch (Exception ex) {
                    Log.d(TAG, "Failed to create notification channel\n" + ex.getMessage());
                }
                Notification.Builder builder = new Notification.Builder(mContext, Constants.DOWNLOAD_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_system_update)
                        .setWhen(System.currentTimeMillis())
                        .setTicker(text)
                        .setContentTitle(text)
                        .setContentText(mContext.getString(R.string.update_found_notification_desc))
                        .setContentIntent(contentIntent)
                        .setLocalOnly(true)
                        .setAutoCancel(true);

                // Trigger the notification
                try {
                    nm.notify(R.string.update_found_notification, builder.build());
                } catch (Exception ex) {
                    Log.d(TAG, "Failed to create notification\n" + ex.getMessage());
                }
            }
        }
        recordAvailableUpdate(update, intent);
        State.saveState(mContext, update);
        if (mResultListener != null) {
            mResultListener.onResult(true);
        }
    }
}
