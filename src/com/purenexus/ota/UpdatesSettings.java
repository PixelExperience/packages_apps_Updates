/*
 * Copyright (C) 2012 The CyanogenMod Project (DvTonder)
 * Copyright (C) 2017 The LineageOS Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.purenexus.ota;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceScreen;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.LayoutInflater;

import com.purenexus.ota.misc.Constants;
import com.purenexus.ota.misc.State;
import com.purenexus.ota.misc.UpdateInfo;
import com.purenexus.ota.receiver.DownloadReceiver;
import com.purenexus.ota.service.UpdateCheckService;
import com.purenexus.ota.utils.UpdateFilter;
import com.purenexus.ota.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;

import com.mukesh.MarkdownView;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.Volley;
import com.android.volley.toolbox.StringRequest;

public class UpdatesSettings extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener, UpdatePreference.OnReadyListener,
        UpdatePreference.OnActionListener {
    private static String TAG = "UpdatesSettings";

    private static String REQUEST_TAG = "LoadChangelog";

    // intent extras
    public static final String EXTRA_UPDATE_LIST_UPDATED = "update_list_updated";
    public static final String EXTRA_FINISHED_DOWNLOAD_ID = "download_id";
    public static final String EXTRA_FINISHED_DOWNLOAD_PATH = "download_path";

    private static final String UPDATES_CATEGORY = "updates_category";

    private static final String INFO_CATEGORY = "info_category";
    private static final String DEVELOPER_INFO = "developer_info";
    private static final String WEBSITE_INFO = "website_info";
    private static final String DONATE_INFO = "donate_info";

    private static final String ADDONS_CATEGORY = "addons_category";

    private SharedPreferences mPrefs;
    private ListPreference mUpdateCheck;

    private PreferenceScreen preferenceScreen;

    private PreferenceCategory mUpdatesList;
    private UpdatePreference mDownloadingPreference;

    private PreferenceCategory mInfoCategory;
    private PreferenceScreen mDeveloperInfo;
    private PreferenceScreen mWebsiteInfo;
    private PreferenceScreen mDonateInfo;

    private static String DONATE_URL = "";
    private static String WEBSITE_URL = "";

    private PreferenceCategory mAdddonsList;

    private File mUpdateFolder;

    private Context mContext;

    private boolean mStartUpdateVisible = false;
    private ProgressDialog mProgressDialog;

    private DownloadManager mDownloadManager;
    private boolean mDownloading = false;
    private long mDownloadId;
    private String mFileName;

    private Handler mUpdateHandler = new Handler();

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (DownloadReceiver.ACTION_DOWNLOAD_STARTED.equals(action)) {
                mDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                mUpdateHandler.post(mUpdateProgress);
            } else if (UpdateCheckService.ACTION_CHECK_FINISHED.equals(action)) {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                    mProgressDialog = null;

                    int count = intent.getIntExtra(UpdateCheckService.EXTRA_NEW_UPDATE_COUNT, -1);
                    if (count == 0) {
                        showSnack(mContext.getString(R.string.no_updates_found));
                    } else if (count < 0) {
                        showSnack(mContext.getString(R.string.update_check_failed));
                    }
                }
                updateLayout();
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mContext = getActivity();

        mDownloadManager = (DownloadManager) mContext.getSystemService(mContext.DOWNLOAD_SERVICE);

        // Load the layouts
        setPreferencesFromResource(R.xml.main, null);

        preferenceScreen = getPreferenceScreen();

        mUpdatesList = (PreferenceCategory) findPreference(UPDATES_CATEGORY);
        mUpdateCheck = (ListPreference) findPreference(Constants.UPDATE_CHECK_PREF);


        mInfoCategory = (PreferenceCategory) findPreference(INFO_CATEGORY);
        mDeveloperInfo = (PreferenceScreen) findPreference(DEVELOPER_INFO);
        mWebsiteInfo = (PreferenceScreen) findPreference(WEBSITE_INFO);
        mDonateInfo = (PreferenceScreen) findPreference(DONATE_INFO);

        mAdddonsList = (PreferenceCategory) findPreference(ADDONS_CATEGORY);

        mWebsiteInfo.setOnPreferenceClickListener(this);
        mDonateInfo.setOnPreferenceClickListener(this);

        preferenceScreen.removePreference(mInfoCategory);
        preferenceScreen.removePreference(mAdddonsList);

        // Load the stored preference data
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (mUpdateCheck != null) {
            int check = mPrefs.getInt(Constants.UPDATE_CHECK_PREF, Constants.UPDATE_FREQ_WEEKLY);
            mUpdateCheck.setValue(String.valueOf(check));
            mUpdateCheck.setSummary(mapCheckValue(check));
            mUpdateCheck.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public void onReady(UpdatePreference pref) {
        pref.setOnReadyListener(null);
        mUpdateHandler.post(mUpdateProgress);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mUpdateCheck) {
            int value = Integer.valueOf((String) newValue);
            mPrefs.edit().putInt(Constants.UPDATE_CHECK_PREF, value).apply();
            mUpdateCheck.setSummary(mapCheckValue(value));
            Utils.scheduleUpdateService(mContext, value * 1000);
            return true;
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mWebsiteInfo) {
            try{
                Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse(WEBSITE_URL));
                startActivity(intent);
            }catch (Exception ex){
                showSnack(getString(R.string.error_open_url));
            }
            return true;
        }else if (preference == mDonateInfo) {
            try{
                Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse(DONATE_URL));
                startActivity(intent);
            }catch (Exception ex){
                showSnack(getString(R.string.error_open_url));
            }
            return true;
        }else{
            String key;
            try{
                key = preference.getKey();
                if (key == null){
                    key = "";
                }
            }catch(Exception ex){
                key = "";
            }

            if (key.startsWith("addon_")){
                String url = key.substring(6);
                try{
                    Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse(url));
                    startActivity(intent);
                }catch (Exception ex){
                    showSnack(getString(R.string.error_open_url));
                }
            }
        }
        return false;
    }

    @Override
    public void onStart() {
        super.onStart();

        getListView().setNestedScrollingEnabled(false);

        // Determine if there are any in-progress downloads
        mDownloadId = mPrefs.getLong(Constants.DOWNLOAD_ID, -1);
        if (mDownloadId >= 0) {
            Cursor c =
                    mDownloadManager.query(new DownloadManager.Query().setFilterById(mDownloadId));
            if (c == null || !c.moveToFirst()) {
                showSnack(mContext.getString(R.string.download_not_found));
            } else {
                int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                Uri uri = Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI)));
                if (status == DownloadManager.STATUS_PENDING
                        || status == DownloadManager.STATUS_RUNNING
                        || status == DownloadManager.STATUS_PAUSED) {
                    mFileName = uri.getLastPathSegment();
                }
            }
            if (c != null) {
                c.close();
            }
        }
        if (mDownloadId < 0 || mFileName == null) {
            resetDownloadState();
        }

        updateLayout();

        IntentFilter filter = new IntentFilter(UpdateCheckService.ACTION_CHECK_FINISHED);
        filter.addAction(DownloadReceiver.ACTION_DOWNLOAD_STARTED);
        mContext.registerReceiver(mReceiver, filter);

        checkForDownloadCompleted(getActivity().getIntent());
        getActivity().setIntent(null);
    }

    @Override
    public void onStop() {
        super.onStop();
        mUpdateHandler.removeCallbacks(mUpdateProgress);
        mContext.unregisterReceiver(mReceiver);
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }
    }

    @Override
    public void onStartDownload(UpdatePreference pref) {
        // If there is no internet connection, display a message and return.
        if (!Utils.isOnline(mContext)) {
            showSnack(mContext.getString(R.string.data_connection_required));
            return;
        }

        if (mDownloading) {
            showSnack(mContext.getString(R.string.download_already_running));
            return;
        }

        // We have a match, get ready to trigger the download
        mDownloadingPreference = pref;

        startDownload();
    }

    private Runnable mUpdateProgress = new Runnable() {
        public void run() {
            if (!mDownloading || mDownloadingPreference == null || mDownloadId < 0) {
                return;
            }

            ProgressBar progressBar = mDownloadingPreference.getProgressBar();
            if (progressBar == null) {
                return;
            }

            ImageView updatesButton = mDownloadingPreference.getUpdatesButton();
            if (updatesButton == null) {
                return;
            }

            // Enable updates button
            updatesButton.setEnabled(true);

            DownloadManager.Query q = new DownloadManager.Query();
            q.setFilterById(mDownloadId);

            Cursor cursor = mDownloadManager.query(q);
            int status;

            if (cursor == null || !cursor.moveToFirst()) {
                // DownloadReceiver has likely already removed the download
                // from the DB due to failure or signature mismatch
                status = DownloadManager.STATUS_FAILED;
            } else {
                status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            }

            switch (status) {
                case DownloadManager.STATUS_PENDING:
                    progressBar.setIndeterminate(true);
                    break;
                case DownloadManager.STATUS_PAUSED:
                case DownloadManager.STATUS_RUNNING:
                    int downloadedBytes = cursor.getInt(
                        cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int totalBytes = cursor.getInt(
                        cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (totalBytes < 0) {
                        progressBar.setIndeterminate(true);
                    } else {
                        progressBar.setIndeterminate(false);
                        progressBar.setMax(totalBytes);
                        progressBar.setProgress(downloadedBytes);
                    }
                    break;
                case DownloadManager.STATUS_FAILED:
                    mDownloadingPreference.setStyle(UpdatePreference.STYLE_NEW);
                    resetDownloadState();
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    mDownloadingPreference.setStyle(UpdatePreference.STYLE_COMPLETING);
                    break;
            }

            if (cursor != null) {
                cursor.close();
            }
            if (status != DownloadManager.STATUS_FAILED
                    && status != DownloadManager.STATUS_SUCCESSFUL) {
                mUpdateHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onStopCompletingDownload(final UpdatePreference pref) {
        if (!mDownloading || mFileName == null) {
            pref.setStyle(UpdatePreference.STYLE_NEW);
            resetDownloadState();
            return;
        }

        final File tmpZip = new File(mUpdateFolder, mFileName + Constants.DOWNLOAD_TMP_EXT);
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.confirm_download_cancelation_dialog_title)
                .setMessage(R.string.confirm_download_cancelation_dialog_message)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!tmpZip.isFile() || tmpZip.delete()) {
                            // Set the preference back to new style
                            pref.setStyle(UpdatePreference.STYLE_NEW);
                            resetDownloadState();
                            showSnack(mContext.getString(R.string.download_cancelled));
                        } else {
                            Log.e(TAG, "Could not delete temp zip");
                            showSnack(mContext.getString(R.string.unable_to_stop_download));
                        }
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    @Override
    public void onStopDownload(final UpdatePreference pref) {
        if (!mDownloading || mFileName == null || mDownloadId < 0) {
            pref.setStyle(UpdatePreference.STYLE_NEW);
            resetDownloadState();
            return;
        }

        new AlertDialog.Builder(mContext)
                .setTitle(R.string.confirm_download_cancelation_dialog_title)
                .setMessage(R.string.confirm_download_cancelation_dialog_message)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Set the preference back to new style
                        pref.setStyle(UpdatePreference.STYLE_NEW);

                        // We are OK to stop download, trigger it
                        mDownloadManager.remove(mDownloadId);
                        mUpdateHandler.removeCallbacks(mUpdateProgress);
                        resetDownloadState();

                        // Clear the stored data from shared preferences
                        mPrefs.edit()
                                .remove(Constants.DOWNLOAD_ID)
                                .apply();

                        showSnack(mContext.getString(R.string.download_cancelled));
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    void checkForDownloadCompleted(Intent intent) {
        if (intent == null) {
            return;
        }

        long downloadId = intent.getLongExtra(EXTRA_FINISHED_DOWNLOAD_ID, -1);
        if (downloadId < 0) {
            return;
        }

        String fullPathName = intent.getStringExtra(EXTRA_FINISHED_DOWNLOAD_PATH);
        if (fullPathName == null) {
            return;
        }

        String fileName = new File(fullPathName).getName();

        // Find the matching preference so we can retrieve the UpdateInfo
        UpdatePreference pref = (UpdatePreference) mUpdatesList.findPreference(fileName);
        if (pref != null) {
            pref.setStyle(UpdatePreference.STYLE_DOWNLOADED);
            onStartUpdate(pref);
        }

        resetDownloadState();
    }

    private void resetDownloadState() {
        mDownloadId = -1;
        mFileName = null;
        mDownloading = false;
        mDownloadingPreference = null;
    }

    private String mapCheckValue(Integer value) {
        Resources resources = getResources();
        String[] checkNames = resources.getStringArray(R.array.update_check_entries);
        String[] checkValues = resources.getStringArray(R.array.update_check_values);
        for (int i = 0; i < checkValues.length; i++) {
            if (Integer.decode(checkValues[i]).equals(value)) {
                return checkNames[i];
            }
        }
        return getString(R.string.unknown);
    }

    void checkForUpdates() {
        if (mProgressDialog != null) {
            return;
        }

        // If there is no internet connection, display a message and return.
        if (!Utils.isOnline(mContext)) {
            showSnack(mContext.getString(R.string.data_connection_required));
            return;
        }

        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setMessage(getString(R.string.checking_for_updates));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                Intent cancelIntent = new Intent(getActivity(), UpdateCheckService.class);
                cancelIntent.setAction(UpdateCheckService.ACTION_CANCEL_CHECK);
                mContext.startService(cancelIntent);
                mProgressDialog = null;
            }
        });

        Intent checkIntent = new Intent(getActivity(), UpdateCheckService.class);
        checkIntent.setAction(UpdateCheckService.ACTION_CHECK);
        mContext.startService(checkIntent);

        mProgressDialog.show();
    }

    void updateLayout() {
        // Read existing Updates
        LinkedList<String> existingFiles = new LinkedList<String>();

        mUpdateFolder = Utils.makeUpdateFolder(mContext);
        File[] files = mUpdateFolder.listFiles(new UpdateFilter(".zip"));

        if (mUpdateFolder.exists() && mUpdateFolder.isDirectory() && files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    existingFiles.add(file.getName());
                }
            }
        }

        // Clear the notification if one exists
        Utils.cancelNotification(getActivity());

        // Build list of updates
        LinkedList<UpdateInfo> availableUpdates = State.loadState(mContext);
        LinkedList<UpdateInfo> updates = new LinkedList<UpdateInfo>();

        if (availableUpdates.size() > 0){
            UpdateInfo update = availableUpdates.get(0);
            if (existingFiles.contains(update.getFileName())) {
                UpdateInfo ui = new UpdateInfo.Builder()
                .setFileName(update.getFileName())
                .setFilesize(update.getFileSize())
                .setBuildDate(update.getDate())
                .setMD5(update.getMD5())
                .setDeveloper(update.getDeveloper())
                .setChangelogUrl(update.getChangelogUrl())
                .setDonateUrl(update.getDonateUrl())
                .setWebsiteUrl(update.getWebsiteUrl())
                .setAddons(update.getAddonsInJson())
                .build();
                updates.add(ui);
            }else{
                updates.add(update);
            }
        }

        // Update the preference list
        refreshPreferences(updates);
    }

    private boolean isDownloadCompleting(String fileName) {
        return new File(mUpdateFolder, fileName + Constants.DOWNLOAD_TMP_EXT).isFile();
    }

    private void refreshPreferences(LinkedList<UpdateInfo> updates) {
        if (mUpdatesList == null) {
            return;
        }

        // Clear the list
        mUpdatesList.removeAll();
        preferenceScreen.removePreference(mInfoCategory);
        preferenceScreen.removePreference(mAdddonsList);

        // Convert the installed version name to the associated filename
        String installedZip = "purenexus_" + Utils.getDeviceType() + "-" + Utils.getInstalledVersion() + ".zip";



        if (updates.size() > 0){
            UpdateInfo ui = updates.get(0);

            preferenceScreen.addPreference(mAdddonsList);
            mAdddonsList.removeAll();

            List<Map<String,String>> addons = ui.getAddons();

            for (Map<String, String> addon : addons) {
                Preference preference = new Preference(preferenceScreen.getContext());
                preference.setTitle(addon.get("title"));
                preference.setSummary(addon.get("summary"));
                preference.setKey("addon_" + addon.get("url"));
                preference.setIcon(mContext.getDrawable(R.drawable.ic_addon_download));
                preference.setOnPreferenceClickListener(this);
                mAdddonsList.addPreference(preference);
            }

            if (mAdddonsList.getPreferenceCount() == 0){
                preferenceScreen.removePreference(mAdddonsList);
            }

            preferenceScreen.addPreference(mInfoCategory);
            mInfoCategory.removeAll();

            if (ui.getDeveloper() != null && !ui.getDeveloper().isEmpty()){
                mDeveloperInfo.setSummary(ui.getDeveloper());
                mInfoCategory.addPreference(mDeveloperInfo);
            }

            if (ui.getWebsiteUrl() != null && !ui.getWebsiteUrl().isEmpty()){
                WEBSITE_URL = ui.getWebsiteUrl();
                mInfoCategory.addPreference(mWebsiteInfo);
            }else{
                WEBSITE_URL = "";
            }

            if (ui.getDonateUrl() != null && !ui.getDonateUrl().isEmpty()){
                DONATE_URL = ui.getDonateUrl();
                mInfoCategory.addPreference(mDonateInfo);
            }else{
                DONATE_URL = "";
            }

            if (mInfoCategory.getPreferenceCount() == 0){
                preferenceScreen.removePreference(mInfoCategory);
            }

            // Determine the preference style and create the preference
            boolean isDownloading = ui.getFileName().equals(mFileName);
            int style;

            if (isDownloading) {
                // In progress download
                style = UpdatePreference.STYLE_DOWNLOADING;
            } else if (isDownloadCompleting(ui.getFileName())) {
                style = UpdatePreference.STYLE_COMPLETING;
                mDownloading = true;
                mFileName = ui.getFileName();
            } else if (ui.getFileName().equals(installedZip)) {
                // This is the currently installed version
                style = UpdatePreference.STYLE_INSTALLED;
            } else if (ui.getDownloadUrl() != null) {
                style = UpdatePreference.STYLE_NEW;
            } else {
                style = UpdatePreference.STYLE_DOWNLOADED;
            }

            UpdatePreference up = new UpdatePreference(mContext, ui, style);
            up.setOnActionListener(this);
            up.setKey(ui.getFileName());

            // If we have an in progress download, link the preference
            if (isDownloading) {
                mDownloadingPreference = up;
                up.setOnReadyListener(this);
                mDownloading = true;
            }

            // Add to the list
            mUpdatesList.addPreference(up);
        }

        // If no updates are in the list, show the default message
        if (mUpdatesList.getPreferenceCount() == 0) {
            Preference pref = new Preference(mContext);
            pref.setLayoutResource(R.layout.preference_empty_list);
            pref.setTitle(R.string.no_available_updates_intro);
            pref.setEnabled(false);
            mUpdatesList.addPreference(pref);
        }
    }

    @Override
    public void onDeleteUpdate(UpdatePreference pref) {
        final String fileName = pref.getKey();

        if (mUpdateFolder.exists() && mUpdateFolder.isDirectory()) {
            File zipFileToDelete = new File(mUpdateFolder, fileName);

            if (zipFileToDelete.exists()) {
                zipFileToDelete.delete();
            } else {
                Log.d(TAG, "Update to delete not found");
                return;
            }

            showSnack(getString(R.string.delete_single_update_success_message, fileName));
        } else {
            showSnack(getString(mUpdateFolder.exists() ?
                    R.string.delete_updates_failure_message :
                    R.string.delete_updates_noFolder_message));
        }
        // Update the list
        updateLayout();
    }

    private void startDownload() {
        UpdateInfo ui = mDownloadingPreference.getUpdateInfo();
        if (ui == null) {
            return;
        }

        mDownloadingPreference.setStyle(UpdatePreference.STYLE_DOWNLOADING);

        mFileName = ui.getFileName();
        mDownloading = true;

        // Start the download
        Intent intent = new Intent(mContext, DownloadReceiver.class);
        intent.setAction(DownloadReceiver.ACTION_START_DOWNLOAD);
        intent.putExtra(DownloadReceiver.EXTRA_UPDATE_INFO, (Parcelable) ui);
        mContext.sendBroadcast(intent);

        mUpdateHandler.post(mUpdateProgress);
    }

    public void confirmDeleteAll() {
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_all_dialog_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // We are OK to delete, trigger it
                        deleteOldUpdates();
                        updateLayout();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private boolean deleteOldUpdates() {
        boolean success;
        //mUpdateFolder: Foldername with fullpath of SDCARD
        if (mUpdateFolder.exists() && mUpdateFolder.isDirectory()) {
            deleteDir(mUpdateFolder);
            mUpdateFolder.mkdir();
            success = true;
            showSnack(mContext.getString(R.string.delete_updates_success_message));
        } else {
            success = false;
            showSnack(mContext.getString(mUpdateFolder.exists() ?
                    R.string.delete_updates_failure_message :
                    R.string.delete_updates_noFolder_message));
        }
        return success;
    }

    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String aChildren : children) {
                boolean success = deleteDir(new File(dir, aChildren));
                if (!success) {
                    return false;
                }
            }
        }
        // The directory is now empty so delete it
        return dir.delete();
    }

    @Override
    public void onStartUpdate(UpdatePreference pref) {
        final UpdateInfo updateInfo = pref.getUpdateInfo();

        // Prevent the dialog from being triggered more than once
        if (mStartUpdateVisible) {
            return;
        }

        mStartUpdateVisible = true;

        // Get the message body right
        String dialogBody = getString(R.string.apply_update_dialog_text, updateInfo.getFileName());

        // Display the dialog
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(dialogBody)
                .setPositiveButton(R.string.dialog_update, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Utils.triggerUpdate(mContext, updateInfo.getFileName());
                        } catch (IOException e) {
                            Log.e(TAG, "Unable to reboot into recovery mode", e);
                            showSnack(mContext.getString(R.string.apply_unable_to_reboot_toast));
                        }
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Do nothing and allow the dialog to be dismissed
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        mStartUpdateVisible = false;
                    }
                })
                .show();
    }

    @Override
    public void showChangelog(String mUpdateChangelogUrl) {
        if (!Utils.isOnline(mContext)) {
            showSnack(mContext.getString(R.string.data_connection_required));
            return;
        }
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setMessage(mContext.getString(R.string.changelog_loading));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
        public void onCancel(DialogInterface dialog) {
        ((UpdateApplication) getActivity().getApplicationContext()).getQueue().cancelAll(REQUEST_TAG);
        mProgressDialog = null;
        }
        });
        mProgressDialog.show();

        StringRequest changelogRequest = new StringRequest(Request.Method.GET,
                mUpdateChangelogUrl, new Response.Listener<String>() {
 
                    @Override
                    public void onResponse(String response) {
                        if (mProgressDialog != null){
                            mProgressDialog.hide();
                            mProgressDialog = null;
                        }
                        LayoutInflater inflater = LayoutInflater.from(mContext);
                        View view = inflater.inflate(R.layout.ota_changelog_layout, null);
                        MarkdownView markdownView = (MarkdownView) view.findViewById(R.id.markdown_view);
                        markdownView.setMarkDownText(response);
                        markdownView.setOpenUrlInBrowser(true);

                        AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
                        dialog.setTitle(R.string.changelog);
                        dialog.setView(view);
                        dialog.setPositiveButton(R.string.dialog_ok, null);
                        dialog.show();
                    }
                }, new Response.ErrorListener() {
 
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        showSnack(getString(R.string.changelog_error));
                        VolleyLog.d(REQUEST_TAG, "Error: " + error.getMessage());
                        if (mProgressDialog != null){
                            mProgressDialog.hide();
                            mProgressDialog = null;
                        }
                    }
        });
        changelogRequest.setTag(REQUEST_TAG);
        changelogRequest.setShouldCache(false);

        ((UpdateApplication) getActivity().getApplicationContext()).getQueue().add(changelogRequest);
    }

    private void showSnack(String mMessage) {
        ((UpdatesActivity) getActivity()).showSnack(mMessage);
    }
}
