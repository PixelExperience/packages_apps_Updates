/*
 * Copyright (C) 2017 Henrique Silva (jhenrique09)
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */
package com.purenexus.ota;

import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.view.View;
import android.widget.Toast;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Activity;
import android.util.Log;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import java.io.File;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;

import com.woxthebox.draglistview.DragListView;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;


import com.purenexus.ota.misc.ItemAdapter;
import com.purenexus.ota.utils.Utils;
import com.purenexus.ota.misc.Constants;

import java.util.ArrayList;

public class InstallActivity extends AppCompatActivity {

    private ArrayList<Pair<Long, String>> mItemArray;
    private DragListView mDragListView;
    private ProgressDialog mProgressDialog;
    private FloatingActionMenu fab;
    private FloatingActionButton fabAddFile;
    private FloatingActionButton fabRemoveAllFiles;
    private FloatingActionButton fabWipe;
    private FloatingActionButton fabInstall;
    public FrameLayout noFilesLayout;

    private String rom_fileName = "";
    private String NEW_LINE = "\n";
    private StringBuilder mScript = new StringBuilder();

    private SharedPreferences mPrefs;
    private static String TAG = "InstallActivity";

    private static final int READ_REQUEST_CODE = 8794;

    private static int INSTALL_REQUEST_CODE = 9087;
    private static int ADD_FILE_REQUEST_CODE = 9088;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_install);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        mDragListView = (DragListView) findViewById(R.id.drag_list_view);
        fab = (FloatingActionMenu) findViewById(R.id.fab_menu);
        fabAddFile = (FloatingActionButton) findViewById(R.id.fab_add_file);
        fabRemoveAllFiles = (FloatingActionButton) findViewById(R.id.fab_remove_all_files);
        fabWipe = (FloatingActionButton) findViewById(R.id.fab_wipe);
        fabInstall = (FloatingActionButton) findViewById(R.id.fab_install);
        noFilesLayout = (FrameLayout) findViewById(R.id.no_files);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(InstallActivity.this);
        mDragListView.getRecyclerView().setVerticalScrollBarEnabled(true);
        mDragListView.setCanDragHorizontally(false);


        fabAddFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.close(true);
                if (!isStoragePermissionGranted(ADD_FILE_REQUEST_CODE)) {
                    Toast.makeText(InstallActivity.this, getString(R.string.storage_permission_error), Toast.LENGTH_SHORT).show();
                }else{
                    performFileSearch();
                }
            }
        });

        fabRemoveAllFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.close(true);
                mItemArray = new ArrayList<>();
                setupListRecyclerView();
            }
        });

        fabWipe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.close(true);
                LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                final View wipe_layout = inflater.inflate(R.layout.wipe_layout,null, false);
                final CheckBox wipe_cache = (CheckBox) wipe_layout.findViewById(R.id.wipe_cache);
                final CheckBox wipe_dalvik = (CheckBox) wipe_layout.findViewById(R.id.wipe_dalvik);
                final CheckBox wipe_data = (CheckBox) wipe_layout.findViewById(R.id.wipe_data);

                wipe_cache.setChecked(mPrefs.getBoolean(Constants.WIPE_CACHE_PREF, Constants.WIPE_CACHE_BY_DEFAULT));
                wipe_dalvik.setChecked(mPrefs.getBoolean(Constants.WIPE_DALVIK_PREF, Constants.WIPE_DALVIK_BY_DEFAULT));
                wipe_data.setChecked(mPrefs.getBoolean(Constants.WIPE_DATA_PREF, Constants.WIPE_DATA_BY_DEFAULT));

                new AlertDialog.Builder(InstallActivity.this)
                .setView(wipe_layout)
                .setTitle(getString(R.string.fab_wipe_title))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mPrefs.edit().putBoolean(Constants.WIPE_CACHE_PREF, wipe_cache.isChecked()).apply();
                        mPrefs.edit().putBoolean(Constants.WIPE_DALVIK_PREF, wipe_dalvik.isChecked()).apply();
                        mPrefs.edit().putBoolean(Constants.WIPE_DATA_PREF, wipe_data.isChecked()).apply();
                    }
                })
                .show();
            }
        });

        fabInstall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fab.close(true);
                if (mItemArray.size() > 0){
                    new AlertDialog.Builder(InstallActivity.this)
                    .setTitle(R.string.install_title)
                    .setMessage(R.string.install_dialog_message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (!isStoragePermissionGranted(INSTALL_REQUEST_CODE)) {
                                Toast.makeText(InstallActivity.this, getString(R.string.storage_permission_error), Toast.LENGTH_SHORT).show();
                            }else{
                                checkRootPreInstall();
                            }
                        }
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();
                }else{
                    Toast.makeText(InstallActivity.this, getString(R.string.no_files), Toast.LENGTH_SHORT).show();
                }
            }
        });
        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if(extras == null) {
                rom_fileName = null;
            } else {
                rom_fileName = extras.getString("rom");
            }
        } else {
            rom_fileName = (String) savedInstanceState.getSerializable("rom");
        }

        mItemArray = new ArrayList<>();
        if (rom_fileName != null && !rom_fileName.isEmpty()){
            mItemArray.add(new Pair<>((long) 0, rom_fileName));
        }

        setupListRecyclerView();
    }

    private void checkRootPreInstall(){
        new AsyncTask<Void, Void, Boolean>() {
            protected void onPreExecute() {
                mProgressDialog = new ProgressDialog(InstallActivity.this);
                mProgressDialog.setMessage(getString(R.string.checking_root));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
            }
            protected Boolean doInBackground(Void... params) {
                Boolean haveRootAccess = Utils.getRoot();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                return haveRootAccess;
            }
            protected void onPostExecute(Boolean result) {
                if (mProgressDialog != null){
                    mProgressDialog.hide();
                    mProgressDialog = null;
                }
                if (!result){
                    new AlertDialog.Builder(InstallActivity.this)
                    .setTitle(R.string.available_reboot_manual_title)
                    .setMessage(R.string.available_reboot_manual_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
                }else{
                    doInstall();
                }

            }
        }.execute();
    }

    private void doInstall(){
        mScript = new StringBuilder();
        if (mPrefs.getBoolean(Constants.WIPE_DATA_PREF, Constants.WIPE_DATA_BY_DEFAULT)){
            mScript.append("wipe data").append(NEW_LINE);
        }
        if (mPrefs.getBoolean(Constants.WIPE_CACHE_PREF, Constants.WIPE_CACHE_BY_DEFAULT)){
            mScript.append("wipe cache").append(NEW_LINE);
        }
        if (mPrefs.getBoolean(Constants.WIPE_DALVIK_PREF, Constants.WIPE_DALVIK_BY_DEFAULT)){
            mScript.append("wipe dalvik").append(NEW_LINE);
        }

        if (rom_fileName != null && !rom_fileName.isEmpty()){
            File file = new File(rom_fileName);
            mScript.append("install /sdcard/" + Constants.UPDATES_FOLDER + "/" + file.getName()).append(NEW_LINE);
        }


        new AsyncTask<Void, String, String>() {
            protected void onPreExecute() {
                mProgressDialog = new ProgressDialog(InstallActivity.this);
                mProgressDialog.setMessage(getString(R.string.please_wait));
                mProgressDialog.setIndeterminate(true);
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
            }

            protected void onProgressUpdate(String... text) {
                if (mProgressDialog != null){
                    mProgressDialog.setMessage(text[0]);
                }
            }
            protected String doInBackground(Void... params) {
                Utils.deleteTempFolder();

                for (final Pair<Long, String> pair : mItemArray) {
                    if (!pair.second.equals(rom_fileName)){
                        publishProgress(String.format(getString(R.string.copy_progress), pair.second));
                        File source = new File(pair.second);
                        String newSourceName = source.getName().replaceAll("[^a-zA-Z0-9.-]", "_");
                        File dest = new File(Utils.makeTempFolder().getPath() + "/" + newSourceName);
                        if (!Utils.copyFile(source, dest)){
                            return "ERROR;" + pair.second;
                        }else{
                            mScript.append("install /sdcard/" + Constants.UPDATES_FOLDER + "/temp/" + newSourceName).append(NEW_LINE);
                        }
                    }
                }
                mScript.append("cmd rm -rf /sdcard/" + Constants.UPDATES_FOLDER + "/temp/").append(NEW_LINE);

                String scriptFinal = mScript.toString().trim();

                String SCRIPT_FILE = "/cache/recovery/openrecoveryscript";
                Utils.shell("mkdir -p /cache/recovery/", true);
                Utils.shell("echo \"" + scriptFinal + "\" > " + SCRIPT_FILE + "\n", true);
                String out = Utils.shell("cat " + SCRIPT_FILE, true).trim();

                String SCRIPT_FILE2 = "/data/cache/recovery/openrecoveryscript";
                Utils.shell("mkdir -p /data/cache/recovery/", true);
                Utils.shell("echo \"" + scriptFinal + "\" > " + SCRIPT_FILE2 + "\n", true);
                String out2 = Utils.shell("cat " + SCRIPT_FILE2, true).trim();

                boolean isScriptWritten = out.equals(scriptFinal) || out2.equals(scriptFinal);

                if (out != null && !out.isEmpty() && isScriptWritten){
                    return "OK";
                }else{
                    return "ERROR_WRITE_ORS";
                }
            }

            protected void onPostExecute(String result) {
                if (mProgressDialog != null){
                    mProgressDialog.hide();
                    mProgressDialog = null;
                }
                if (result.equals("OK")){
                    try{
                        Utils.recovery(InstallActivity.this);
                    }catch(Exception ex){
                    }
                    new AlertDialog.Builder(InstallActivity.this)
                    .setTitle(R.string.restart_recovery)
                    .setMessage(R.string.restart_recovery_error_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
                }else if (result.startsWith("ERROR;")){
                    String path = result.substring(6);
                    Utils.deleteTempFolder();
                    mScript = new StringBuilder();
                    new AlertDialog.Builder(InstallActivity.this)
                    .setTitle(R.string.install_title)
                    .setMessage(String.format(getString(R.string.copy_failed), path))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
                }else if (result.startsWith("ERROR_WRITE_ORS")){
                    Utils.deleteTempFolder();
                    mScript = new StringBuilder();
                    new AlertDialog.Builder(InstallActivity.this)
                    .setTitle(R.string.install_title)
                    .setMessage(R.string.install_write_ors_failed_dialog_message)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try{
                                Utils.recovery(InstallActivity.this);
                            }catch(Exception ex){
                            }
                            new AlertDialog.Builder(InstallActivity.this)
                            .setTitle(R.string.restart_recovery)
                            .setMessage(R.string.restart_recovery_error_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                        }
                    })
                    .setNegativeButton(R.string.dialog_cancel, null)
                    .show();

                }
            }
        }.execute();
    }

    public void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    private void setupListRecyclerView() {
        mDragListView.setLayoutManager(new LinearLayoutManager(InstallActivity.this));
        ItemAdapter listAdapter = new ItemAdapter(mItemArray, R.layout.list_item, R.id.image, false, InstallActivity.this);
        mDragListView.setAdapter(listAdapter, true);
        if (mItemArray.size() > 0){
            noFilesLayout.setVisibility(View.GONE);
        }else{
            noFilesLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
            Utils.deleteTempFolder();
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Utils.deleteTempFolder();
        finish();
        return;
    }   

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                String realPath = Utils.getRealPathFromURI(InstallActivity.this, uri);
                Log.i(TAG, "Uri: " + uri.toString() + " realpath " + realPath);
                if (realPath != null && !realPath.isEmpty()){

                    if (realPath.endsWith(".zip")){

                       ArrayList<Pair<Long, String>> mTempItemArray;
                       mTempItemArray = new ArrayList<>();
                       Integer i = 0;
                       for (Pair<Long, String> pair : mItemArray) {
                        mTempItemArray.add(new Pair<>((long) i, pair.second));
                        i++;
                    }
                    mTempItemArray.add(new Pair<>((long) i, realPath));
                    mItemArray = mTempItemArray;
                    setupListRecyclerView();
                }else{
                    Toast.makeText(InstallActivity.this, getString(R.string.file_not_allowed), Toast.LENGTH_SHORT).show();
                }
            }else{
                Toast.makeText(InstallActivity.this, getString(R.string.file_get_path_error), Toast.LENGTH_SHORT).show();
            }
        }
        }
    }

    public boolean isStoragePermissionGranted(int requestCode) {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }else{
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
                return false;
            }
        }else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == INSTALL_REQUEST_CODE && grantResults[0]== PackageManager.PERMISSION_GRANTED){
            checkRootPreInstall();
        }
        if(requestCode == ADD_FILE_REQUEST_CODE && grantResults[0]== PackageManager.PERMISSION_GRANTED){
            performFileSearch();
        }
    }

}
