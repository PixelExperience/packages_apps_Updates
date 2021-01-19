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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.BatteryManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import org.pixelexperience.ota.controller.UpdaterController;
import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.misc.StringGenerator;
import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.UpdateInfo;
import org.pixelexperience.ota.model.UpdateStatus;

import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;

public class UpdatesListAdapter extends RecyclerView.Adapter<UpdatesListAdapter.ViewHolder> {

    private static final String TAG = "UpdateListAdapter";

    private static final int BATTERY_PLUGGED_ANY = BatteryManager.BATTERY_PLUGGED_AC
            | BatteryManager.BATTERY_PLUGGED_USB
            | BatteryManager.BATTERY_PLUGGED_WIRELESS;

    private String mDownloadId = null;
    private UpdaterController mUpdaterController;
    private UpdateInfo mUpdate;

    private AlertDialog infoDialog;

    private Context mContext;
    private LocalBroadcastManager mBroadcastManager;

    UpdatesListAdapter() {
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.update_item_view, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onViewDetachedFromWindow(ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (infoDialog != null) {
            infoDialog.dismiss();
        }
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        mContext = recyclerView.getContext();
        mBroadcastManager = LocalBroadcastManager.getInstance(mContext);
    }

    void setUpdaterController(UpdaterController updaterController) {
        mUpdaterController = updaterController;
        notifyDataSetChanged();
    }

    @SuppressLint("SetTextI18n")
    private void handleActiveStatus(ViewHolder viewHolder) {
        boolean canDelete = false;

        if (mUpdaterController.isDownloading()) {
            canDelete = mUpdate.getStatus() != UpdateStatus.STARTING;
            long length = mUpdate.getFile() != null &&
                    mUpdate.getStatus() != UpdateStatus.STARTING ?
                    mUpdate.getFile().length() : 0;
            String downloaded = Utils.readableFileSize(length);
            String total = Utils.readableFileSize(mUpdate.getFileSize());
            String percentage = NumberFormat.getPercentInstance().format(
                    mUpdate.getProgress() / 100.f);
            long eta = mUpdate.getEta();
            if (eta > 0) {
                CharSequence etaString = StringGenerator.formatETA(mContext, eta * 1000);
                viewHolder.mProgressText.setText(mContext.getString(
                        R.string.list_download_progress_eta_new, downloaded, total, etaString,
                        percentage));
            } else {
                viewHolder.mProgressText.setText(mContext.getString(
                        R.string.list_download_progress_new, downloaded, total, percentage));
            }
            setButtonAction(viewHolder.mAction, Action.PAUSE, mUpdate.getStatus() != UpdateStatus.STARTING);
            viewHolder.mDetails.setVisibility(View.GONE);
            viewHolder.mProgressBar.setIndeterminate(mUpdate.getStatus() == UpdateStatus.STARTING);
            viewHolder.mProgressBar.setProgress(mUpdate.getProgress());
        } else if (mUpdaterController.isInstallingUpdate()) {
            viewHolder.mDetails.setVisibility(View.GONE);
            setButtonAction(viewHolder.mAction, Action.INSTALL, mUpdate.getStatus() == UpdateStatus.INSTALLATION_FAILED);
            boolean notAB = !mUpdaterController.isInstallingABUpdate();
            viewHolder.mProgressText.setText(notAB ? R.string.dialog_prepare_zip_message :
                    mUpdate.getFinalizing() ?
                            R.string.finalizing_package :
                            R.string.installing_update);
            viewHolder.mProgressBar.setIndeterminate(mUpdate.getInstallProgress() == 0);
            viewHolder.mProgressBar.setProgress(mUpdate.getInstallProgress());
        } else if (mUpdaterController.isVerifyingUpdate()) {
            viewHolder.mDetails.setVisibility(View.GONE);
            setButtonAction(viewHolder.mAction, Action.INSTALL, false);
            viewHolder.mProgressText.setText(R.string.list_verifying_update);
            viewHolder.mProgressBar.setIndeterminate(true);
        } else {
            canDelete = mUpdate.getStatus() != UpdateStatus.STARTING;
            viewHolder.mDetails.setVisibility(View.GONE);
            setButtonAction(viewHolder.mAction, Action.RESUME, !isBusy());
            String downloaded = Utils.readableFileSize(mUpdate.getFile() != null && mUpdate.getStatus() != UpdateStatus.STARTING ? mUpdate.getFile().length() : 0);
            String total = Utils.readableFileSize(mUpdate.getFileSize());
            String percentage = NumberFormat.getPercentInstance().format(
                    mUpdate.getProgress() / 100.f);
            viewHolder.mProgressText.setText(mContext.getString(R.string.list_download_progress_new,
                    downloaded, total, percentage));
            viewHolder.mProgressBar.setIndeterminate(false);
            viewHolder.mProgressBar.setProgress(mUpdate.getProgress());
        }
        viewHolder.mBuildName.setSelected(false);
        setupOptionMenuListeners(canDelete, viewHolder);
        viewHolder.mProgressBar.setVisibility(View.VISIBLE);
        viewHolder.mProgressText.setVisibility(View.VISIBLE);
        viewHolder.mBuildSize.setVisibility(View.GONE);
    }

    private void handleNotActiveStatus(ViewHolder viewHolder) {
        if (Utils.getPersistentStatus(mContext) == UpdateStatus.Persistent.VERIFIED) {
            setupOptionMenuListeners(true, viewHolder);
            setButtonAction(viewHolder.mAction,
                    Utils.canInstall(mUpdate) ? Action.INSTALL : Action.DELETE, !isBusy());
            viewHolder.mDetails.setVisibility(View.GONE);
        } else if (!Utils.canInstall(mUpdate)) {
            setupOptionMenuListeners(false, viewHolder);
            setButtonAction(viewHolder.mAction, Action.INFO, !isBusy());
            viewHolder.mDetails.setVisibility(View.GONE);
        } else {
            setupOptionMenuListeners(false, viewHolder);
            setButtonAction(viewHolder.mAction, Action.DOWNLOAD, !isBusy());
            viewHolder.mDetails.setVisibility(View.VISIBLE);
        }
        String fileSize = Utils.readableFileSize(mUpdate.getFileSize());
        viewHolder.mBuildSize.setText(fileSize);

        viewHolder.mProgressBar.setVisibility(View.GONE);
        viewHolder.mProgressText.setVisibility(View.GONE);
        viewHolder.mBuildSize.setVisibility(View.VISIBLE);
        viewHolder.mBuildName.setSelected(true);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, int i) {
        if (mDownloadId == null) {
            viewHolder.mAction.setEnabled(false);
            return;
        }

        mUpdate = mUpdaterController.getCurrentUpdate();
        if (mUpdate == null) {
            // The update was deleted
            viewHolder.mAction.setEnabled(false);
            viewHolder.mAction.setText(R.string.action_download);
            return;
        }

        viewHolder.itemView.setSelected(true);

        boolean activeLayout;
        switch (Utils.getPersistentStatus(mContext)) {
            case UpdateStatus.Persistent.UNKNOWN:
                activeLayout = mUpdate.getStatus() == UpdateStatus.STARTING;
                break;
            case UpdateStatus.Persistent.VERIFIED:
                activeLayout = mUpdate.getStatus() == UpdateStatus.INSTALLING &&
                        mUpdate.getStatus() != UpdateStatus.INSTALLATION_FAILED;
                break;
            case UpdateStatus.Persistent.DOWNLOADING:
            case UpdateStatus.Persistent.STARTING_DOWNLOAD:
                activeLayout = true;
                break;
            default:
                throw new RuntimeException("Unknown update status");
        }

        String buildDate = StringGenerator.getDateLocalizedUTC(mContext,
                DateFormat.LONG, mUpdate.getTimestamp());
        String buildVersion = mUpdate.getName();
        viewHolder.mBuildDate.setText(buildDate);
        viewHolder.mBuildName.setText(buildVersion);
        viewHolder.mBuildName.setCompoundDrawables(null, null, null, null);
        viewHolder.mDetails.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Utils.getDownloadWebpageUrl(mUpdate.getName())));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } catch (Exception ex) {
                showSnackbar(mContext.getString(R.string.error_open_url));
            }
        });

        if (activeLayout) {
            handleActiveStatus(viewHolder);
        } else {
            handleNotActiveStatus(viewHolder);
        }
    }

    @Override
    public int getItemCount() {
        return mDownloadId == null ? 0 : 1;
    }

    public void setDownloadId(String downloadId) {
        mDownloadId = downloadId;
    }

    void notifyUpdateChanged() {
        if (mDownloadId == null) {
            return;
        }
        notifyItemChanged(0);
    }

    void removeUpdate() {
        if (mDownloadId == null) {
            return;
        }
        notifyItemRemoved(0);
        notifyItemRangeChanged(0, getItemCount());
    }

    private void startDownloadWithWarning() {
        mBroadcastManager.sendBroadcast(new Intent(UpdatesActivity.ACTION_START_DOWNLOAD_WITH_WARNING));
    }

    private void setButtonAction(Button button, Action action, boolean enabled) {
        final View.OnClickListener clickListener;
        switch (action) {
            case DOWNLOAD:
                button.setText(R.string.action_download);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_download, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> startDownloadWithWarning() : null;
                break;
            case PAUSE:
                button.setText(R.string.action_pause);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> mUpdaterController.pauseDownload()
                        : null;
                break;
            case RESUME: {
                button.setText(R.string.action_resume);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_resume, 0, 0, 0);
                button.setEnabled(enabled);
                UpdateInfo update = mUpdaterController.getCurrentUpdate();
                final boolean canInstall = Utils.canInstall(update) ||
                        update.getFile().length() == update.getFileSize();
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        mUpdaterController.resumeDownload();
                    } else {
                        showSnackbar(mContext.getString(R.string.snack_update_not_installable));
                    }
                } : null;
            }
            break;
            case INSTALL: {
                button.setText(R.string.action_install);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_install, 0, 0, 0);
                button.setEnabled(enabled);
                UpdateInfo update = mUpdaterController.getCurrentUpdate();
                final boolean canInstall = Utils.canInstall(update);
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        getInstallDialog().show();
                    } else {
                        showSnackbar(mContext.getString(R.string.snack_update_not_installable));
                    }
                } : null;
            }
            break;
            case INFO: {
                button.setText(R.string.details_button);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_information, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> showInfoDialog() : null;
            }
            break;
            case DELETE: {
                button.setText(R.string.action_delete);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_delete, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> getDeleteDialog().show() : null;
            }
            break;
            default:
                clickListener = null;
                button.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
        button.setAlpha(enabled ? 1.f : 0.5f);

        // Disable action mode when a button is clicked
        button.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onClick(v);
            }
        });
    }

    private boolean isBusy() {
        return mUpdaterController.hasActiveDownloads() || mUpdaterController.isVerifyingUpdate()
                || mUpdaterController.isInstallingUpdate();
    }

    private AlertDialog.Builder getDeleteDialog() {
        return new AlertDialog.Builder(mContext, R.style.AppTheme_AlertDialogStyle)
                .setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            mUpdaterController.pauseDownload();
                            mUpdaterController.removeUpdateAndNotify();
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    @SuppressLint("RestrictedApi")
    private void setupOptionMenuListeners(final boolean canDelete, ViewHolder viewHolder) {
        ContextThemeWrapper wrapper = new ContextThemeWrapper(mContext,
                R.style.AppTheme_PopupMenuOverlapAnchor);
        PopupMenu popupMenu = new PopupMenu(wrapper, viewHolder.mBuildDate, Gravity.NO_GRAVITY,
                R.attr.actionOverflowMenuStyle, 0);
        popupMenu.inflate(R.menu.menu_action_mode);

        MenuBuilder menu = (MenuBuilder) popupMenu.getMenu();
        MenuItem deleteAction = menu.findItem(R.id.menu_delete_action);
        MenuItem exportAction = menu.findItem(R.id.menu_export_update);

        deleteAction.setVisible(canDelete);
        exportAction.setVisible(
                Utils.getPersistentStatus(mContext) == UpdateStatus.Persistent.VERIFIED);

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.menu_delete_action:
                    getDeleteDialog().show();
                    return true;
                case R.id.menu_export_update:
                    exportUpdate();
                    return true;
            }
            return false;
        });
        viewHolder.itemView.setOnLongClickListener(v -> {
            new MenuPopupHelper(wrapper, menu, viewHolder.mBuildDate).show();
            return true;
        });
        viewHolder.mOptionsButton.setOnClickListener(v -> new MenuPopupHelper(wrapper, menu, viewHolder.mOptionsButton).show());

        boolean isOneMenuItemVisible = deleteAction.isVisible() || exportAction.isVisible();
        viewHolder.mOptionsButton.setVisibility(isOneMenuItemVisible ? View.VISIBLE : View.GONE);
    }

    private AlertDialog.Builder getInstallDialog() {
        if (!isBatteryLevelOk()) {
            Resources resources = mContext.getResources();
            String message = resources.getString(R.string.dialog_battery_low_message_pct,
                    resources.getInteger(R.integer.battery_ok_percentage_discharging),
                    resources.getInteger(R.integer.battery_ok_percentage_charging));
            return new AlertDialog.Builder(mContext, R.style.AppTheme_AlertDialogStyle)
                    .setTitle(R.string.dialog_battery_low_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null);
        }
        UpdateInfo update = mUpdaterController.getCurrentUpdate();
        int resId;
        String extraMessage = "";
        try {
            if (Utils.isABUpdate(update.getFile())) {
                resId = R.string.apply_update_dialog_message_ab;
            } else {
                resId = R.string.apply_update_dialog_message;
                extraMessage = " (" + Constants.DOWNLOAD_PATH + ")";
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not determine the type of the update");
            return null;
        }

        return new AlertDialog.Builder(mContext, R.style.AppTheme_AlertDialogStyle)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(mContext.getString(resId, update.getName(),
                        mContext.getString(android.R.string.ok)) + extraMessage)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> Utils.triggerUpdate(mContext))
                .setNegativeButton(android.R.string.cancel, null);
    }

    private void exportUpdate() {
        Intent intent = new Intent(UpdatesActivity.ACTION_EXPORT_UPDATE);
        intent.putExtra(UpdatesActivity.EXTRA_UPDATE_NAME, mUpdate.getName());
        intent.putExtra(UpdatesActivity.EXTRA_UPDATE_FILE, mUpdate.getFile());
        mBroadcastManager.sendBroadcast(intent);
    }

    private void showInfoDialog() {
        if (infoDialog != null) {
            infoDialog.dismiss();
        }
        infoDialog = new AlertDialog.Builder(mContext, R.style.AppTheme_AlertDialogStyle)
                .setTitle(R.string.blocked_update_dialog_title)
                .setPositiveButton(android.R.string.ok, null)
                .setMessage(R.string.blocked_update_dialog_summary)
                .show();
    }

    private boolean isBatteryLevelOk() {
        Intent intent = mContext.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (!intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
            return true;
        }
        int percent = Math.round(100.f * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) /
                intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int required = (plugged & BATTERY_PLUGGED_ANY) != 0 ?
                mContext.getResources().getInteger(R.integer.battery_ok_percentage_charging) :
                mContext.getResources().getInteger(R.integer.battery_ok_percentage_discharging);
        return percent >= required;
    }

    private void showSnackbar(String text) {
        Intent intent = new Intent(UpdatesActivity.ACTION_SHOW_SNACKBAR);
        intent.putExtra(UpdatesActivity.EXTRA_SNACKBAR_TEXT, text);
        mBroadcastManager.sendBroadcast(intent);
    }

    private enum Action {
        DOWNLOAD,
        PAUSE,
        RESUME,
        INSTALL,
        INFO,
        DELETE,
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private Button mAction;
        private ImageView mOptionsButton;
        private Button mDetails;

        private TextView mBuildDate;
        private TextView mBuildName;
        private TextView mBuildSize;

        private ProgressBar mProgressBar;
        private TextView mProgressText;

        ViewHolder(final View view) {
            super(view);
            mAction = view.findViewById(R.id.update_action);
            mOptionsButton = view.findViewById(R.id.options_action);
            mDetails = view.findViewById(R.id.details_action);

            mBuildDate = view.findViewById(R.id.build_date);
            mBuildName = view.findViewById(R.id.build_name);
            mBuildSize = view.findViewById(R.id.build_size);

            mProgressBar = view.findViewById(R.id.progress_bar);
            mProgressText = view.findViewById(R.id.progress_text);
        }
    }
}
