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
package org.pixelexperience.ota.model;

import java.io.File;

public class Update extends UpdateBase implements UpdateInfo {

    public static final String LOCAL_ID = "local";

    private UpdateStatus mStatus = UpdateStatus.UNKNOWN;
    private File mFile;
    private int mProgress;
    private long mEta;
    private long mSpeed;
    private int mInstallProgress;
    private boolean mIsFinalizing;
    private String mHash;

    public Update() {
    }

    public Update(UpdateInfo update) {
        super(update);
        mStatus = update.getStatus();
        mFile = update.getFile();
        mHash = update.getHash();
    }

    @Override
    public UpdateStatus getStatus() {
        return mStatus;
    }

    public void setStatus(UpdateStatus status) {
        mStatus = status;
    }

    @Override
    public File getFile() {
        return mFile;
    }

    public void setFile(File file) {
        mFile = file;
    }

    @Override
    public String getHash() {
        return mHash;
    }

    public void setHash(String hash) {
        mHash = hash;
    }
}
