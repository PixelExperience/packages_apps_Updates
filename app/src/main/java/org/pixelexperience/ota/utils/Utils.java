/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2014 ParanoidAndroid Project
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2018 Pixel Experience (jhenrique09)
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */
package org.pixelexperience.ota.utils;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.webkit.URLUtil;

import org.pixelexperience.ota.R;
import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.service.UpdateCheckService;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final String TAG = "Utils";

    private Utils() {
        // this class is not supposed to be instantiated
    }

    public static File makeUpdateFolder() {
        File updatesFolder = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Constants.UPDATES_FOLDER);
        if (!updatesFolder.exists()) {
            try {
                updatesFolder.mkdir();
            } catch (Exception ignored) {
            }
        }
        return updatesFolder;
    }

    public static boolean deleteDir(File dir) {
        try {
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
        } catch (Exception ex) {
            return false; // failed to delete file
        }
    }

    public static void deleteTempFolder() {
        deleteDir(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Constants.UPDATES_FOLDER + "/" + "temp"));
    }

    public static void cancelNotification(Context context) {
        final NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(R.string.notif_download_success_title);
        nm.cancel(R.string.notif_download_success_summary);
    }

    private static String getSystemProperty(String key, String defaultValue) {
        String value;

        try {
            value = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
            return (value == null || value.isEmpty()) ? defaultValue : value;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    public static boolean isOTAConfigured() {
        String prop = getSystemProperty(Constants.CURRENT_BUILD_TYPE, "UNOFFICIAL");
        return prop.toLowerCase().equals("official");
    }

    public static String getDeviceName() {
        return getSystemProperty(Constants.CURRENT_DEVICE_NAME, "");
    }

    public static String getInstalledVersion() {
        return getSystemProperty(Constants.CURRENT_VERSION, "");
    }

    public static long getInstalledBuildDate() {
        return getTimestampFromDateString(extractBuildDate(getSystemProperty(Constants.CURRENT_VERSION, ""), Constants.PATTERN_FILENAME_DATE_FORMAT), Constants.FILENAME_DATE_FORMAT);
    }

    public static String extractBuildDate(String date, String pattern) {
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(date);
        if (m.find()) {
            return m.group(1);
        } else {
            return "";
        }
    }

    public static long getTimestampFromDateString(String date, String format) {
        try {
            SimpleDateFormat formatter = new SimpleDateFormat(format);
            Date d = formatter.parse(date);
            return d.getTime() / 1000;
        } catch (Exception e) {
            return 0;
        }
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
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null) { // connected to the internet
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI || activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE || activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isOnMobileData(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null) { // connected to the internet
                if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    return true;
                }
            }
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

    public static Notification createDownloadNotificationChannel(Context context) {
        try {
            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel mChannel = new NotificationChannel(Constants.DOWNLOADING_CHANNEL_ID, context.getResources().getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
            mChannel.enableLights(false);
            mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            mNotificationManager.createNotificationChannel(mChannel);
            return new Notification.Builder(context, Constants.DOWNLOADING_CHANNEL_ID)
                    .setWhen(System.currentTimeMillis())
                    .setSmallIcon(R.drawable.ic_system_update)
                    .setContentTitle(context.getResources().getString(R.string.app_name))
                    .setOngoing(true)
                    .build();
        } catch (Exception ex) {
            return null;
        }
    }

    public static boolean isValidURL(String url) {
        return URLUtil.isValidUrl(url);
    }

    /**
     * Method borrowed from OpenDelta. Using reflection voodoo instead calling
     * the hidden class directly, to dev/test outside of AOSP tree.
     * <p/>
     * Jorrit "Chainfire" Jongma and The OmniROM Project
     */
    public static boolean setPermissions(String path, int mode, int uid, int gid) {
        try {
            Class<?> FileUtils = Utils.class.getClassLoader().loadClass("android.os.SdcardUtils");
            Method setPermissions = FileUtils.getDeclaredMethod("setPermissions", String.class,
                    int.class,
                    int.class,
                    int.class);
            return ((Integer) setPermissions.invoke(
                    null,
                    path,
                    mode,
                    uid,
                    gid) == 0);
        } catch (Exception e) {
            // A lot of voodoo could go wrong here, return failure instead of
            // crash
            e.printStackTrace();
        }
        return false;
    }

    public static String exec(String command) {
        try {
            Process p = Runtime.getRuntime().exec(command);
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("sync\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            return getStreamLines(p.getInputStream());
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static String getStreamLines(final InputStream is) {
        String out = null;
        StringBuffer buffer = null;
        final DataInputStream dis = new DataInputStream(is);

        try {
            if (dis.available() > 0) {
                buffer = new StringBuffer(dis.readLine());
                while (dis.available() > 0) {
                    buffer.append("\n").append(dis.readLine());
                }
            }
            dis.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (buffer != null) {
            out = buffer.toString();
        }
        return out;
    }

    private static boolean isExternalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public static String[] getSdcardPaths() {
        String sPrimarySdcard = null, sSecondarySdcard = null;

        ArrayList<String> mounts = new ArrayList<>();
        ArrayList<String> vold = new ArrayList<>();

        Scanner scanner = null;
        try {
            scanner = new Scanner(new File("/proc/mounts"));
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.startsWith("/dev/block/vold/")) {
                    String[] lineElements = line.split(" ");
                    String element = lineElements[1];

                    mounts.add(element);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        boolean addExternal = mounts.size() == 1 && isExternalStorageAvailable();
        if (mounts.size() == 0 && addExternal) {
            mounts.add("/mnt/sdcard");
        }
        File fstab = findFstab();
        scanner = null;
        if (fstab != null) {
            try {

                scanner = new Scanner(fstab);
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("dev_mount")) {
                        String[] lineElements = line.split(" ");
                        String element = lineElements[2];

                        if (element.contains(":")) {
                            element = element.substring(0, element.indexOf(":"));
                        }

                        if (!element.toLowerCase().contains("usb")) {
                            vold.add(element);
                        }
                    } else if (line.startsWith("/devices/platform")) {
                        String[] lineElements = line.split(" ");
                        String element = lineElements[1];

                        if (element.contains(":")) {
                            element = element.substring(0, element.indexOf(":"));
                        }

                        if (!element.toLowerCase().contains("usb")) {
                            vold.add(element);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }
        }
        if (addExternal && (vold.size() == 1 && isExternalStorageAvailable())) {
            mounts.add(vold.get(0));
        }
        if (vold.size() == 0 && isExternalStorageAvailable()) {
            vold.add("/mnt/sdcard");
        }

        for (int i = 0; i < mounts.size(); i++) {
            String mount = mounts.get(i);
            File root = new File(mount);
            if (!vold.contains(mount)
                    || (!root.exists() || !root.isDirectory() || !root.canWrite())) {
                mounts.remove(i--);
            }
        }

        for (int i = 0; i < mounts.size(); i++) {
            String mount = mounts.get(i);
            if (!mount.contains("sdcard0") && !mount.equalsIgnoreCase("/mnt/sdcard")
                    && !mount.equalsIgnoreCase("/sdcard")) {
                sSecondarySdcard = mount;
            } else {
                sPrimarySdcard = mount;
            }
        }

        if (sPrimarySdcard == null) {
            sPrimarySdcard = "/sdcard";
        }
        return new String[]{sPrimarySdcard, sSecondarySdcard};
    }

    private static File findFstab() {
        File file;

        file = new File("/system/etc/vold.fstab");
        if (file.exists()) {
            return file;
        }

        String fstab = exec("grep -ls \"/dev/block/\" * --include=fstab.* --exclude=fstab.goldfish");
        if (fstab != null) {
            String[] files = fstab.split("\n");
            for (String file1 : files) {
                file = new File(file1);
                if (file.exists()) {
                    return file;
                }
            }
        }

        return null;
    }
    public static String readableFileSize(long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "kB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

}