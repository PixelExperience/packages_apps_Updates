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

    private UpdateStatus mStatus = UpdateStatus.UNKNOWN;
    private File mFile;
    private int mProgress;
    private long mEta;
    private long mSpeed;
    private int mInstallProgress;
    private boolean mIsFinalizing;
    private String mHash;
    private boolean mIsIncremental;
    private boolean mHasIncremental;

    public Update() {
    }

    public Update(UpdateInfo update) {
        super(update);
        mStatus = update.getStatus();
        mFile = update.getFile();
        mProgress = update.getProgress();
        mEta = update.getEta();
        mSpeed = update.getSpeed();
        mInstallProgress = update.getInstallProgress();
        mIsFinalizing = update.getFinalizing();
        mHash = update.getHash();
        mIsIncremental = update.getIsIncremental();
        mHasIncremental = update.getHasIncremental();
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
    public int getProgress() {
        return mProgress;
    }

    public void setProgress(int progress) {
        mProgress = progress;
    }

    @Override
    public long getEta() {
        return mEta;
    }

    public void setEta(long eta) {
        mEta = eta;
    }

    @Override
    public long getSpeed() {
        return mSpeed;
    }

    public void setSpeed(long speed) {
        mSpeed = speed;
    }

    @Override
    public int getInstallProgress() {
        return mInstallProgress;
    }

    public void setInstallProgress(int progress) {
        mInstallProgress = progress;
    }

    @Override
    public boolean getFinalizing() {
        return mIsFinalizing;
    }

    public void setFinalizing(boolean finalizing) {
        mIsFinalizing = finalizing;
    }

    @Override
    public String getHash() {
        return mHash;
    }

    public void setHash(String hash) {
        mHash = hash;
    }

    @Override
    public boolean getIsIncremental() {
        return mIsIncremental;
    }

    public void setIsIncremental(boolean isIncremental) {
        mIsIncremental = isIncremental;
    }

    @Override
    public boolean getHasIncremental() {
        return mHasIncremental;
    }

    public void setHasIncremental(boolean hasIncremental) {
        mHasIncremental = hasIncremental;
    }
}
