/*
    Copyright (C) 2019 Pixel Experience

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */
package org.pixelexperience.ota;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class LocalChangelogActivity extends AppCompatActivity {

    private static final String CHANGELOG_PATH = "/system/etc/Changelog.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_changelog);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        final ProgressDialog dialog = new ProgressDialog(this, R.style.AppTheme_AlertDialogStyle);
        final Handler mHandler = new Handler();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setMessage(getString(R.string.changelog_loading));
        dialog.show();
        mHandler.postDelayed(() -> {
            StringBuilder data = new StringBuilder();
            int numRead;
            char[] tmp = new char[2048];
            try (InputStreamReader inputReader = new FileReader(CHANGELOG_PATH)) {

                while ((numRead = inputReader.read(tmp)) >= 0) {
                    data.append(tmp, 0, numRead);
                }
            } catch (IOException ignored) {
            }

            final TextView textView = findViewById(R.id.changelog_text);
            textView.setText(data);

            mHandler.postDelayed(dialog::dismiss, 1000);
        }, 2000);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

}
