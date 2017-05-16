/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2017 Henrique Silva (jhenrique09)
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
import android.support.v14.preference.SwitchPreference;
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
import android.os.AsyncTask;
import android.widget.Toast;

import com.purenexus.ota.misc.Constants;
import com.purenexus.ota.misc.State;
import com.purenexus.ota.misc.UpdateInfo;
import com.purenexus.ota.receiver.DownloadReceiver;
import com.purenexus.ota.service.UpdateCheckService;
import com.purenexus.ota.utils.UpdateFilter;
import com.purenexus.ota.utils.Utils;
import com.purenexus.ota.utils.MD5;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Date;

import com.mukesh.MarkdownView;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.Volley;
import com.android.volley.toolbox.StringRequest;
import android.support.v4.app.ActivityCompat;
import android.os.Build;
import android.content.pm.PackageManager;
import android.Manifest;
import android.support.design.widget.Snackbar;

public class UpdatesSettings extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener, UpdatePreference.OnReadyListener,
        UpdatePreference.OnActionListener {
    private static String TAG = "UpdatesSettings";

    private static String REQUEST_TAG = "LoadChangelog";

    private static int DOWNLOAD_REQUEST_CODE = 9487;

    // intent extras
    public static final String EXTRA_UPDATE_LIST_UPDATED = "update_list_updated";
    public static final String EXTRA_FINISHED_DOWNLOAD_ID = "download_id";
    public static final String EXTRA_FINISHED_DOWNLOAD_PATH = "download_path";

    private static final String UPDATES_CATEGORY = "updates_category";

    private static final String EXTRAS_CATEGORY = "extras_category";
    private static final String DEVELOPER_INFO = "developer_info";
    private static final String WEBSITE_INFO = "website_info";
    private static final String DONATE_INFO = "donate_info";

    private static final String ADDONS_PREFERENCE = "addons";

    private static final String PREF_DOWNLOAD_FOLDER = "pref_download_folder";

    private static final String PREF_CHECK_MD5 = "pref_check_md5";

    private List<Map<String,String>> addons;

    private SharedPreferences mPrefs;
    private ListPreference mUpdateCheck;

    private PreferenceScreen preferenceScreen;

    private PreferenceCategory mUpdatesList;
    private UpdatePreference mDownloadingPreference;

    private PreferenceCategory mExtrasCategory;
    private PreferenceScreen mDeveloperInfo;
    private PreferenceScreen mWebsiteInfo;
    private PreferenceScreen mDonateInfo;

    private PreferenceScreen mDownloadFolder;

    private SwitchPreference mCheckMD5;

    private static String DONATE_URL = "";
    private static String WEBSITE_URL = "";

    private PreferenceScreen mAddons;

    private File mUpdateFolder;

    private Context mContext;

    private boolean mStartUpdateVisible = false;
    private boolean mChangelogVisible = false;
    private ProgressDialog mProgressDialog;

    private DownloadManager mDownloadManager;
    private boolean mDownloading = false;
    private long mDownloadId;
    private String mFileName;

    private UpdatePreference pendingDownload;

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
                    if (count > 0) {
                        showSnack(mContext.getResources().getQuantityString(R.plurals.not_new_updates_found_body,count, count),Snackbar.LENGTH_SHORT);
                    } else if (count == 0) {
                        showSnack(mContext.getString(R.string.no_updates_found),Snackbar.LENGTH_SHORT);
                    } else if (count < 0) {
                        showSnack(mContext.getString(R.string.update_check_failed),Snackbar.LENGTH_LONG);
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


        mExtrasCategory = (PreferenceCategory) findPreference(EXTRAS_CATEGORY);
        mDeveloperInfo = (PreferenceScreen) findPreference(DEVELOPER_INFO);
        mWebsiteInfo = (PreferenceScreen) findPreference(WEBSITE_INFO);
        mDonateInfo = (PreferenceScreen) findPreference(DONATE_INFO);

        mAddons = (PreferenceScreen) findPreference(ADDONS_PREFERENCE);

        mDownloadFolder = (PreferenceScreen) findPreference(PREF_DOWNLOAD_FOLDER);
        mDownloadFolder.setSummary(Utils.makeUpdateFolder().getPath());

        mCheckMD5 = (SwitchPreference) findPreference(PREF_CHECK_MD5);

        mWebsiteInfo.setOnPreferenceClickListener(this);
        mDonateInfo.setOnPreferenceClickListener(this);
        mAddons.setOnPreferenceClickListener(this);

        preferenceScreen.removePreference(mExtrasCategory);

        // Load the stored preference data
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        if (mUpdateCheck != null) {
            int check = mPrefs.getInt(Constants.UPDATE_CHECK_PREF, Constants.UPDATE_FREQ_WEEKLY);
            mUpdateCheck.setValue(String.valueOf(check));
            mUpdateCheck.setSummary(mapCheckValue(check));
            mUpdateCheck.setOnPreferenceChangeListener(this);
        }
        if (mCheckMD5 != null) {
            mCheckMD5.setChecked(mPrefs.getBoolean(Constants.CHECK_MD5_PREF, true));
            mCheckMD5.setOnPreferenceChangeListener(this);
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
        if (preference == mCheckMD5) {
            Boolean value = (Boolean)newValue;
            mPrefs.edit().putBoolean(Constants.CHECK_MD5_PREF, value).apply();
            return true;
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mWebsiteInfo) {
            try{
                Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse(WEBSITE_URL));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }catch (Exception ex){
                showSnack(getString(R.string.error_open_url),Snackbar.LENGTH_SHORT);
            }
            return true;
        }else if (preference == mDonateInfo) {
            try{
                Intent intent = new Intent(Intent.ACTION_VIEW,Uri.parse(DONATE_URL));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }catch (Exception ex){
                showSnack(getString(R.string.error_open_url),Snackbar.LENGTH_SHORT);
            }
            return true;
        }else if (preference == mAddons) {
            try{
                Intent intent = new Intent(getActivity(), AddonsActivity.class);
                intent.putExtra("addons", (ArrayList<Map<String,String>>) addons);
                getActivity().startActivity(intent);
            }catch (Exception ex){

            }
            return true;
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
                showSnack(mContext.getString(R.string.download_not_found),Snackbar.LENGTH_SHORT);
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

        if (!Utils.isOTAConfigured()){
            new AlertDialog.Builder(mContext)
            .setTitle(R.string.app_name)
            .setMessage(mContext.getString(R.string.ota_not_configured_error_message))
            .setPositiveButton(android.R.string.ok, null)
            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    getActivity().finish();
                }
            })
            .show();
        }
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
            showSnack(mContext.getString(R.string.data_connection_required),Snackbar.LENGTH_LONG);
            return;
        }

        if (mDownloading) {
            showSnack(mContext.getString(R.string.download_already_running),Snackbar.LENGTH_SHORT);
            return;
        }

        if (!isStoragePermissionGranted(DOWNLOAD_REQUEST_CODE)) {
            pendingDownload = pref;
            showSnack(mContext.getString(R.string.storage_permission_error),Snackbar.LENGTH_SHORT);
            return;
        }else{
            pendingDownload = null;
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
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!tmpZip.isFile() || tmpZip.delete()) {
                            // Set the preference back to new style
                            pref.setStyle(UpdatePreference.STYLE_NEW);
                            resetDownloadState();
                            showSnack(mContext.getString(R.string.download_cancelled),Snackbar.LENGTH_SHORT);
                        } else {
                            Log.e(TAG, "Could not delete temp zip");
                            showSnack(mContext.getString(R.string.unable_to_stop_download),Snackbar.LENGTH_SHORT);
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
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
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

                        showSnack(mContext.getString(R.string.download_cancelled),Snackbar.LENGTH_SHORT);
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

        Date d = new Date();
        mPrefs.edit().putLong(Constants.LAST_UPDATE_CHECK_PREF, d.getTime()).apply();

        // If there is no internet connection, display a message and return.
        if (!Utils.isOnline(mContext)) {
            showSnack(mContext.getString(R.string.data_connection_required),Snackbar.LENGTH_LONG);
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

        mUpdateFolder = Utils.makeUpdateFolder();
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
        preferenceScreen.removePreference(mExtrasCategory);

        boolean isUpdateAvailable = false;

        if (updates.size() > 0){
            UpdateInfo ui = updates.get(0);

            isUpdateAvailable = ui.isNewerThanInstalled();
            
            preferenceScreen.addPreference(mExtrasCategory);
            mExtrasCategory.removeAll();

            addons = ui.getAddons();
            if (addons.size() > 0){
                mExtrasCategory.addPreference(mAddons);
            }

            if (ui.getDeveloper() != null && !ui.getDeveloper().isEmpty()){
                mDeveloperInfo.setSummary(ui.getDeveloper());
                mExtrasCategory.addPreference(mDeveloperInfo);
            }

            if (ui.getWebsiteUrl() != null && !ui.getWebsiteUrl().isEmpty()){
                WEBSITE_URL = ui.getWebsiteUrl();
                mExtrasCategory.addPreference(mWebsiteInfo);
            }else{
                WEBSITE_URL = "";
            }

            if (ui.getDonateUrl() != null && !ui.getDonateUrl().isEmpty()){
                DONATE_URL = ui.getDonateUrl();
                mExtrasCategory.addPreference(mDonateInfo);
            }else{
                DONATE_URL = "";
            }

            if (mExtrasCategory.getPreferenceCount() == 0){
                preferenceScreen.removePreference(mExtrasCategory);
            }

            if (isUpdateAvailable){

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
                } else if (ui.getFileName().contains(Utils.getInstalledVersion())) {
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
        }

        // If no updates are in the list, show the default message
        if (!isUpdateAvailable) {
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

            showSnack(getString(R.string.delete_single_update_success_message, fileName),Snackbar.LENGTH_SHORT);
        } else {
            showSnack(getString(mUpdateFolder.exists() ?
                    R.string.delete_updates_failure_message :
                    R.string.delete_updates_noFolder_message),Snackbar.LENGTH_SHORT);
        }
        // Update the list
        updateLayout();
    }

    private void startDownload() {
        UpdateInfo ui = mDownloadingPreference.getUpdateInfo();
        if (ui == null) {
            return;
        }

        Utils.writeMD5File(ui.getFileName(),ui.getMD5());

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

    public void confirmRestartRecovery() {

        new AlertDialog.Builder(getActivity())
        .setTitle(R.string.restart_recovery)
        .setMessage(R.string.restart_recovery_dialog_message)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try{
                    Utils.recovery(mContext);
                }catch(Exception ex){
                }
                new AlertDialog.Builder(mContext)
                .setTitle(R.string.restart_recovery)
                .setMessage(R.string.restart_recovery_error_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
            }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
         
    }

    private boolean deleteOldUpdates() {
        boolean success;
        //mUpdateFolder: Foldername with fullpath of SDCARD
        if (mUpdateFolder.exists() && mUpdateFolder.isDirectory()) {
            Utils.deleteDir(mUpdateFolder);
            mUpdateFolder.mkdir();
            success = true;
            showSnack(mContext.getString(R.string.delete_updates_success_message),Snackbar.LENGTH_SHORT);
        } else {
            success = false;
            showSnack(mContext.getString(mUpdateFolder.exists() ?
                    R.string.delete_updates_failure_message :
                    R.string.delete_updates_noFolder_message),Snackbar.LENGTH_SHORT);
        }
        return success;
    }

    @Override
    public void onStartUpdate(UpdatePreference pref) {
        final UpdateInfo updateInfo = pref.getUpdateInfo();

        // Prevent the dialog from being triggered more than once
        if (mStartUpdateVisible) {
            return;
        }

        mStartUpdateVisible = true;
        boolean isMD5CheckAllowed = mPrefs.getBoolean(Constants.CHECK_MD5_PREF, true);

        if (isMD5CheckAllowed){
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setMessage(mContext.getString(R.string.checking_md5));
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();

            new AsyncTask<Void, Void, Boolean>() {
                protected Boolean doInBackground(Void... params) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                    File updateFile = new File(Utils.makeUpdateFolder().getPath() + "/" + updateInfo.getFileName());
                    return MD5.checkMD5(Utils.readMD5File(updateInfo.getFileName()),updateFile);
                }

                protected void onPostExecute(Boolean result) {
                    if (mProgressDialog != null){
                        mProgressDialog.hide();
                        mProgressDialog = null;
                    }
                    if (result){
                        showInstallDialog(updateInfo);
                    }else{

                        new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.md5_failed_dialog_title)
                        .setMessage(mContext.getString(R.string.md5_failed_dialog_message))
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                showInstallDialog(updateInfo);
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
                }
            }.execute();

        }else{
            showInstallDialog(updateInfo);
        }

    }

    private void showInstallDialog(UpdateInfo updateInfo){
        mStartUpdateVisible = false;
        try{
            Intent intent = new Intent(getActivity(), InstallActivity.class);
            intent.putExtra("rom", Utils.makeUpdateFolder().getPath() + "/" + updateInfo.getFileName());
            getActivity().startActivity(intent);
        }catch (Exception ex){

        }
    }

    @Override
    public void showChangelog(String mUpdateChangelogUrl) {
        if (mChangelogVisible){
            return;
        }
        if (!Utils.isOnline(mContext)) {
            showSnack(mContext.getString(R.string.data_connection_required),Snackbar.LENGTH_LONG);
            mChangelogVisible = false;
            return;
        }
        mChangelogVisible = true;
        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setMessage(mContext.getString(R.string.changelog_loading));
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(true);
        mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
        public void onCancel(DialogInterface dialog) {
        ((UpdateApplication) getActivity().getApplicationContext()).getQueue().cancelAll(REQUEST_TAG);
        mProgressDialog = null;
        mChangelogVisible = false;
        }
        });
        mProgressDialog.show();

        StringRequest changelogRequest = new StringRequest(Request.Method.GET,
                mUpdateChangelogUrl, new Response.Listener<String>() {
 
                    @Override
                    public void onResponse(String response) {
                        try{
                            if (mProgressDialog != null){
                                mProgressDialog.hide();
                                mProgressDialog = null;
                            }
                            LayoutInflater inflater = LayoutInflater.from(mContext);
                            View view = inflater.inflate(R.layout.ota_changelog_layout, null);
                            MarkdownView markdownView = (MarkdownView) view.findViewById(R.id.markdown_view);
                            markdownView.setMarkDownText(response);
                            markdownView.setOpenUrlInBrowser(true);

                            new AlertDialog.Builder(mContext)
                            .setTitle(R.string.changelog)
                            .setView(view)
                            .setPositiveButton(android.R.string.ok, null)
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialog) {
                                    mChangelogVisible = false;
                                }
                            })
                            .show();
                        }catch(Exception ex){
                            mChangelogVisible = false;
                        }
                    }
                }, new Response.ErrorListener() {
 
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        mChangelogVisible = false;
                        showSnack(getString(R.string.changelog_error),Snackbar.LENGTH_SHORT);
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

    private void showSnack(String mMessage, int length) {
        ((UpdatesActivity) getActivity()).showSnack(mMessage, length);
    }

    public boolean isStoragePermissionGranted(int requestCode) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (getActivity().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }else{
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
                return false;
            }
        }else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == DOWNLOAD_REQUEST_CODE && grantResults[0]== PackageManager.PERMISSION_GRANTED){
            mDownloadingPreference = pendingDownload;
            startDownload();
        }
    }
}
