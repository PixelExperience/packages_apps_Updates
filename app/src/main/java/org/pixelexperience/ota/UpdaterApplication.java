/*
 * Copyright (C) 2012 The CyanogenMod Project
 * Copyright (C) 2017 The LineageOS Project
 * Copyright (C) 2018 Pixel Experience (jhenrique09)
 *
 * * Licensed under the GNU GPLv2 license
 *
 * The text of the license can be found in the LICENSE file
 * or at https://www.gnu.org/licenses/gpl-2.0.txt
 */
package org.pixelexperience.ota;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import org.pixelexperience.ota.activities.UpdaterActivity;

public class UpdaterApplication extends Application implements
        Application.ActivityLifecycleCallbacks {

    private boolean mMainActivityActive;
    private RequestQueue mRequestQueue;

    @Override
    public void onCreate() {
        super.onCreate();
        mMainActivityActive = false;
        registerActivityLifecycleCallbacks(this);
        mRequestQueue = Volley.newRequestQueue(this);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (activity instanceof UpdaterActivity) {
            mMainActivityActive = true;
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (activity instanceof UpdaterActivity) {
            mMainActivityActive = false;
        }
    }

    public boolean isMainActivityActive() {
        return mMainActivityActive;
    }

    public RequestQueue getQueue() {
        return mRequestQueue;
    }
}
