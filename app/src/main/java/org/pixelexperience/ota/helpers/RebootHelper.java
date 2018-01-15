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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.PowerManager;

import org.pixelexperience.ota.R;
import org.pixelexperience.ota.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;

public class RebootHelper {
    public static void showRebootDialog(final Context context, final String filename) {
        AlertDialog.Builder alert = new AlertDialog.Builder(context);
        alert.setTitle(R.string.reboot_title);
        alert.setMessage(context.getResources().getString(R.string.reboot_message));
        alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
                reboot(context, filename);

            }
        });
        alert.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        alert.show();
    }

    private static void reboot(final Context context, final String filename) {
        try {
            File f = new File("/cache/recovery/command");
            f.delete();
            String file = RecoveryHelper.getCommandsFile();
            FileOutputStream os = null;
            try {
                os = new FileOutputStream("/cache/recovery/" + file, false);

                String[] commands = RecoveryHelper.getCommands(RecoveryHelper.getRecoveryFilePath(filename));
                int size = commands.length, j = 0;
                for (; j < size; j++) {
                    os.write((commands[j] + "\n").getBytes("UTF-8"));
                }
            } finally {
                if (os != null) {
                    os.close();
                    Utils.setPermissions("/cache/recovery/" + file, 0644,
                            android.os.Process.myUid(), 2001);
                }
            }
            ((PowerManager) context.getSystemService(Activity.POWER_SERVICE)).reboot("recovery");
        } catch (Exception e) {
            AlertDialog.Builder alert = new AlertDialog.Builder(context);
            alert.setTitle(R.string.reboot_failed_title);
            alert.setMessage(String.format(context.getResources().getString(R.string.reboot_failed_message), filename));
            alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();

                }
            });
            alert.show();
            e.printStackTrace();
        }
    }
}
