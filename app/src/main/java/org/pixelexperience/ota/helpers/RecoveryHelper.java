/*
 * Copyright (C) 2014 ParanoidAndroid Project
 * Copyright (C) 2018 Pixel Experience (jhenrique09)
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */
package org.pixelexperience.ota.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.utils.Utils;

import java.util.ArrayList;
import java.util.List;

class RecoveryHelper {

    private static final String TAG = "RecoveryHelper";

    static String getCommandsFile() {
        return "openrecoveryscript";
    }

    static String[] getCommands(String filename, Context context)
            throws Exception {

        List<String> commands = new ArrayList<>();
        commands.add("install " + filename);

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        Boolean deleteAfterInstall = mPrefs.getBoolean(Constants.DELETE_AFTER_INSTALL_PREF, Constants.DELETE_AFTER_INSTALL_DEFAULT);
        Boolean wipeCache = mPrefs.getBoolean(Constants.WIPE_CACHE_PREF, Constants.WIPE_CACHE_BY_DEFAULT);
        Boolean wipeDalvikCache = mPrefs.getBoolean(Constants.WIPE_DALVIK_PREF, Constants.WIPE_DALVIK_BY_DEFAULT);
        Boolean wipeData = mPrefs.getBoolean(Constants.WIPE_DATA_PREF, Constants.WIPE_DATA_BY_DEFAULT);

        if (wipeCache){
            commands.add("wipe cache");
        }

        if (wipeDalvikCache){
            commands.add("wipe dalvik");
        }

        if (wipeData){
            commands.add("wipe data");
        }

        if (deleteAfterInstall){
            commands.add("cmd rm -rf " + filename);
        }

        return commands.toArray(new String[commands.size()]);

    }

    static String getRecoveryFilePath(String filePath) {
        String internalStorage = "sdcard";
        String externalStorage = "external_sd";

        String[] sdcardPath = Utils.getSdcardPaths();

        String primarySdcard = sdcardPath[0];
        String secondarySdcard = sdcardPath[1];

        boolean useInternal = false;

        String[] internalNames = new String[]{
                primarySdcard == null ? "NOPE" : primarySdcard,
                "/mnt/sdcard",
                "/storage/sdcard/",
                "/sdcard",
                "/storage/sdcard0",
                "/storage/emulated/0"
        };

        String[] externalNames = new String[]{
                secondarySdcard == null ? "NOPE" : secondarySdcard,
                "/mnt/extSdCard",
                "/storage/extSdCard/",
                "/extSdCard",
                "/storage/sdcard1",
                "/storage/emulated/1"
        };

        Log.v(TAG, "getRecoveryFilePath:filePath = " + filePath);

        for (String internalName : internalNames) {
            Log.v(TAG, "getRecoveryFilePath:checking internalName = " + internalName);

            if (filePath.startsWith(internalName)) {
                filePath = filePath.replace(internalName, "/" + internalStorage);

                useInternal = true;

                break;
            }
        }

        if (!useInternal) {
            for (String externalName : externalNames) {
                Log.v(TAG, "getRecoveryFilePath:checking externalName = " + externalName);

                if (filePath.startsWith(externalName)) {
                    filePath = filePath.replace(externalName, "/" + externalStorage);
                    break;
                }
            }
        }

        while (filePath.startsWith("//")) {
            filePath = filePath.substring(1);
        }

        Log.v(TAG, "getRecoveryFilePath:new filePath = " + filePath);

        return filePath;
    }
}
