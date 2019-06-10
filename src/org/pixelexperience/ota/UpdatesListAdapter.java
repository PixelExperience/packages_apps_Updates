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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.view.ContextThemeWrapper;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.MenuPopupHelper;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.pixelexperience.ota.controller.UpdaterController;
import org.pixelexperience.ota.controller.UpdaterService;
import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.misc.PermissionsUtils;
import org.pixelexperience.ota.misc.StringGenerator;
import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.UpdateInfo;
import org.pixelexperience.ota.model.UpdateStatus;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.List;

public class UpdatesListAdapter extends RecyclerView.Adapter<UpdatesListAdapter.ViewHolder> {

    private static final String TAG = "UpdateListAdapter";

    private final float mAlphaDisabledValue;

    private List<String> mDownloadIds;
    private String mSelectedDownload;
    private UpdaterController mUpdaterController;
    private UpdatesListActivity mActivity;

    UpdatesListAdapter(UpdatesListActivity activity) {
        mActivity = activity;

        TypedValue tv = new TypedValue();
        mActivity.getTheme().resolveAttribute(android.R.attr.disabledAlpha, tv, true);
        mAlphaDisabledValue = tv.getFloat();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.update_item_view, viewGroup, false);
        return new ViewHolder(view);
    }

    void setUpdaterController(UpdaterController updaterController) {
        mUpdaterController = updaterController;
        notifyDataSetChanged();
    }

    @SuppressLint("SetTextI18n")
    private void handleActiveStatus(ViewHolder viewHolder, UpdateInfo update) {
        boolean canDelete = false;

        final String downloadId = update.getDownloadId();
        if (mUpdaterController.isDownloading(downloadId)) {
            canDelete = true;
            String downloaded = Utils.readableFileSize(update.getFile().length());
            String total = Utils.readableFileSize(update.getFileSize());
            String percentage = NumberFormat.getPercentInstance().format(
                    update.getProgress() / 100.f);
            long eta = update.getEta();
            if (eta > 0) {
                CharSequence etaString = StringGenerator.formatETA(mActivity, eta * 1000);
                viewHolder.mProgressText.setText(mActivity.getString(
                        R.string.list_download_progress_eta_new, downloaded, total, etaString,
                        percentage));
            } else {
                viewHolder.mProgressText.setText(mActivity.getString(
                        R.string.list_download_progress_new, downloaded, total, percentage));
            }
            setButtonAction(viewHolder.mAction, Action.PAUSE, downloadId, true);
            viewHolder.mDetails.setVisibility(View.GONE);
            viewHolder.mProgressBar.setIndeterminate(update.getStatus() == UpdateStatus.STARTING);
            viewHolder.mProgressBar.setProgress(update.getProgress());
            viewHolder.mBuildName.setSelected(false);
        } else if (mUpdaterController.isInstallingUpdate(downloadId)) {
            viewHolder.mDetails.setVisibility(View.GONE);
            setButtonAction(viewHolder.mAction, Action.CANCEL_INSTALLATION, downloadId, true);
            boolean notAB = !mUpdaterController.isInstallingABUpdate();
            viewHolder.mProgressText.setText(notAB ? R.string.dialog_prepare_zip_message :
                    update.getFinalizing() ?
                            R.string.finalizing_package :
                            R.string.preparing_ota_first_boot);
            viewHolder.mProgressBar.setIndeterminate(false);
            viewHolder.mProgressBar.setProgress(update.getInstallProgress());
            viewHolder.mBuildName.setSelected(true);
        } else if (mUpdaterController.isVerifyingUpdate(downloadId)) {
            viewHolder.mDetails.setVisibility(View.GONE);
            setButtonAction(viewHolder.mAction, Action.INSTALL, downloadId, false);
            viewHolder.mProgressText.setText(R.string.list_verifying_update);
            viewHolder.mProgressBar.setIndeterminate(true);
            viewHolder.mBuildName.setSelected(true);
        } else {
            canDelete = true;
            viewHolder.mDetails.setVisibility(View.GONE);
            setButtonAction(viewHolder.mAction, Action.RESUME, downloadId, !isBusy());
            String downloaded = Utils.readableFileSize(update.getFile().length());
            String total = Utils.readableFileSize(update.getFileSize());
            String percentage = NumberFormat.getPercentInstance().format(
                    update.getProgress() / 100.f);
            viewHolder.mProgressText.setText(mActivity.getString(R.string.list_download_progress_new,
                    downloaded, total, percentage));
            viewHolder.mProgressBar.setIndeterminate(false);
            viewHolder.mProgressBar.setProgress(update.getProgress());
            viewHolder.mBuildName.setSelected(false);
        }

        viewHolder.itemView.setOnLongClickListener(getLongClickListener(update, canDelete,
                viewHolder.mBuildDate));
        viewHolder.mProgressBar.setVisibility(View.VISIBLE);
        viewHolder.mProgressText.setVisibility(View.VISIBLE);
        viewHolder.mBuildSize.setVisibility(View.INVISIBLE);
    }

    private void handleNotActiveStatus(ViewHolder viewHolder, UpdateInfo update) {
        final String downloadId = update.getDownloadId();
        if (mUpdaterController.isWaitingForReboot(downloadId)) {
            viewHolder.itemView.setOnLongClickListener(
                    getLongClickListener(update, false, viewHolder.mBuildDate));
            setButtonAction(viewHolder.mAction, Action.REBOOT, downloadId, true);
            viewHolder.mDetails.setVisibility(View.GONE);
        } else if (update.getPersistentStatus() == UpdateStatus.Persistent.VERIFIED) {
            viewHolder.itemView.setOnLongClickListener(
                    getLongClickListener(update, true, viewHolder.mBuildDate));
            setButtonAction(viewHolder.mAction,
                    Utils.canInstall(update) ? Action.INSTALL : Action.DELETE,
                    downloadId, !isBusy());
            viewHolder.mDetails.setVisibility(View.GONE);
        } else if (!Utils.canInstall(update)) {
            viewHolder.itemView.setOnLongClickListener(
                    getLongClickListener(update, false, viewHolder.mBuildDate));
            setButtonAction(viewHolder.mAction, Action.INFO, downloadId, !isBusy());
            viewHolder.mDetails.setVisibility(View.GONE);
        } else {
            viewHolder.itemView.setOnLongClickListener(
                    getLongClickListener(update, false, viewHolder.mBuildDate));
            setButtonAction(viewHolder.mAction, Action.DOWNLOAD, downloadId, !isBusy());
            viewHolder.mDetails.setVisibility(View.VISIBLE);
        }
        String fileSize = Utils.readableFileSize(update.getFileSize());
        viewHolder.mBuildSize.setText(fileSize);

        viewHolder.mProgressBar.setVisibility(View.INVISIBLE);
        viewHolder.mProgressText.setVisibility(View.INVISIBLE);
        viewHolder.mBuildSize.setVisibility(View.VISIBLE);
        viewHolder.mBuildName.setSelected(true);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder viewHolder, int i) {
        if (mDownloadIds == null) {
            viewHolder.mAction.setEnabled(false);
            return;
        }

        final String downloadId = mDownloadIds.get(i);
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
        if (update == null) {
            // The update was deleted
            viewHolder.mAction.setEnabled(false);
            viewHolder.mAction.setText(R.string.action_download);
            return;
        }

        viewHolder.itemView.setSelected(downloadId.equals(mSelectedDownload));

        boolean activeLayout;
        switch (update.getPersistentStatus()) {
            case UpdateStatus.Persistent.UNKNOWN:
                activeLayout = update.getStatus() == UpdateStatus.STARTING;
                break;
            case UpdateStatus.Persistent.VERIFIED:
                activeLayout = update.getStatus() == UpdateStatus.INSTALLING;
                break;
            case UpdateStatus.Persistent.INCOMPLETE:
                activeLayout = true;
                break;
            default:
                throw new RuntimeException("Unknown update status");
        }

        String buildDate = StringGenerator.getDateLocalizedUTC(mActivity,
                DateFormat.LONG, update.getTimestamp());
        String buildVersion = update.getName();
        viewHolder.mBuildDate.setText(buildDate);
        viewHolder.mBuildName.setText(buildVersion);
        viewHolder.mBuildName.setCompoundDrawables(null, null, null, null);
        viewHolder.mDetails.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Utils.getDownloadWebpageUrl(update.getName())));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mActivity.startActivity(intent);
            } catch (Exception ex) {
                mActivity.showSnackbar(R.string.error_open_url, Snackbar.LENGTH_SHORT);
            }
        });

        if (activeLayout) {
            handleActiveStatus(viewHolder, update);
        } else {
            handleNotActiveStatus(viewHolder, update);
        }
    }

    @Override
    public int getItemCount() {
        return mDownloadIds == null ? 0 : mDownloadIds.size();
    }

    public void setData(List<String> downloadIds) {
        mDownloadIds = downloadIds;
    }

    void notifyItemChanged(String downloadId) {
        if (mDownloadIds == null) {
            return;
        }
        notifyItemChanged(mDownloadIds.indexOf(downloadId));
    }

    void removeItem(String downloadId) {
        if (mDownloadIds == null) {
            return;
        }
        int position = mDownloadIds.indexOf(downloadId);
        mDownloadIds.remove(downloadId);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, getItemCount());
    }

    private void startDownloadWithWarning(final String downloadId) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean warn = preferences.getBoolean(Constants.PREF_MOBILE_DATA_WARNING, true);
        if (Utils.isOnWifiOrEthernet(mActivity) || !warn) {
            mUpdaterController.startDownload(downloadId);
            return;
        }

        View checkboxView = LayoutInflater.from(mActivity).inflate(R.layout.checkbox_view, null);
        CheckBox checkbox = checkboxView.findViewById(R.id.checkbox);
        checkbox.setText(R.string.checkbox_mobile_data_warning);

        new AlertDialog.Builder(mActivity, R.style.AppTheme_AlertDialogStyle)
                .setTitle(R.string.update_on_mobile_data_title)
                .setMessage(R.string.update_on_mobile_data_message)
                .setView(checkboxView)
                .setPositiveButton(R.string.action_download,
                        (dialog, which) -> {
                            if (checkbox.isChecked()) {
                                preferences.edit()
                                        .putBoolean(Constants.PREF_MOBILE_DATA_WARNING, false)
                                        .apply();
                                mActivity.supportInvalidateOptionsMenu();
                            }
                            mUpdaterController.startDownload(downloadId);
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setButtonAction(Button button, Action action, final String downloadId,
                                 boolean enabled) {
        final View.OnClickListener clickListener;
        switch (action) {
            case DOWNLOAD:
                button.setText(R.string.action_download);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_download, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> startDownloadWithWarning(downloadId) : null;
                break;
            case PAUSE:
                button.setText(R.string.action_pause);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_pause, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> mUpdaterController.pauseDownload(downloadId)
                        : null;
                break;
            case RESUME: {
                button.setText(R.string.action_resume);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_resume, 0, 0, 0);
                button.setEnabled(enabled);
                UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update) ||
                        update.getFile().length() == update.getFileSize();
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        mUpdaterController.resumeDownload(downloadId);
                    } else {
                        mActivity.showSnackbar(R.string.snack_update_not_installable,
                                Snackbar.LENGTH_LONG);
                    }
                } : null;
            }
            break;
            case INSTALL: {
                button.setText(R.string.action_install);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_install, 0, 0, 0);
                button.setEnabled(enabled);
                UpdateInfo update = mUpdaterController.getUpdate(downloadId);
                final boolean canInstall = Utils.canInstall(update);
                clickListener = enabled ? view -> {
                    if (canInstall) {
                        getInstallDialog(downloadId).show();
                    } else {
                        mActivity.showSnackbar(R.string.snack_update_not_installable,
                                Snackbar.LENGTH_LONG);
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
                clickListener = enabled ? view -> getDeleteDialog(downloadId).show() : null;
            }
            break;
            case CANCEL_INSTALLATION: {
                button.setText(R.string.action_cancel);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_cancel, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> getCancelInstallationDialog().show() : null;
            }
            break;
            case REBOOT: {
                button.setText(R.string.reboot);
                button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_updateui_restart, 0, 0, 0);
                button.setEnabled(enabled);
                clickListener = enabled ? view -> {
                    PowerManager pm =
                            (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
                    pm.reboot(null);
                } : null;
            }
            break;
            default:
                clickListener = null;
                button.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
        button.setAlpha(enabled ? 1.f : mAlphaDisabledValue);

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

    private AlertDialog.Builder getDeleteDialog(final String downloadId) {
        return new AlertDialog.Builder(mActivity, R.style.AppTheme_AlertDialogStyle)
                .setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            mUpdaterController.pauseDownload(downloadId);
                            mUpdaterController.deleteUpdate(downloadId);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private View.OnLongClickListener getLongClickListener(final UpdateInfo update,
                                                          final boolean canDelete, View anchor) {
        return view -> {
            startActionMode(update, canDelete, anchor);
            return true;
        };
    }

    private AlertDialog.Builder getInstallDialog(final String downloadId) {
        if (!isBatteryLevelOk()) {
            Resources resources = mActivity.getResources();
            String message = resources.getString(R.string.dialog_battery_low_message_pct,
                    resources.getInteger(R.integer.battery_ok_percentage_discharging),
                    resources.getInteger(R.integer.battery_ok_percentage_charging));
            return new AlertDialog.Builder(mActivity, R.style.AppTheme_AlertDialogStyle)
                    .setTitle(R.string.dialog_battery_low_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null);
        }
        UpdateInfo update = mUpdaterController.getUpdate(downloadId);
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

        return new AlertDialog.Builder(mActivity, R.style.AppTheme_AlertDialogStyle)
                .setTitle(R.string.apply_update_dialog_title)
                .setMessage(mActivity.getString(resId,update.getName(),
                        mActivity.getString(android.R.string.ok)) + extraMessage)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> Utils.triggerUpdate(mActivity, downloadId))
                .setNegativeButton(android.R.string.cancel, null);
    }

    private AlertDialog.Builder getCancelInstallationDialog() {
        return new AlertDialog.Builder(mActivity, R.style.AppTheme_AlertDialogStyle)
                .setMessage(R.string.cancel_installation_dialog_message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> {
                            Intent intent = new Intent(mActivity, UpdaterService.class);
                            intent.setAction(UpdaterService.ACTION_INSTALL_STOP);
                            mActivity.startService(intent);
                        })
                .setNegativeButton(android.R.string.cancel, null);
    }

    private void startActionMode(final UpdateInfo update, final boolean canDelete, View anchor) {
        mSelectedDownload = update.getDownloadId();
        notifyItemChanged(update.getDownloadId());

        ContextThemeWrapper wrapper = new ContextThemeWrapper(mActivity,
                R.style.AppTheme_PopupMenuOverlapAnchor);
        PopupMenu popupMenu = new PopupMenu(wrapper, anchor, Gravity.NO_GRAVITY,
                R.attr.actionOverflowMenuStyle, 0);
        popupMenu.inflate(R.menu.menu_action_mode);

        MenuBuilder menu = (MenuBuilder) popupMenu.getMenu();
        menu.findItem(R.id.menu_delete_action).setVisible(canDelete);
        menu.findItem(R.id.menu_copy_url).setVisible(update.getAvailableOnline());
        menu.findItem(R.id.menu_export_update).setVisible(
                update.getPersistentStatus() == UpdateStatus.Persistent.VERIFIED);

        popupMenu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.menu_delete_action:
                    getDeleteDialog(update.getDownloadId()).show();
                    return true;
                case R.id.menu_copy_url:
                    Utils.addToClipboard(mActivity,
                            mActivity.getString(R.string.label_download_url),
                            Utils.getDownloadWebpageUrl(update.getName()),
                            mActivity.getString(R.string.toast_download_url_copied));
                    return true;
                case R.id.menu_export_update:
                    // TODO: start exporting once the permission has been granted
                    boolean hasPermission = PermissionsUtils.checkAndRequestStoragePermission(
                            mActivity, 0);
                    if (hasPermission) {
                        exportUpdate(update);
                    }
                    return true;
            }
            return false;
        });

        MenuPopupHelper helper = new MenuPopupHelper(wrapper, menu, anchor);
        helper.show();
    }

    private void exportUpdate(UpdateInfo update) {
        File dest = new File(Utils.getExportPath(), update.getName());
        if (dest.exists()) {
            dest = Utils.appendSequentialNumber(dest);
        }
        Intent intent = new Intent(mActivity, ExportUpdateService.class);
        intent.setAction(ExportUpdateService.ACTION_START_EXPORTING);
        intent.putExtra(ExportUpdateService.EXTRA_SOURCE_FILE, update.getFile());
        intent.putExtra(ExportUpdateService.EXTRA_DEST_FILE, dest);
        mActivity.startService(intent);
    }

    private void showInfoDialog() {
        String messageString = mActivity.getString(R.string.snack_update_not_installable);
        AlertDialog dialog = new AlertDialog.Builder(mActivity, R.style.AppTheme_AlertDialogStyle)
                .setTitle(R.string.blocked_update_dialog_title)
                .setPositiveButton(android.R.string.ok, null)
                .setMessage(messageString)
                .show();
        TextView textView = dialog.findViewById(android.R.id.message);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private boolean isBatteryLevelOk() {
        Intent intent = mActivity.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (!intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)) {
            return true;
        }
        int percent = Math.round(100.f * intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) /
                intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int required = (plugged & BatteryManager.BATTERY_PLUGGED_ANY) != 0 ?
                mActivity.getResources().getInteger(R.integer.battery_ok_percentage_charging) :
                mActivity.getResources().getInteger(R.integer.battery_ok_percentage_discharging);
        return percent >= required;
    }

    private enum Action {
        DOWNLOAD,
        PAUSE,
        RESUME,
        INSTALL,
        INFO,
        DELETE,
        CANCEL_INSTALLATION,
        REBOOT,
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private Button mAction;
        private Button mDetails;

        private TextView mBuildDate;
        private TextView mBuildName;
        private TextView mBuildSize;

        private ProgressBar mProgressBar;
        private TextView mProgressText;

        ViewHolder(final View view) {
            super(view);
            mAction = view.findViewById(R.id.update_action);
            mDetails = view.findViewById(R.id.details_action);

            mBuildDate = view.findViewById(R.id.build_date);
            mBuildName = view.findViewById(R.id.build_name);
            mBuildSize = view.findViewById(R.id.build_size);

            mProgressBar = view.findViewById(R.id.progress_bar);
            mProgressText = view.findViewById(R.id.progress_text);
        }
    }
}
