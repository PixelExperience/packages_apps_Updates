/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2018 Pixel Experience
 * Copyright (C) 2017 The LineageOS Project
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */

package org.pixelexperience.ota.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.pixelexperience.ota.utils.UpdateChecker;
import org.pixelexperience.ota.utils.Utils;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Utils.deleteTempFolder();
            UpdateChecker.scheduleUpdateService(context, true);
        }
    }
}
