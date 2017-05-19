/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2017 Henrique Silva (jhenrique09)
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package com.purenexus.ota.utils;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.URLUtil;
import android.os.Environment;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.database.Cursor;
import android.content.ContentUris;

import com.purenexus.ota.R;
import com.purenexus.ota.misc.Constants;
import com.purenexus.ota.service.UpdateCheckService;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.text.SimpleDateFormat;
import java.io.DataOutputStream;
import java.util.ArrayList;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;

import android.app.AlertDialog;
import android.content.DialogInterface;

import com.stericson.RootTools.BuildConfig;
import com.stericson.RootTools.RootTools;

import org.apache.commons.io.FileUtils;

public class Utils {

    private static final String TAG = "Utils";

    private Utils() {
        // this class is not supposed to be instantiated
    }

    public static File makeUpdateFolder() {
        File updatesFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Constants.UPDATES_FOLDER);
        if (!updatesFolder.exists()){
            try{
                updatesFolder.mkdir();
            }catch(Exception ex){
            }
        }
        return updatesFolder;
    }

    public static File makeTempFolder() {
        File tempFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Constants.UPDATES_FOLDER + "/temp");
        if (!tempFolder.exists()){
            try{
                tempFolder.mkdir();
            }catch(Exception ex){
            }
        }
        return tempFolder;
    }

    public static boolean deleteDir(File dir) {
        try{
            if (dir.isDirectory()) {
                String[] children = dir.list();
                for (String aChildren : children) {
                    boolean success = deleteDir(new File(dir, aChildren));
                    if (!success) {
                        return false;
                    }
                }
            }
        // The directory is now empty so delete it
            return dir.delete();
        }catch(Exception ex){
            return false; // failed to delete file
        }
    }

    public static void deleteTempFolder() {
        deleteDir(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Constants.UPDATES_FOLDER + "/" + "temp"));
    }

    public static void writeMD5File(String fileName,String md5) {
        String path = makeUpdateFolder().getPath() + "/" + fileName + ".md5";
        File md5File = new File(path);

        if (md5File.exists()){
            try{
                md5File.delete();
            }catch(Exception ex){
            }
        }

        writeStringAsFile(path, md5);
    }

    public static String readMD5File(String fileName) {
        String path = makeUpdateFolder().getPath() + "/" + fileName + ".md5";
        File md5File = new File(path);
        if (!md5File.exists()){
            return "";
        }

        return readFileAsString(path);
    }

    public static void writeStringAsFile(String filePath, String fileContents) {
        try {
            FileWriter out = new FileWriter(new File(filePath));
            out.write(fileContents);
            out.close();
        } catch (Exception e) {
            Log.e(TAG, "Error in writeStringAsFile");
        }
    }

    public static String readFileAsString(String filePath) {
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        BufferedReader in = null;

        try {
            in = new BufferedReader(new FileReader(new File(filePath)));
            while ((line = in.readLine()) != null) stringBuilder.append(line);

        } catch (Exception e) {
            Log.e(TAG, "Error in readFileAsString");
        } 

        return stringBuilder.toString();
    }

    public static void cancelNotification(Context context) {
        final NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(R.string.not_new_updates_found_title);
        nm.cancel(R.string.not_download_success);
    }

    public static boolean isOTAConfigured(){
        return SystemProperties.get("ro.ota.manifest") != null && !SystemProperties.get("ro.ota.manifest").isEmpty();
    }

    public static String getDeviceType() {
        return SystemProperties.get(Constants.CURRENT_DEVICE_NAME);
    }

    public static String getInstalledVersion() {
        return SystemProperties.get(Constants.CURRENT_VERSION);
    }

    public static long getInstalledBuildDate() {
        return getTimestampFromDateString(SystemProperties.get("ro.ota.build.date", ""),Constants.FILENAME_DATE_FORMAT);
    }

    public static String getDateLocalized(Context context, long unixTimestamp) {
        DateFormat f = DateFormat.getDateInstance(DateFormat.LONG, getCurrentLocale(context));
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = new Date(unixTimestamp * 1000);
        return f.format(date);
    }

    public static long getTimestampFromDateString(String date,String format) {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat(format);
            Date d = formatter.parse(date);
            long timestamp = d.getTime() / 1000;
            return timestamp;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getAndroidVersion(String versionName) {
        return versionName.split("-")[1];
    }

    public static String getUserAgentString(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi.packageName + "/" + pi.versionName;
        } catch (PackageManager.NameNotFoundException nnfe) {
            return null;
        }
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }

    public static void scheduleUpdateService(Context context, int updateFrequency) {
        // Load the required settings from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        long lastCheck = prefs.getLong(Constants.LAST_UPDATE_CHECK_PREF, 0);

        // Get the intent ready
        Intent i = new Intent(context, UpdateCheckService.class);
        i.setAction(UpdateCheckService.ACTION_CHECK);
        PendingIntent pi = PendingIntent.getService(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);

        // Clear any old alarms and schedule the new alarm
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);

        if (updateFrequency != Constants.UPDATE_FREQ_NONE) {
            am.setRepeating(AlarmManager.RTC_WAKEUP, lastCheck + updateFrequency, updateFrequency, pi);
        }
    }

    public static String getRealPathFromURI(Context context, Uri uri) {
        try {
            String filePath = "";
            String selection = null;
            String[] selectionArgs = null;
            if (Build.VERSION.SDK_INT >= 19 && DocumentsContract.isDocumentUri(context,uri)) {
                if (isExternalStorageDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    if ("primary".equalsIgnoreCase(type)) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    } else {
                        filePath = "/storage/" + type + "/" + split[1];
                        return filePath;
                    }
                } else if (isDownloadsDocument(uri)) {
                    final String id = DocumentsContract.getDocumentId(uri);
                    uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                } else if (isMediaDocument(uri)) {
                    final String docId = DocumentsContract.getDocumentId(uri);
                    final String[] split = docId.split(":");
                    final String type = split[0];
                    if ("image".equals(type)) {
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    } else if ("video".equals(type)) {
                        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                    } else if ("audio".equals(type)) {
                        uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    }
                    selection = "_id=?";
                    selectionArgs = new String[] {
                        split[1]
                    };
                }
            }
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                String[] projection = {
                    MediaStore.Images.Media.DATA
                };
                Cursor cursor = null;
                try {
                    cursor = context.getContentResolver()
                    .query(uri, projection, selection, selectionArgs, null);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    if (cursor.moveToFirst()) {
                        return cursor.getString(column_index);
                    }
                } catch (Exception e) {}
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                return uri.getPath();
            }
        } catch (Exception ex) {}

        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static Locale getCurrentLocale(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.getResources().getConfiguration().getLocales()
                    .getFirstMatch(context.getResources().getAssets().getLocales());
        } else {
            return context.getResources().getConfiguration().locale;
        }
    }

    public static boolean copyFile(File sourceFile, File destFile) {
        try {
            FileUtils.copyFile(sourceFile, destFile);
        }catch (Exception e){
            return false;
        }
        return true;
    }

    public static void recovery(Context context) {
        rebootPhone(context, "recovery");
    }

    public static String shell(String cmd, boolean root) {
        String out = "";
        ArrayList<String> r = system(root ? getSuBin() : "sh",cmd).getStringArrayList("out");
        for(String l: r) {
            out += l+"\n";
        }
        return out;
    }

    public static boolean getRoot() {
        return RootTools.isAccessGiven();
    }
    
    public static boolean isRootAvailable() {
        return RootTools.isRootAvailable();
    }
    
    private static void rebootPhone(Context context, String type) { 
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            powerManager.reboot("recovery");
        } catch (Exception e) {
            Log.e("Tools", "reboot '"+type+"' error: "+e.getMessage());
            shell("reboot "+type, true);
        }
    }
    
    private static String getSuBin() {
        if (new File("/system/xbin","su").exists()) {
            return "/system/xbin/su";
        }
        if (RootTools.isRootAvailable()) {
            return "su";
        }
        return "sh";
    }
    
    private static Bundle system(String shell, String command) {
        
        ArrayList<String> res = new ArrayList<String>();
        ArrayList<String> err = new ArrayList<String>();
        boolean success = false;
        try {
            Process process = Runtime.getRuntime().exec(shell);
            DataOutputStream STDIN = new DataOutputStream(process.getOutputStream());
            BufferedReader STDOUT = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader STDERR = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            if (BuildConfig.DEBUG) Log.i(shell, command);
            STDIN.writeBytes(command + "\n");
            STDIN.flush();
            STDIN.writeBytes("exit\n");
            STDIN.flush();
            
            process.waitFor();
            if (process.exitValue() == 255) {
                if (BuildConfig.DEBUG) Log.e(shell,"SU was probably denied! Exit value is 255");
                err.add("SU was probably denied! Exit value is 255");
            }
            
            while (STDOUT.ready()) {
                String read = STDOUT.readLine();
                if (BuildConfig.DEBUG) Log.d(shell, read);
                res.add(read);
            }
            while (STDERR.ready()) {
                String read = STDERR.readLine();
                if (BuildConfig.DEBUG) Log.e(shell, read);
                err.add(read);
            }
            
            process.destroy();
            success = true;
            if (err.size() > 0) {
                success = false;
            }
        } catch (IOException e) {
            if (BuildConfig.DEBUG) Log.e(shell,"IOException: "+e.getMessage());
            err.add("IOException: "+e.getMessage());
        } catch (InterruptedException e) {
            if (BuildConfig.DEBUG) Log.e(shell,"InterruptedException: "+e.getMessage());
            err.add("InterruptedException: "+e.getMessage());
        }
        if (BuildConfig.DEBUG) Log.d(shell,"END");
        Bundle r = new Bundle();
        r.putBoolean("success", success);
        r.putString("cmd", command);
        r.putString("binary", shell);
        r.putStringArrayList("out", res);
        r.putStringArrayList("error", err);
        return r;
    }

    public static boolean isValidURL(String url){
        return URLUtil.isValidUrl(url);
    }

}
