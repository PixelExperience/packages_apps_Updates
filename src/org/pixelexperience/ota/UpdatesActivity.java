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
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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
import org.pixelexperience.ota.controller.UpdaterController;
import org.pixelexperience.ota.controller.UpdaterService;
import org.pixelexperience.ota.download.DownloadClient;
import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.UpdateInfo;
import org.pixelexperience.ota.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UpdatesActivity extends UpdatesListActivity {

    private static final String TAG = "UpdatesActivity";
    private UpdaterService mUpdaterService;
    private BroadcastReceiver mBroadcastReceiver;

    private UpdatesListAdapter mAdapter;

    private ExtrasFragment mExtrasFragment;
    private SwipeRefreshLayout mSwipeRefresh;
    private Button mRefreshButton;
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
        mAdapter.onRequestPermissionsResult(requestCode, grantResults);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updates);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        mAdapter = new UpdatesListAdapter(this);
        recyclerView.setAdapter(mAdapter);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (UpdaterController.ACTION_UPDATE_STATUS.equals(intent.getAction())) {
                    UpdateStatus status = (UpdateStatus) intent.getSerializableExtra(UpdaterController.EXTRA_STATUS);
                    handleDownloadStatusChange(status);
                    mAdapter.notifyDataSetChanged();
                } else if (UpdaterController.ACTION_NETWORK_UNAVAILABLE.equals(intent.getAction())) {
                    showSnackbar(R.string.snack_download_failed, Snackbar.LENGTH_LONG);
                } else if (UpdaterController.ACTION_DOWNLOAD_PROGRESS.equals(intent.getAction()) ||
                        UpdaterController.ACTION_INSTALL_PROGRESS.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.notifyItemChanged(downloadId);
                } else if (UpdaterController.ACTION_UPDATE_REMOVED.equals(intent.getAction())) {
                    String downloadId = intent.getStringExtra(UpdaterController.EXTRA_DOWNLOAD_ID);
                    mAdapter.removeItem(downloadId);
                    hideUpdates();
                    downloadUpdatesList(false);
                }else if (ExportUpdateService.ACTION_EXPORT_STATUS.equals(intent.getAction())){
                    int status = intent.getIntExtra(ExportUpdateService.EXTRA_EXPORT_STATUS, -1);
                    handleExportStatusChanged(status);
                }else if (UpdaterController.ACTION_UPDATE_CLEANUP_IN_PROGRESS.equals(intent.getAction())) {
                    hideUpdates();
                    refreshAnimationStart();
                }else if (UpdaterController.ACTION_UPDATE_CLEANUP_DONE.equals(intent.getAction())) {
                    cleanupUpdates();
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
    }

    private void handleExportStatusChanged(int status){
        switch(status){
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
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            Intent intent = new Intent(this, UpdaterService.class);
            startService(intent);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } catch (IllegalStateException ignored) {

        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_STATUS);
        intentFilter.addAction(UpdaterController.ACTION_DOWNLOAD_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_INSTALL_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_REMOVED);
        intentFilter.addAction(UpdaterController.ACTION_NETWORK_UNAVAILABLE);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_CLEANUP_IN_PROGRESS);
        intentFilter.addAction(UpdaterController.ACTION_UPDATE_CLEANUP_DONE);
        intentFilter.addAction(ExportUpdateService.ACTION_EXPORT_STATUS);
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
        findViewById(R.id.no_new_updates_view).setVisibility(View.GONE);
        findViewById(R.id.recycler_view).setVisibility(View.VISIBLE);
    }

    private void loadUpdatesList(File jsonFile, boolean manualRefresh)
            throws IOException, JSONException {
        mExtrasFragment.updatePrefs(Utils.parseJson(jsonFile, false));
        Log.d(TAG, "Adding remote updates");
        UpdaterController controller = mUpdaterService.getUpdaterController();

        UpdateInfo newUpdate = Utils.parseJson(jsonFile, true);
        boolean updateAvailable = newUpdate != null && controller.addUpdate(newUpdate);

        if (manualRefresh) {
            showSnackbar(
                    updateAvailable ? R.string.update_found_notification : R.string.snack_no_updates_found,
                    Snackbar.LENGTH_SHORT);
        }

        List<String> updateIds = new ArrayList<>();
        List<UpdateInfo> sortedUpdates = controller.getUpdates();
        hideUpdates();
        if (newUpdate != null && Utils.isCompatible(newUpdate) && !sortedUpdates.isEmpty()) {
            sortedUpdates.sort((u1, u2) -> Long.compare(u2.getTimestamp(), u1.getTimestamp()));
            for (UpdateInfo update : sortedUpdates) {
                if (Utils.isCompatible(update)) {
                    updateIds.add(update.getDownloadId());
                    showUpdates();
                    break; // Limit to 1
                }
            }
            mAdapter.setData(updateIds);
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
        } else {
            downloadUpdatesList(false);
        }
    }

    private void processNewJson(File json, File jsonNew, boolean manualRefresh) {
        try {
            loadUpdatesList(jsonNew, manualRefresh);
            if (json.exists() && Utils.checkForNewUpdates(json, jsonNew, false)) {
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
                    .setUseIncremental(Utils.shouldUseIncremental(this))
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
        mSwipeRefresh.setRefreshing(true);
        mRefreshButton.setEnabled(false);
    }

    private void refreshAnimationStop() {
        if (mRefreshButton == null || mSwipeRefresh == null) {
            setupRefreshComponents();
        }
        mSwipeRefresh.setRefreshing(false);
        mRefreshButton.setEnabled(true);
    }

    private void handleDownloadStatusChange(UpdateStatus status) {
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

    @Override
    public void showSnackbar(int stringId, int duration) {
        Snackbar snack = Snackbar.make(findViewById(R.id.view_snackbar), stringId, duration);
        TextView tv = snack.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        tv.setTextColor(getColor(R.color.text_primary));
        snack.show();
    }

    private void handleABInstallationFailed(){
        if (Utils.shouldUseIncremental(this)){
            new AlertDialog.Builder(this, R.style.AppTheme_AlertDialogStyle)
                    .setTitle(R.string.installing_update_error)
                    .setMessage(R.string.installing_update_ab_disable_incremental_summary)
                    .setPositiveButton(R.string.action_download,
                            (dialog, which) -> {
                                UpdaterController controller = mUpdaterService.getUpdaterController();
                                controller.setShouldUseIncremental(false);
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }else{
            showSnackbar(R.string.installing_update_error, Snackbar.LENGTH_LONG);
        }
    }

    private void cleanupUpdates(){
        mAdapter.setData(new ArrayList<>());
        mAdapter.notifyDataSetChanged();
        downloadUpdatesList(false);
    }
}
