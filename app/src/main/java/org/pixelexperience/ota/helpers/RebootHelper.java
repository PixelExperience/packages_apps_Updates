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
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import org.pixelexperience.ota.R;
import org.pixelexperience.ota.misc.Constants;
import org.pixelexperience.ota.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;

public class RebootHelper {
    public static void showRebootDialog(final Context context, final String filename) {
        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        View checkboxDeleteAfterInstallView = LayoutInflater.from(context).inflate(R.layout.checkbox_view, null);
        final CheckBox checkBoxDeleteAfterInstall = checkboxDeleteAfterInstallView.findViewById(R.id.checkbox);
        checkBoxDeleteAfterInstall.setText(R.string.delete_after_install_title);
        checkBoxDeleteAfterInstall.setChecked(mPrefs.getBoolean(Constants.DELETE_AFTER_INSTALL_PREF, Constants.DELETE_AFTER_INSTALL_DEFAULT));
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setView(checkboxDeleteAfterInstallView);
        builder.setTitle(R.string.reboot_title);
        builder.setMessage(context.getResources().getString(R.string.reboot_message));
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which){}
        });
        builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which){}
        });
        builder.setNeutralButton(R.string.advanced_options, new DialogInterface.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which){}
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                mPrefs.edit().putBoolean(Constants.DELETE_AFTER_INSTALL_PREF, checkBoxDeleteAfterInstall.isChecked()).apply();
                rebootWithWipeDataConfirmation(context, filename, dialog);
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                dialog.dismiss();
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                showAdvancedOptionsDialog(context);
            }
        });
    }
    private static void showAdvancedOptionsDialog(final Context context) {
        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        View advancedInstallView = LayoutInflater.from(context).inflate(R.layout.advanced_install_options, null);
        final CheckBox wipeCache = advancedInstallView.findViewById(R.id.wipe_cache);
        final CheckBox wipeDalvikCache = advancedInstallView.findViewById(R.id.wipe_dalvik_cache);
        final CheckBox wipeData = advancedInstallView.findViewById(R.id.wipe_data);
        wipeCache.setChecked(mPrefs.getBoolean(Constants.WIPE_CACHE_PREF, Constants.WIPE_CACHE_BY_DEFAULT));
        wipeDalvikCache.setChecked(mPrefs.getBoolean(Constants.WIPE_DALVIK_PREF, Constants.WIPE_DALVIK_BY_DEFAULT));
        wipeData.setChecked(mPrefs.getBoolean(Constants.WIPE_DATA_PREF, Constants.WIPE_DATA_BY_DEFAULT));
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setView(advancedInstallView);
        builder.setTitle(R.string.advanced_options);
        builder.setPositiveButton(R.string.save_advanced_options, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mPrefs.edit().putBoolean(Constants.WIPE_CACHE_PREF, wipeCache.isChecked()).apply();
                mPrefs.edit().putBoolean(Constants.WIPE_DALVIK_PREF, wipeDalvikCache.isChecked()).apply();
                mPrefs.edit().putBoolean(Constants.WIPE_DATA_PREF, wipeData.isChecked()).apply();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
            }
        });
        final AlertDialog dialog = builder.create();
        dialog.show();
    }
    private static void rebootWithWipeDataConfirmation(final Context context, final String filename, final AlertDialog parentDialog) {
        final SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Boolean wipeData = mPrefs.getBoolean(Constants.WIPE_DATA_PREF, Constants.WIPE_DATA_BY_DEFAULT);
        if (!wipeData){
            reboot(context, filename, parentDialog);
        }else {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setCancelable(false);
            builder.setTitle(R.string.wipe_data_dialog_confirmation_title);
            builder.setMessage(R.string.wipe_data_dialog_confirmation_message);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    reboot(context, filename, parentDialog);
                    dialog.dismiss();
                }
            });
            builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });
            final AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private static void reboot(final Context context, final String filename, final AlertDialog parentDialog) {
        try {
            File f = new File("/cache/recovery/command");
            f.delete();
            String file = RecoveryHelper.getCommandsFile();
            FileOutputStream os = null;
            try {
                os = new FileOutputStream("/cache/recovery/" + file, false);

                String[] commands = RecoveryHelper.getCommands(RecoveryHelper.getRecoveryFilePath(filename), context);
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
            if (parentDialog != null){
                parentDialog.dismiss();
            }
            e.printStackTrace();
        }
    }
}
