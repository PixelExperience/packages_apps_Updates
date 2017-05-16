/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.purenexus.ota;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.text.format.Formatter;
import android.os.Handler;
import java.lang.Runnable;

import com.purenexus.ota.misc.UpdateInfo;
import com.purenexus.ota.UpdateApplication;
import com.purenexus.ota.UpdatesActivity;
import com.purenexus.ota.utils.Utils;

import java.io.File;

public class UpdatePreference extends Preference implements OnClickListener, OnLongClickListener {
    public static final int STYLE_NEW = 1;
    public static final int STYLE_DOWNLOADING = 2;
    public static final int STYLE_DOWNLOADED = 3;
    public static final int STYLE_INSTALLED = 4;
    public static final int STYLE_COMPLETING = 5;

    private static String TAG = "UpdatePreference";

    public interface OnActionListener {
        void onStartDownload(UpdatePreference pref);
        void onStopCompletingDownload(UpdatePreference pref);
        void onStopDownload(UpdatePreference pref);
        void onStartUpdate(UpdatePreference pref);
        void onDeleteUpdate(UpdatePreference pref);
        void showChangelog(String mUpdateChangelogUrl);
    }

    public interface OnReadyListener {
        void onReady(UpdatePreference pref);
    }

    private OnActionListener mOnActionListener;
    private OnReadyListener mOnReadyListener;

    private Context mContext;
    private UpdateInfo mUpdateInfo = null;
    private int mStyle;

    private ImageView mStopDownloadButton;
    private TextView mTitleText;
    private TextView mSummaryText;
    private View mUpdatesPref;
    private ProgressBar mProgressBar;
    private Button mButton;

    private String mBuildVersionName;
    private String mBuildDateString;

    private String mUpdateFileName;
    private long mUpdateFileSize;
    private String mUpdateChangelogUrl;

    private boolean buttonClicked = false;

    private OnClickListener mButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mOnActionListener == null || buttonClicked) {
                return;
            }

            buttonClicked = true;

            switch (mStyle) {
                case STYLE_COMPLETING:
                    mOnActionListener.onStopCompletingDownload(UpdatePreference.this);
                    break;
                case STYLE_DOWNLOADED:
                    mOnActionListener.onStartUpdate(UpdatePreference.this);
                    break;
                case STYLE_DOWNLOADING:
                    mOnActionListener.onStopDownload(UpdatePreference.this);
                    break;
                case STYLE_NEW:
                    mOnActionListener.onStartDownload(UpdatePreference.this);
                    break;
            }
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    buttonClicked = false;
                }
            }, 500);
        }
    };

    public UpdatePreference(Context context, UpdateInfo ui, int style) {
        super(context, null, R.style.UpdatesPreferenceStyle);
        setLayoutResource(R.layout.preference_updates);
        mStyle = style;
        mUpdateInfo = ui;
        mContext = context;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        mUpdateFileName = mUpdateInfo.getFileName();
        mUpdateFileSize = mUpdateInfo.getFileSize();
        mUpdateChangelogUrl = mUpdateInfo.getChangelogUrl();
        // We only show updates of type Utils.getUpdateType(), so just use that here
        mBuildVersionName = Utils.getInstalledVersion();
        mBuildDateString = Utils.getDateLocalized(mContext,mUpdateInfo.getDateTimestamp());

        // Store the views from the layout
        mTitleText = (TextView)view.findViewById(R.id.title);
        mSummaryText = (TextView)view.findViewById(R.id.summary);
        mProgressBar = (ProgressBar)view.findViewById(R.id.download_progress_bar);
        mStopDownloadButton = (ImageView)view.findViewById(R.id.updates_button);
        mButton = (Button) view.findViewById(R.id.button);
        mStopDownloadButton.setOnClickListener(mButtonClickListener);
        mButton.setOnClickListener(mButtonClickListener);

        mUpdatesPref = view.findViewById(R.id.updates_pref);
        mUpdatesPref.setOnClickListener(this);
        mUpdatesPref.setOnLongClickListener(this);

        // Update the views
        updatePreferenceViews();

        mTitleText.setText(mUpdateFileName);

        if (mOnReadyListener != null) {
            mOnReadyListener.onReady(this);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        switch (mStyle) {
            case STYLE_DOWNLOADED:
                confirmDelete();
                break;

            case STYLE_INSTALLED:
            case STYLE_COMPLETING:
            case STYLE_DOWNLOADING:
            case STYLE_NEW:
            default:
                // Do nothing for now
                break;
        }
        return true;
    }

    @Override
    public void onClick(View v) {
        if (mOnActionListener != null && !buttonClicked) {
            mOnActionListener.showChangelog(mUpdateChangelogUrl);
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.confirm_delete_dialog_title)
                .setMessage(R.string.confirm_delete_dialog_message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // We are OK to delete, trigger it
                        if (mOnActionListener != null) {
                            mOnActionListener.onDeleteUpdate(UpdatePreference.this);
                        }
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    @Override
    public String toString() {
        return "UpdatePreference [mUpdateInfo=" + mUpdateInfo + ", mStyle=" + mStyle + "]";
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            updatePreferenceViews();
        }
    }

    public void setOnActionListener(OnActionListener listener) {
        mOnActionListener = listener;
    }

    public void setOnReadyListener(OnReadyListener listener) {
        mOnReadyListener = listener;
        if (mUpdatesPref != null && listener != null) {
            listener.onReady(this);
        }
    }

    public void setStyle(int style) {
        mStyle = style;
        if (mUpdatesPref != null) {
            showStyle();
        }
    }

    public int getStyle() {
        return mStyle;
    }

    public void setProgress(int max, int progress) {
        if (mStyle != STYLE_DOWNLOADING) {
            return;
        }
        mProgressBar.setMax(max);
        mProgressBar.setProgress(progress);
    }

    public ProgressBar getProgressBar() {
        return mProgressBar;
    }

    public ImageView getUpdatesButton() {
        return mStopDownloadButton;
    }

    public UpdateInfo getUpdateInfo() {
        return mUpdateInfo;
    }

    private void updatePreferenceViews() {
        if (mUpdatesPref != null) {
            mUpdatesPref.setEnabled(true);
            mUpdatesPref.setLongClickable(true);

            final boolean enabled = isEnabled();
            mUpdatesPref.setOnClickListener(enabled ? this : null);
            if (!enabled) {
                mUpdatesPref.setBackgroundColor(0);
            }

            // Show the proper style view
            showStyle();
        }
    }

    private void showStyle() {
        // Display the appropriate preference style
        switch (mStyle) {
            case STYLE_DOWNLOADED:
                mStopDownloadButton.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                mButton.setVisibility(View.VISIBLE);
                mSummaryText.setText(String.format("%1$s • %2$s\nAndroid %3$s %4$s",
                    mBuildDateString, Formatter.formatFileSize(mContext,mUpdateFileSize),
                    Utils.getAndroidVersion(mUpdateFileName), mContext.getString(R.string.type_downloaded)));
                mButton.setText(mContext.getString(R.string.install_button));
                break;

            case STYLE_DOWNLOADING:
                mButton.setVisibility(View.GONE);
                mStopDownloadButton.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                mSummaryText.setText(String.format("%1$s • %2$s\nAndroid %3$s %4$s",
                    mBuildDateString, Formatter.formatFileSize(mContext,mUpdateFileSize),
                    Utils.getAndroidVersion(mUpdateFileName), mContext.getString(R.string.type_downloading)));
                break;

            case STYLE_INSTALLED:
                mStopDownloadButton.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                mButton.setVisibility(View.GONE);
                mSummaryText.setText(String.format("%1$s • %2$s\nAndroid %3$s %4$s",
                    mBuildDateString, Formatter.formatFileSize(mContext,mUpdateFileSize),
                    Utils.getAndroidVersion(mUpdateFileName), mContext.getString(R.string.type_installed))); //
                break;

            case STYLE_COMPLETING:
                mStopDownloadButton.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                mProgressBar.setIndeterminate(true);
                mButton.setVisibility(View.GONE);
                mSummaryText.setText(String.format("%1$s • %2$s\nAndroid %3$s %4$s",
                    mBuildDateString, Formatter.formatFileSize(mContext,mUpdateFileSize),
                    Utils.getAndroidVersion(mUpdateFileName), mContext.getString(R.string.type_completing)));
                break;

            case STYLE_NEW:
            default:
                mStopDownloadButton.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                mButton.setVisibility(View.VISIBLE);
                mSummaryText.setText(String.format("%1$s • %2$s\nAndroid %3$s",
                    mBuildDateString, Formatter.formatFileSize(mContext,mUpdateFileSize),
                    Utils.getAndroidVersion(mUpdateFileName)));
                mButton.setText(mContext.getString(R.string.download_button));
                break;
        }
    }
}
