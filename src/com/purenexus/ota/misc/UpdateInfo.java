/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.purenexus.ota.misc;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.purenexus.ota.utils.Utils;

import java.io.File;
import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class UpdateInfo implements Parcelable, Serializable {
    private String mFileName;
    private long mFileSize;
    private long mBuildDate;
    private String mDownloadUrl;
    private String mChangelogUrl;
    private String mDonateUrl;
    private String mWebsiteUrl;
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
     * Set file name
     */
    public void setFileName(String fileName) {
        mFileName = fileName;
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
    public long getDate() {
        return mBuildDate;
    }

    /**
     * Get download location
     */
    public String getDownloadUrl() {
        return mDownloadUrl;
    }

    /**
     * Get changelog url
     */
    public String getChangelogUrl() {
        return mChangelogUrl;
    }

    /**
     * Get donate url
     */
    public String getDonateUrl() {
        return mDonateUrl;
    }

    /**
     * Get website url
     */
    public String getWebsiteUrl() {
        return mWebsiteUrl;
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
    public List<Map<String,String>> getAddons() {
        List<Map<String,String>> addons = new ArrayList<Map<String,String>>();
        try{
            JSONArray addonsListJson = new JSONArray(mAddons);
            int length = addonsListJson.length();
            for (int i = 0; i < length; i++) {
                if (addonsListJson.isNull(i)) {
                    continue;
                }
                JSONObject addon = addonsListJson.getJSONObject(i);
                Map<String,String> map = new HashMap<String,String>();
                map.put("title", addon.getString("title"));
                map.put("summary", addon.getString("summary"));
                map.put("url", addon.getString("url"));
                addons.add(i,map);
            }
        }catch(Exception ex){
            addons = new ArrayList<Map<String,String>>();
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
        mIsNewerThanInstalled = mBuildDate > Utils.getInstalledBuildDate();

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
                && mBuildDate == ui.mBuildDate
                && TextUtils.equals(mDownloadUrl, ui.mDownloadUrl);
    }

    public static final Parcelable.Creator<UpdateInfo> CREATOR = new Parcelable.Creator<UpdateInfo>() {
        public UpdateInfo createFromParcel(Parcel in) {
            return new UpdateInfo(in);
        }

        public UpdateInfo[] newArray(int size) {
            return new UpdateInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mFileName);
        out.writeLong(mFileSize);
        out.writeString(mChangelogUrl);
        out.writeLong(mBuildDate);
        out.writeString(mDownloadUrl);
        out.writeString(mDonateUrl);
        out.writeString(mWebsiteUrl);
        out.writeString(mDeveloper);
        out.writeString(mMD5);
        out.writeString(mAddons);
    }

    private void readFromParcel(Parcel in) {
        mFileName = in.readString();
        mFileSize = in.readLong();
        mChangelogUrl = in.readString();
        mBuildDate = in.readLong();
        mDownloadUrl = in.readString();
        mDonateUrl = in.readString();
        mWebsiteUrl = in.readString();
        mDeveloper = in.readString();
        mMD5 = in.readString();
        mAddons = in.readString();
    }

    public static class Builder {
        private String mFileName;
        private long mFileSize;
        private long mBuildDate;
        private String mDownloadUrl;
        private String mChangelogUrl;
        private String mDonateUrl;
        private String mWebsiteUrl;
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

        public Builder setBuildDate(long buildDate) {
            mBuildDate = buildDate;
            return this;
        }

        public Builder setDownloadUrl(String downloadUrl) {
            mDownloadUrl = downloadUrl;
            return this;
        }

        public Builder setChangelogUrl(String changelogUrl) {
            mChangelogUrl = changelogUrl;
            return this;
        }

        public Builder setDonateUrl(String donateUrl) {
            mDonateUrl = donateUrl;
            return this;
        }

        public Builder setWebsiteUrl(String websiteUrl) {
            mWebsiteUrl = websiteUrl;
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
            info.mChangelogUrl = mChangelogUrl;
            info.mDonateUrl = mDonateUrl;
            info.mWebsiteUrl = mWebsiteUrl;
            info.mDeveloper = mDeveloper;
            info.mMD5 = mMD5;
            info.mAddons = mAddons;
            return info;
        }
    }
}
