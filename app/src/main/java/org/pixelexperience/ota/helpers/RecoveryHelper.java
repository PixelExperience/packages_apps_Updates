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

import android.util.Log;

import org.pixelexperience.ota.utils.Utils;

import java.util.ArrayList;
import java.util.List;

class RecoveryHelper {

    private static final String TAG = "RecoveryHelper";

    static String getCommandsFile() {
        return "openrecoveryscript";
    }

    static String[] getCommands(String filename)
            throws Exception {

        List<String> commands = new ArrayList<>();
        commands.add("install " + filename);

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
