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
package org.pixelexperience.ota.misc;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.pixelexperience.ota.UpdatesDbHelper;
import org.pixelexperience.ota.controller.UpdaterService;
import org.pixelexperience.ota.model.Update;
import org.pixelexperience.ota.model.UpdateBaseInfo;
import org.pixelexperience.ota.model.UpdateInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {

    private static final String TAG = "Utils";

    private Utils() {
    }

    public static File getDownloadPath() {
        return new File(Constants.DOWNLOAD_PATH);
    }

    public static File getExportPath() {
        File dir = new File(Environment.getExternalStorageDirectory(), Constants.EXPORT_PATH);
        if (!dir.isDirectory()) {
            if (dir.exists() || !dir.mkdirs()) {
                throw new RuntimeException("Could not create directory");
            }
        }
        return dir;
    }

    public static File getCachedUpdateList(Context context) {
        return new File(context.getCacheDir(), "updates_v2.json");
    }

    // This should really return an UpdateBaseInfo object, but currently this only
    // used to initialize UpdateInfo objects
    private static UpdateInfo parseJsonUpdate(JSONObject object) throws JSONException {
        Update update = new Update();
        update.setTimestamp(object.getLong("datetime"));
        update.setName(object.getString("filename"));
        update.setDownloadId(object.getString("id"));
        update.setFileSize(object.getLong("size"));
        update.setDownloadUrl(object.getString("url"));
        update.setVersion(object.getString("version"));
        update.setHash(object.getString("filehash"));
        update.setMaintainer(object.isNull("maintainer") ? "" : object.getString("maintainer"));
        update.setMaintainerUrl(object.isNull("maintainer_url") ? "" : object.getString("maintainer_url"));
        update.setDonateUrl(object.isNull("donate_url") ? "" : object.getString("donate_url"));
        update.setForumUrl(object.isNull("forum_url") ? "" : object.getString("forum_url"));
        update.setWebsiteUrl(object.isNull("website_url") ? "" : object.getString("website_url"));
        update.setNewsUrl(object.isNull("news_url") ? "" : object.getString("news_url"));
        return update;
    }

    public static boolean isCompatible(UpdateBaseInfo update) {
        if (update.getVersion().compareTo(SystemProperties.get(Constants.PROP_BUILD_VERSION)) < 0) {
            Log.d(TAG, update.getName() + " is older than current Android version");
            return false;
        }
        if (update.getTimestamp() <= SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0)) {
            Log.d(TAG, update.getName() + " is older than/equal to the current build");
            return false;
        }
        return true;
    }

    public static boolean canInstall(UpdateBaseInfo update) {
        return (update.getTimestamp() > SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0)) &&
                update.getVersion().equalsIgnoreCase(
                        SystemProperties.get(Constants.PROP_BUILD_VERSION));
    }

    public static UpdateInfo parseJson(File file, boolean compatibleOnly)
            throws IOException, JSONException {

        StringBuilder json = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            for (String line; (line = br.readLine()) != null; ) {
                json.append(line);
            }
        }

        JSONObject obj = new JSONObject(json.toString());
        try {
            UpdateInfo update = parseJsonUpdate(obj);
            if (!compatibleOnly || isCompatible(update)) {
                return update;
            } else {
                Log.d(TAG, "Ignoring incompatible update " + update.getName());
            }
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse update object", e);
        }

        return null;
    }

    public static String getServerURL() {
        return String.format(Constants.OTA_URL, SystemProperties.get(Constants.PROP_DEVICE), SystemProperties.get(Constants.PROP_VERSION_CODE));
    }

    public static String getDownloadWebpageUrl(String fileName) {
        return String.format(Constants.DOWNLOAD_WEBPAGE_URL, SystemProperties.get(Constants.PROP_DEVICE), fileName);
    }

    public static void triggerUpdate(Context context, String downloadId) {
        final Intent intent = new Intent(context, UpdaterService.class);
        intent.setAction(UpdaterService.ACTION_INSTALL_UPDATE);
        intent.putExtra(UpdaterService.EXTRA_DOWNLOAD_ID, downloadId);
        context.startService(intent);
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        return !(info == null || !info.isConnected() || !info.isAvailable());
    }

    public static boolean isOnWifiOrEthernet(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        return (info != null && (info.getType() == ConnectivityManager.TYPE_ETHERNET
                || info.getType() == ConnectivityManager.TYPE_WIFI));
    }

    public static boolean checkForNewUpdates(File oldJson, File newJson)
            throws IOException, JSONException {
        UpdateInfo oldUpdate = parseJson(oldJson, true);
        UpdateInfo newUpdate = parseJson(newJson, true);
        if (oldUpdate == null || newUpdate == null) {
            return false;
        }
        return !oldUpdate.getDownloadId().equals(newUpdate.getDownloadId());
    }

    public static long getZipEntryOffset(ZipFile zipFile, String entryPath) {
        // Each entry has an header of (30 + n + m) bytes
        // 'n' is the length of the file name
        // 'm' is the length of the extra field
        final int FIXED_HEADER_SIZE = 30;
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        long offset = 0;
        while (zipEntries.hasMoreElements()) {
            ZipEntry entry = zipEntries.nextElement();
            int n = entry.getName().length();
            int m = entry.getExtra() == null ? 0 : entry.getExtra().length;
            int headerSize = FIXED_HEADER_SIZE + n + m;
            offset += headerSize;
            if (entry.getName().equals(entryPath)) {
                return offset;
            }
            offset += entry.getCompressedSize();
        }
        Log.e(TAG, "Entry " + entryPath + " not found");
        throw new IllegalArgumentException("The given entry was not found");
    }

    private static void removeUncryptFiles(File downloadPath) {
        File[] uncryptFiles = downloadPath.listFiles(
                (dir, name) -> name.endsWith(Constants.UNCRYPT_FILE_EXT));
        if (uncryptFiles == null) {
            return;
        }
        for (File file : uncryptFiles) {
            file.delete();
        }
    }

    public static void cleanupDownloadsDir(Context context) {
        File downloadPath = getDownloadPath();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        removeUncryptFiles(downloadPath);

        long buildTimestamp = SystemProperties.getLong(Constants.PROP_BUILD_DATE, 0);
        long prevTimestamp = preferences.getLong(Constants.PREF_INSTALL_OLD_TIMESTAMP, 0);
        String lastUpdatePath = preferences.getString(Constants.PREF_INSTALL_PACKAGE_PATH, null);
        boolean reinstalling = preferences.getBoolean(Constants.PREF_INSTALL_AGAIN, false);
        if ((buildTimestamp != prevTimestamp || reinstalling) &&
                lastUpdatePath != null) {
            File lastUpdate = new File(lastUpdatePath);
            if (lastUpdate.exists()) {
                lastUpdate.delete();
                // Remove the pref not to delete the file if re-downloaded
                preferences.edit().remove(Constants.PREF_INSTALL_PACKAGE_PATH).apply();
            }
        }

        final String DOWNLOADS_CLEANUP_DONE = "cleanup_done";
        if (preferences.getBoolean(DOWNLOADS_CLEANUP_DONE, false)) {
            return;
        }

        Log.d(TAG, "Cleaning " + downloadPath);
        if (!downloadPath.isDirectory()) {
            return;
        }
        File[] files = downloadPath.listFiles();
        if (files == null) {
            return;
        }

        // Ideally the database is empty when we get here
        UpdatesDbHelper dbHelper = new UpdatesDbHelper(context);
        List<String> knownPaths = new ArrayList<>();
        for (UpdateInfo update : dbHelper.getUpdates()) {
            knownPaths.add(update.getFile().getAbsolutePath());
        }
        for (File file : files) {
            if (!knownPaths.contains(file.getAbsolutePath())) {
                Log.d(TAG, "Deleting " + file.getAbsolutePath());
                file.delete();
            }
        }

        preferences.edit().putBoolean(DOWNLOADS_CLEANUP_DONE, true).apply();
    }

    public static File appendSequentialNumber(final File file) {
        String name;
        String extension;
        int extensionPosition = file.getName().lastIndexOf(".");
        if (extensionPosition > 0) {
            name = file.getName().substring(0, extensionPosition);
            extension = file.getName().substring(extensionPosition);
        } else {
            name = file.getName();
            extension = "";
        }
        final File parent = file.getParentFile();
        for (int i = 1; i < Integer.MAX_VALUE; i++) {
            File newFile = new File(parent, name + "-" + i + extension);
            if (!newFile.exists()) {
                return newFile;
            }
        }
        throw new IllegalStateException();
    }

    public static boolean isABDevice() {
        return SystemProperties.getBoolean(Constants.PROP_AB_DEVICE, false);
    }

    private static boolean isABUpdate(ZipFile zipFile) {
        return zipFile.getEntry(Constants.AB_PAYLOAD_BIN_PATH) != null &&
                zipFile.getEntry(Constants.AB_PAYLOAD_PROPERTIES_PATH) != null;
    }

    public static boolean isABUpdate(File file) throws IOException {
        ZipFile zipFile = new ZipFile(file);
        boolean isAB = isABUpdate(zipFile);
        zipFile.close();
        return isAB;
    }

    public static void addToClipboard(Context context, String label, String text, String toastMessage) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean isEncrypted(Context context, File file) {
        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        if (sm == null) {
            return false;
        }
        return sm.isEncrypted(file);
    }

    public static int getUpdateCheckSetting(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getInt(Constants.PREF_AUTO_UPDATES_CHECK_INTERVAL,
                Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY);
    }

    public static boolean isUpdateCheckEnabled(Context context) {
        return getUpdateCheckSetting(context) != Constants.AUTO_UPDATES_CHECK_INTERVAL_NEVER;
    }

    public static long getUpdateCheckInterval(Context context) {
        switch (Utils.getUpdateCheckSetting(context)) {
            case Constants.AUTO_UPDATES_CHECK_INTERVAL_DAILY:
                return AlarmManager.INTERVAL_DAY;
            case Constants.AUTO_UPDATES_CHECK_INTERVAL_WEEKLY:
            default:
                return AlarmManager.INTERVAL_DAY * 7;
            case Constants.AUTO_UPDATES_CHECK_INTERVAL_MONTHLY:
                return AlarmManager.INTERVAL_DAY * 30;
        }
    }

    public static String calculateMD5(File updateFile) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Exception while getting digest", e);
            return null;
        }

        InputStream is;
        try {
            is = new FileInputStream(updateFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Exception while getting FileInputStream", e);
            return null;
        }

        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String output = bigInt.toString(16);
            // Fill to 32 chars
            output = String.format("%32s", output).replace(' ', '0');
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Unable to process file for MD5", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception on closing MD5 input stream", e);
            }
        }
    }

    @SuppressLint("DefaultLocale")
    public static String readableFileSize(long size) {
        String units[] = new String[]{"B", "kB", "MB", "GB", "TB", "PB"};
        int mod = 1024;
        double power = (size > 0) ? Math.floor(Math.log(size) / Math.log(mod)) : 0;
        String unit = units[(int)power];
        double result = size / Math.pow(mod, power);
        if (unit.equals("B")|| unit.equals("kB") || unit.equals("MB")){
            result = (int)result;
            return String.format("%d %s", (int)result, unit);
        }
        return String.format("%01.2f %s", result, unit);
    }
}
