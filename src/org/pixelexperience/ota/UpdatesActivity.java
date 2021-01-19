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
package org.pixelexperience.ota;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.pixelexperience.ota.controller.ABUpdateInstaller;
import org.pixelexperience.ota.controller.UpdaterController;
import org.pixelexperience.ota.controller.UpdaterService;
import org.pixelexperience.ota.download.DownloadClient;
import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.misc.PermissionsUtils;
import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.UpdateInfo;
import org.pixelexperience.ota.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static org.pixelexperience.ota.model.UpdateStatus.UNKNOWN;

public class UpdatesActivity extends UpdatesListActivity {

    private static final String TAG = "UpdatesActivity";

    public static final String ACTION_START_DOWNLOAD_WITH_WARNING = "action_start_download_with_warning";

    public static final String ACTION_SHOW_SNACKBAR = "action_show_snackbar";
    public static final String EXTRA_SNACKBAR_TEXT = "extra_snackbar_text";

    public static final String ACTION_EXPORT_UPDATE = "action_export_update";
    public static final String EXTRA_UPDATE_NAME = "extra_snackbar_text";
    public static final String EXTRA_UPDATE_FILE = "extra_update_file";

    private String mExportUpdateName;
    private File mExportUpdateFile;

    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    private UpdatesListAdapter mAdapter;

    private ExtrasFragment mExtrasFragment;
    private SwipeRefreshLayout mSwipeRefresh;
    private Button mRefreshButton;
    private UpdateStatus mUpdateStatus;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            UpdaterService.LocalBinder binder = (UpdaterService.LocalBinder) service;
            mUpdaterService = binder.getService();
            mAdapter.setUpdaterController(mUpdaterService.getUpdaterController());
            getUpdatesList();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mAdapter.setUpdaterController(null);
            mUpdaterService = null;
            mAdapter.notifyDataSetChanged();
        }
    };
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                requestCode == ExportUpdateService.EXPORT_STATUS_PERMISSION_REQUEST_CODE) {
            exportUpdate();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        mAdapter = new UpdatesListAdapter();
        recyclerView.setAdapter(mAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        if (ABUpdateInstaller.needsReboot()) {
            return;
        }

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    UpdateStatus status = (UpdateStatus) intent.getSerializableExtra(UpdaterController.EXTRA_STATUS);
                    handleStatusChange(status);
                    mAdapter.notifyDataSetChanged();
                } else if (UpdaterController.ACTION_NETWORK_UNAVAILABLE.equals(intent.getAction())) {
                    showSnackbar(R.string.snack_download_failed, Snackbar.LENGTH_LONG);
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    mAdapter.notifyUpdateChanged();
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    mAdapter.removeUpdate();
                    hideUpdates();
                    downloadUpdatesList(false);
                }else if (ExportUpdateService.ACTION_EXPORT_STATUS.equals(intent.getAction())){
                    int status = intent.getIntExtra(ExportUpdateService.EXTRA_EXPORT_STATUS, -1);
                    handleExportStatusChanged(status);
                } else if (ABUpdateInstaller.ACTION_RESTART_PENDING.equals(intent.getAction())) {
                    hideUpdates();
                    showRestartPendingDialog();
                } else if (ACTION_START_DOWNLOAD_WITH_WARNING.equals(intent.getAction())) {
                    startDownloadWithWarning();
                } else if (ACTION_SHOW_SNACKBAR.equals(intent.getAction())) {
                    showSnackbar(intent.getStringExtra(EXTRA_SNACKBAR_TEXT), Snackbar.LENGTH_LONG);
                } else if (ACTION_EXPORT_UPDATE.equals(intent.getAction())) {
                    mExportUpdateName = intent.getStringExtra(EXTRA_UPDATE_NAME);
                    mExportUpdateFile = (File) intent.getSerializableExtra(EXTRA_UPDATE_FILE);
                    exportUpdate();
                }
            }
        };

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mExtrasFragment = new ExtrasFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.extras_view, mExtrasFragment)
                .commit();

        setupRefreshComponents();
        refreshAnimationStart();
    }

    private void showRestartPendingDialog() {
        new AlertDialog.Builder(UpdatesActivity.this, R.style.AppTheme_AlertDialogStyle)
                .setTitle(R.string.reboot_needed_dialog_title)
                .setMessage(R.string.reboot_needed_dialog_summary)
                .setPositiveButton(R.string.reboot, (dialog, which) -> Utils.rebootDevice(UpdatesActivity.this))
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(false)
                .setOnDismissListener(dialog -> finish()).show();
    }

    private void handleExportStatusChanged(int status) {
        switch (status) {
            case ExportUpdateService.EXPORT_STATUS_RUNNING:
                showSnackbar(R.string.dialog_export_title, Snackbar.LENGTH_SHORT);
                break;
            case ExportUpdateService.EXPORT_STATUS_ALREADY_RUNNING:
                showSnackbar(R.string.toast_already_exporting, Snackbar.LENGTH_SHORT);
                break;
            case ExportUpdateService.EXPORT_STATUS_SUCCESS:
                showSnackbar(R.string.notification_export_success, Snackbar.LENGTH_SHORT);
                break;
            case ExportUpdateService.EXPORT_STATUS_FAILED:
                showSnackbar(R.string.notification_export_fail, Snackbar.LENGTH_SHORT);
                break;
            default:
                break;
        }
    }

    private void setupRefreshComponents() {
        mRefreshButton = findViewById(R.id.check);
        mRefreshButton.setOnClickListener(view -> downloadUpdatesList(true));
        mSwipeRefresh = findViewById(R.id.swiperefresh);
        mSwipeRefresh.setEnabled(false);
        mRefreshButton.setEnabled(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            Intent intent = new Intent(this, UpdaterService.class);
            startService(intent);
            if (ABUpdateInstaller.needsReboot()) {
                showRestartPendingDialog();
                return;
            }
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } catch (IllegalStateException ignored) {
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        intentFilter.addAction(UpdaterController.ACTION_NETWORK_UNAVAILABLE);
        intentFilter.addAction(ExportUpdateService.ACTION_EXPORT_STATUS);
        intentFilter.addAction(ABUpdateInstaller.ACTION_RESTART_PENDING);
        intentFilter.addAction(ACTION_START_DOWNLOAD_WITH_WARNING);
        intentFilter.addAction(ACTION_SHOW_SNACKBAR);
        intentFilter.addAction(ACTION_EXPORT_UPDATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        if (mUpdaterService != null) {
            unbindService(mConnection);
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_show_changelog: {
                startActivity(new Intent(this, LocalChangelogActivity.class));
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void hideUpdates() {
        findViewById(R.id.no_new_updates_view).setVisibility(View.VISIBLE);
        findViewById(R.id.recycler_view).setVisibility(View.GONE);
    }

    private void showUpdates() {
        if (ABUpdateInstaller.needsReboot()){
            return;
        }
        findViewById(R.id.no_new_updates_view).setVisibility(View.GONE);
        findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
    }

    private void loadUpdatesList(File jsonFile, boolean manualRefresh)
            throws IOException, JSONException {
        mExtrasFragment.updatePrefs(Utils.parseJson(jsonFile, false, this));
        Log.d(TAG, "Adding remote updates");
        UpdaterController controller = mUpdaterService.getUpdaterController();

        UpdateInfo newUpdate = Utils.parseJson(jsonFile, true, this);
        boolean updateAvailable = newUpdate != null && controller.addUpdate(newUpdate);

        if (manualRefresh) {
            showSnackbar(
                    updateAvailable ? R.string.update_found_notification : R.string.snack_no_updates_found,
                    Snackbar.LENGTH_SHORT);
        }
        hideUpdates();
        if (newUpdate != null) {
            showUpdates();
            mAdapter.setDownloadId(newUpdate.getDownloadId());
            mAdapter.notifyDataSetChanged();
        }
    }

    private void getUpdatesList() {
        File jsonFile = Utils.getCachedUpdateList(this);
        if (jsonFile.exists()) {
            try {
                loadUpdatesList(jsonFile, false);
                Log.d(TAG, "Cached list parsed");
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error while parsing json list", e);
            }
            refreshAnimationStop();
        }else{
            downloadUpdatesList(false);
        }
    }

    private void processNewJson(File json, File jsonNew, boolean manualRefresh) {
        try {
            loadUpdatesList(jsonNew, manualRefresh);
            if (json.exists() && Utils.checkForNewUpdates(json, jsonNew, false, this)) {
                UpdatesCheckReceiver.updateRepeatingUpdatesCheck(this);
            }
            // In case we set a one-shot check because of a previous failure
            UpdatesCheckReceiver.cancelUpdatesCheck(this);
            jsonNew.renameTo(json);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not read json", e);
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
        }
    }

    private void downloadUpdatesList(final boolean manualRefresh) {
        final File jsonFile = Utils.getCachedUpdateList(this);
        final File jsonFileTmp = new File(jsonFile.getAbsolutePath() + UUID.randomUUID());
        String url = Utils.getServerURL();
        Log.d(TAG, "Checking " + url);

        DownloadClient.DownloadCallback callback = new DownloadClient.DownloadCallback() {
            @Override
            public void onFailure(final boolean cancelled) {
                Log.e(TAG, "Could not download updates list");
                runOnUiThread(() -> {
                    if (!cancelled) {
                        showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
                    }
                    new Handler().postDelayed(() -> {
                        showUpdates();
                    }, 1000);
                    refreshAnimationStop();
                });
            }

            @Override
            public void onResponse(int statusCode, String url,
                                   DownloadClient.Headers headers) {
            }

            @Override
            public void onSuccess(File destination) {
                runOnUiThread(() -> {
                    Log.d(TAG, "List downloaded");
                    processNewJson(jsonFile, jsonFileTmp, manualRefresh);
                    refreshAnimationStop();
                });
            }
        };

        final DownloadClient downloadClient;
        try {
            downloadClient = new DownloadClient.Builder()
                    .setUrl(url)
                    .setDestination(jsonFileTmp)
                    .setDownloadCallback(callback)
                    .build();
        } catch (IOException exception) {
            Log.e(TAG, "Could not build download client");
            showSnackbar(R.string.snack_updates_check_failed, Snackbar.LENGTH_LONG);
            return;
        }
        refreshAnimationStart();
        downloadClient.start();
    }

    private void refreshAnimationStart() {
        if (mRefreshButton == null || mSwipeRefresh == null) {
            setupRefreshComponents();
        }
        mRefreshButton.setEnabled(false);
        mSwipeRefresh.setRefreshing(true);
        findViewById(R.id.recycler_view).setVisibility(View.GONE);
    }

    private void refreshAnimationStop() {
        if (mRefreshButton == null || mSwipeRefresh == null) {
            setupRefreshComponents();
        }
        new Handler().postDelayed(() -> {
            if (mRefreshButton == null || mSwipeRefresh == null) {
                return;
            }
            mSwipeRefresh.setRefreshing(false);
            handleRefreshButtonState();
        }, 1000);
    }

    private void handleStatusChange(UpdateStatus status) {
        if (mUpdateStatus == status){
            return;
        }
        if (mUpdateStatus == null){
            mUpdateStatus = UNKNOWN;
        }
        mUpdateStatus = status;
        handleRefreshButtonState();
        switch (status) {
            case DOWNLOAD_ERROR:
                showSnackbar(R.string.snack_download_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFICATION_FAILED:
                showSnackbar(R.string.snack_download_verification_failed, Snackbar.LENGTH_LONG);
                break;
            case VERIFIED:
                showSnackbar(R.string.snack_download_verified, Snackbar.LENGTH_LONG);
                break;
            case INSTALLATION_FAILED:
                if (Utils.isABDevice()){
                    handleABInstallationFailed();
                }else{
                    showSnackbar(R.string.installing_update_error, Snackbar.LENGTH_LONG);
                }
                break;
        }
    }

    private void handleRefreshButtonState() {
        if (mUpdateStatus == null){
            mUpdateStatus = UNKNOWN;
        }
        switch (mUpdateStatus) {
            case UNKNOWN:
            case DOWNLOAD_ERROR:
            case DELETED:
            case VERIFICATION_FAILED:
                Log.d(TAG, "handleRefreshButtonState, status is: " + mUpdateStatus + ", enabling button");
                mRefreshButton.setEnabled(true);
                break;
            default:
                Log.d(TAG, "handleRefreshButtonState, status is: " + mUpdateStatus + ", disabling button");
                mRefreshButton.setEnabled(false);
        }
    }

    @Override
    public void showSnackbar(int stringId, int duration) {
        Snackbar snack = Snackbar.make(findViewById(R.id.view_snackbar), stringId, duration);
        TextView tv = snack.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        tv.setTextColor(getColor(R.color.text_primary));
        snack.show();
    }

    @Override
    public void showSnackbar(String text, int duration) {
        Snackbar snack = Snackbar.make(findViewById(R.id.view_snackbar), text, duration);
        TextView tv = snack.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        tv.setTextColor(getColor(R.color.text_primary));
        snack.show();
    }

    private void handleABInstallationFailed() {
        showSnackbar(R.string.installing_update_error, Snackbar.LENGTH_LONG);
    }

    private void startDownloadWithWarning() {
        UpdaterController updaterController = mUpdaterService.getUpdaterController();
        if (!Utils.isNetworkAvailable(this)) {
            updaterController.notifyNetworkUnavailable();
            return;
        }
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean warn = preferences.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true);
        if (Utils.isOnWifiOrEthernet(this) || !warn) {
            updaterController.startDownload();
            return;
        }

        View checkboxView = LayoutInflater.from(this).inflate(R.layout.checkbox_view, null);
        CheckBox checkbox = checkboxView.findViewById(R.id.checkbox);
        checkbox.setText(R.string.checkbox_mobile_data_warning);

        new AlertDialog.Builder(this, R.style.AppTheme_AlertDialogStyle)
                .setTitle(R.string.update_on_mobile_data_title)
                .setMessage(R.string.update_on_mobile_data_message)
                .setView(checkboxView)
                .setPositiveButton(R.string.action_download,
                        (dialog, which) -> {
                            if (checkbox.isChecked()) {
                                preferences.edit()
                                        .putBoolean(Constants.PREF_MOBILE_DATA_WARNING, false)
                                        .apply();
                                supportInvalidateOptionsMenu();
                            }
                            updaterController.startDownload();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void exportUpdate() {
        boolean hasPermission = PermissionsUtils.checkAndRequestStoragePermission(
                this, ExportUpdateService.EXPORT_STATUS_PERMISSION_REQUEST_CODE);
        if (!hasPermission) {
            return;
        }
        File dest = new File(Utils.getExportPath(), mExportUpdateName);
        Intent intent = new Intent(this, ExportUpdateService.class);
        intent.setAction(ExportUpdateService.ACTION_START_EXPORTING);
        intent.putExtra(ExportUpdateService.EXTRA_SOURCE_FILE, mExportUpdateFile);
        intent.putExtra(ExportUpdateService.EXTRA_DEST_FILE, dest);
        startService(intent);
    }
}
