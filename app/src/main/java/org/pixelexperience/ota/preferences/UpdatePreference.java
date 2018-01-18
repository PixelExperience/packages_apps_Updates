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
package org.pixelexperience.ota.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Handler;
import android.preference.Preference;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.cachapa.expandablelayout.ExpandableLayout;

import org.pixelexperience.ota.R;
import org.pixelexperience.ota.misc.UpdateInfo;
import org.pixelexperience.ota.utils.Utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdatePreference extends Preference implements OnLongClickListener {
    public static final int STYLE_NEW = 1;
    public static final int STYLE_DOWNLOADING = 2;
    public static final int STYLE_DOWNLOADED = 3;
    public static final int STYLE_COMPLETING = 4;

    private OnActionListener mOnActionListener;
    private OnReadyListener mOnReadyListener;
    private Context mContext;
    private UpdateInfo mUpdateInfo = null;
    private int mStyle;
    private Button mStopDownloadButton;
    private TextView mSummaryText;
    private View mUpdatesPref;
    private ProgressBar mProgressBar;
    private Button mButton;
    private Button mChangelogButton;
    private ExpandableLayout expandableChangelogLayout;
    private long mUpdateFileSize;
    private String mUpdateChangelog;
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
    public void onBindView(View view) {
        super.onBindView(view);
        mUpdateFileSize = mUpdateInfo.getFileSize();
        mUpdateChangelog = mUpdateInfo.getChangelog();

        // Store the views from the layout
        TextView mTitleText = view.findViewById(R.id.title);
        mSummaryText = view.findViewById(R.id.summary);
        mProgressBar = view.findViewById(R.id.download_progress_bar);
        mStopDownloadButton = view.findViewById(R.id.updates_button);
        mButton = view.findViewById(R.id.button);
        mStopDownloadButton.setOnClickListener(mButtonClickListener);
        mButton.setOnClickListener(mButtonClickListener);
        expandableChangelogLayout = view.findViewById(R.id.expandableChangelogLayout);
        mChangelogButton = view.findViewById(R.id.changelog_button);
        mChangelogButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                expandableChangelogLayout.toggle();
            }
        });
        expandableChangelogLayout.setOnExpansionUpdateListener(new ExpandableLayout.OnExpansionUpdateListener() {
            @Override
            public void onExpansionUpdate(float expansionFraction, int state) {
                mChangelogButton.setCompoundDrawablesWithIntrinsicBounds(mContext.getResources().getDrawable(state != 0 ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down, mContext.getTheme()), null, null, null);
            }
        });
        TextView mChangelogText = view.findViewById(R.id.changelog_text);
        if (isChangelogAvailable()) {
            Pattern p2 = Pattern.compile("\\s+\\*\\s(([\\w_.-]+/)+)");
            Pattern p3 = Pattern.compile("(\\d\\d\\-\\d\\d\\-\\d{4})");
            SpannableStringBuilder sb = new SpannableStringBuilder(mUpdateChangelog);
            Resources.Theme theme = mContext.getTheme();
            TypedValue typedValue = new TypedValue();
            theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true);
            final int color = mContext.getColor(typedValue.resourceId);
            Matcher m = p2.matcher(mUpdateChangelog);
            while (m.find()){
                sb.setSpan(new StyleSpan(Typeface.BOLD),m.start(0), m.end(0), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(color),m.start(0),m.end(0),Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            }
            m = p3.matcher(mUpdateChangelog);
            while (m.find()){
                sb.setSpan(new StyleSpan(Typeface.BOLD+ Typeface.ITALIC),m.start(1), m.end(1), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            }
            mChangelogText.setText(sb);
        }

        mUpdatesPref = view.findViewById(R.id.updates_pref);
        mUpdatesPref.setOnLongClickListener(this);

        // Update the views
        updatePreferenceViews();

        mTitleText.setText(mUpdateInfo.getFileName());

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

            case STYLE_COMPLETING:
            case STYLE_DOWNLOADING:
            case STYLE_NEW:
            default:
                // Do nothing for now
                break;
        }
        return true;
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
                .setNegativeButton(android.R.string.cancel, null)
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

    public int getStyle() {
        return mStyle;
    }

    public void setStyle(int style) {
        mStyle = style;
        if (mUpdatesPref != null) {
            showStyle();
        }
    }

    public void updateDownloadPercent(int percent) {
        if (mStyle != STYLE_DOWNLOADING) {
            return;
        }

        mSummaryText.setText(String.format(Locale.getDefault(), "%1$s • %2$s %3$d%%",
                Utils.readableFileSize(mUpdateFileSize), mContext.getString(R.string.type_downloading), percent));
    }

    public ProgressBar getProgressBar() {
        return mProgressBar;
    }

    public Button getStopDownloadButton() {
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
            if (!enabled) {
                mUpdatesPref.setBackgroundColor(0);
            }

            // Show the proper style view
            showStyle();
        }
    }

    private boolean isChangelogAvailable() {
        return mUpdateChangelog != null && !mUpdateChangelog.equals("");
    }

    private void showStyle() {
        // Display the appropriate preference style
        switch (mStyle) {
            case STYLE_DOWNLOADED:
                mStopDownloadButton.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                mButton.setVisibility(View.VISIBLE);
                mChangelogButton.setVisibility(isChangelogAvailable() ? View.VISIBLE : View.GONE);
                expandableChangelogLayout.collapse();
                mSummaryText.setText(String.format("%1$s • %2$s",
                        Utils.readableFileSize(mUpdateFileSize), mContext.getString(R.string.type_downloaded)));
                mButton.setText(mContext.getString(R.string.install_button));
                break;

            case STYLE_DOWNLOADING:
                mButton.setVisibility(View.GONE);
                mChangelogButton.setVisibility(View.GONE);
                expandableChangelogLayout.collapse();
                mStopDownloadButton.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                mSummaryText.setText(String.format("%1$s • %2$s",
                        Utils.readableFileSize(mUpdateFileSize), mContext.getString(R.string.type_downloading)));
                break;

            case STYLE_COMPLETING:
                mStopDownloadButton.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.VISIBLE);
                mProgressBar.setIndeterminate(true);
                mButton.setVisibility(View.GONE);
                mChangelogButton.setVisibility(View.GONE);
                expandableChangelogLayout.collapse();
                mSummaryText.setText(String.format("%1$s • %2$s",
                        Utils.readableFileSize(mUpdateFileSize), mContext.getString(R.string.type_completing)));
                break;

            case STYLE_NEW:
            default:
                mStopDownloadButton.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.GONE);
                mButton.setVisibility(View.VISIBLE);
                mChangelogButton.setVisibility(isChangelogAvailable() ? View.VISIBLE : View.GONE);
                expandableChangelogLayout.collapse();
                mSummaryText.setText(Utils.readableFileSize(mUpdateFileSize));
                mButton.setText(mContext.getString(R.string.download_button));
                break;
        }
    }

    public interface OnActionListener {
        void onStartDownload(UpdatePreference pref);

        void onStopCompletingDownload(UpdatePreference pref);

        void onStopDownload(UpdatePreference pref);

        void onStartUpdate(UpdatePreference pref);

        void onDeleteUpdate(UpdatePreference pref);
    }

    public interface OnReadyListener {
        void onReady(UpdatePreference pref);
    }
}