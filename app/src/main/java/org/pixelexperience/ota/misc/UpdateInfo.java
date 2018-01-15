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
package org.pixelexperience.ota.misc;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.pixelexperience.ota.utils.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateInfo implements Parcelable, Serializable {
    public static final Parcelable.Creator<UpdateInfo> CREATOR = new Parcelable.Creator<UpdateInfo>() {
        public UpdateInfo createFromParcel(Parcel in) {
            return new UpdateInfo(in);
        }

        public UpdateInfo[] newArray(int size) {
            return new UpdateInfo[size];
        }
    };
    private String mFileName;
    private long mFileSize;
    private String mBuildDate;
    private String mDownloadUrl;
    private String mChangelog;
    private String mDonateUrl;
    private String mForumUrl;
    private String mWebsiteUrl;
    private String mNewsUrl;
    private String mDeveloper;
    private String mMD5;
    private String mAddons;
    private Boolean mIsNewerThanInstalled;

    private UpdateInfo() {
        // Use the builder
    }

    private UpdateInfo(Parcel in) {
        readFromParcel(in);
    }

    /**
     * Get file name
     */
    public String getFileName() {
        return mFileName;
    }

    /**
     * Get file size
     */
    public long getFileSize() {
        return mFileSize;
    }

    /**
     * Get build date
     */
    public String getDate() {
        return mBuildDate;
    }

    /**
     * Get build date in timestamp format
     */
    private long getDateTimestamp() {
        String buildDate = mBuildDate;
        if (buildDate.length() == 8) {
            buildDate = buildDate + "-0000";
        }
        return Utils.getTimestampFromDateString(buildDate, Constants.FILENAME_DATE_FORMAT);
    }

    /**
     * Get download location
     */
    public String getDownloadUrl() {
        return mDownloadUrl;
    }

    /**
     * Get changelog
     */
    public String getChangelog() {
        return mChangelog;
    }

    /**
     * Get donate url
     */
    public String getDonateUrl() {
        return mDonateUrl;
    }

    /**
     * Get forum url
     */
    public String getForumUrl() {
        return mForumUrl;
    }

    /**
     * Get website url
     */
    public String getWebsiteUrl() {
        return mWebsiteUrl;
    }

    /**
     * Get news url
     */
    public String getNewsUrl() {
        return mNewsUrl;
    }

    /**
     * Get developer
     */
    public String getDeveloper() {
        return mDeveloper;
    }

    /**
     * Get MD5
     */
    public String getMD5() {
        return mMD5;
    }

    /**
     * Get addons list
     */
    public List<Map<String, String>> getAddons() {
        List<Map<String, String>> addons = new ArrayList<>();
        try {
            JSONArray addonsListJson = new JSONArray(mAddons);
            int length = addonsListJson.length();
            for (int i = 0; i < length; i++) {
                if (addonsListJson.isNull(i)) {
                    continue;
                }
                JSONObject addon = addonsListJson.getJSONObject(i);
                Map<String, String> map = new HashMap<>();
                map.put("title", addon.getString("title"));
                map.put("summary", addon.getString("summary"));
                map.put("url", addon.getString("url"));
                addons.add(i, map);
            }
        } catch (Exception ex) {
            addons = new ArrayList<>();
        }
        return addons;
    }

    /**
     * Get addons list in json
     */
    public String getAddonsInJson() {
        return mAddons;
    }

    public boolean isNewerThanInstalled() {
        if (mIsNewerThanInstalled != null) {
            return mIsNewerThanInstalled;
        }
        mIsNewerThanInstalled = getDateTimestamp() > Utils.getInstalledBuildDate();

        return mIsNewerThanInstalled;
    }

    @Override
    public String toString() {
        return "UpdateInfo: " + mFileName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof UpdateInfo)) {
            return false;
        }

        UpdateInfo ui = (UpdateInfo) o;
        return TextUtils.equals(mFileName, ui.mFileName)
                && mBuildDate.equals(ui.mBuildDate)
                && TextUtils.equals(mDownloadUrl, ui.mDownloadUrl);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mFileName);
        out.writeLong(mFileSize);
        out.writeString(mChangelog);
        out.writeString(mBuildDate);
        out.writeString(mDownloadUrl);
        out.writeString(mDonateUrl);
        out.writeString(mForumUrl);
        out.writeString(mWebsiteUrl);
        out.writeString(mNewsUrl);
        out.writeString(mDeveloper);
        out.writeString(mMD5);
        out.writeString(mAddons);
    }

    private void readFromParcel(Parcel in) {
        mFileName = in.readString();
        mFileSize = in.readLong();
        mChangelog = in.readString();
        mBuildDate = in.readString();
        mDownloadUrl = in.readString();
        mDonateUrl = in.readString();
        mForumUrl = in.readString();
        mWebsiteUrl = in.readString();
        mNewsUrl = in.readString();
        mDeveloper = in.readString();
        mMD5 = in.readString();
        mAddons = in.readString();
    }

    public static class Builder {
        private String mFileName;
        private long mFileSize;
        private String mBuildDate;
        private String mDownloadUrl;
        private String mChangelog;
        private String mDonateUrl;
        private String mForumUrl;
        private String mWebsiteUrl;
        private String mNewsUrl;
        private String mDeveloper;
        private String mMD5;
        private String mAddons;

        public Builder setFileName(String fileName) {
            mFileName = fileName;
            return this;
        }

        public Builder setFilesize(long fileSize) {
            mFileSize = fileSize;
            return this;
        }

        public Builder setBuildDate(String buildDate) {
            mBuildDate = buildDate;
            return this;
        }

        public Builder setDownloadUrl(String downloadUrl) {
            mDownloadUrl = downloadUrl;
            return this;
        }

        public Builder setChangelog(String changelog) {
            mChangelog = changelog;
            return this;
        }

        public Builder setDonateUrl(String donateUrl) {
            mDonateUrl = donateUrl;
            return this;
        }

        public Builder setForumUrl(String forumUrl) {
            mForumUrl = forumUrl;
            return this;
        }

        public Builder setWebsiteUrl(String websiteUrl) {
            mWebsiteUrl = websiteUrl;
            return this;
        }

        public Builder setNewsUrl(String newsUrl) {
            mNewsUrl = newsUrl;
            return this;
        }

        public Builder setDeveloper(String developer) {
            mDeveloper = developer;
            return this;
        }

        public Builder setMD5(String md5) {
            mMD5 = md5;
            return this;
        }

        public Builder setAddons(String addons) {
            mAddons = addons;
            return this;
        }

        public UpdateInfo build() {
            UpdateInfo info = new UpdateInfo();
            info.mFileName = mFileName;
            info.mFileSize = mFileSize;
            info.mBuildDate = mBuildDate;
            info.mDownloadUrl = mDownloadUrl;
            info.mChangelog = mChangelog;
            info.mDonateUrl = mDonateUrl;
            info.mForumUrl = mForumUrl;
            info.mWebsiteUrl = mWebsiteUrl;
            info.mNewsUrl = mNewsUrl;
            info.mDeveloper = mDeveloper;
            info.mMD5 = mMD5;
            info.mAddons = mAddons;
            return info;
        }
    }
}
