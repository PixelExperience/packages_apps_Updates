package org.pixelexperience.ota.provider;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SecurityProvider extends ContentProvider {

    @SuppressLint("SimpleDateFormat")
    private static Date getSecurityPatch() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(Build.VERSION.SECURITY_PATCH);
        } catch (ParseException e) {
            return null;
        }
    }

    private int getRes(String res) {
        try {
            String settingsPackage = "com.android.settings";
            return getContext().getPackageManager().getResourcesForApplication(settingsPackage).getIdentifier(res, "drawable", settingsPackage);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public final Bundle call(String str, String str2, Bundle bundle) {
        Uri parse;
        List<String> pathSegments;
        if (!TextUtils.isEmpty(str) && !TextUtils.isEmpty(str2) && (parse = Uri.parse(str2)) != null &&
                "content".equals(parse.getScheme()) && "org.pixelexperience.ota.provider".equals(parse.getAuthority()) &&
                parse.getPort() == -1 && (pathSegments = parse.getPathSegments()) != null && pathSegments.size() == 2) {
            Bundle returnBundle = new Bundle();
            String item = pathSegments.get(1);
            if (item.equals("SecurityPatchLevelIcon")) {
                int i = getRes("ic_ota_update_current");
                if (i != 0) {
                    returnBundle.putInt("com.android.settings.icon", i);
                    returnBundle.putString("com.android.settings.icon_package", "com.android.settings");
                    return returnBundle;
                }
            }
            if (item.equals("SecurityPatchLevelSummary")) {
                String summary = DateFormat.format(
                        DateFormat.getBestDateTimePattern(Locale.getDefault(), "dMMMMyyyy"),
                        getSecurityPatch()).toString();
                returnBundle.putString("com.android.settings.summary", summary);
                return returnBundle;
            }
        }
        return null;
    }

    @Override
    public final int delete(Uri uri, String str, String[] strArr) {
        return 0;
    }

    @Override
    public final String getType(Uri uri) {
        return null;
    }

    @Override
    public final Uri insert(Uri uri, ContentValues contentValues) {
        return null;
    }

    @Override
    public final boolean onCreate() {
        return true;
    }

    @Override
    public final Cursor query(Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
        return null;
    }

    @Override
    public final int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
        return 0;
    }
}