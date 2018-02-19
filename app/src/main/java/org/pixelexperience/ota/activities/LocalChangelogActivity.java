/*
    Copyright (C) 2018 Pixel Experience

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
package org.pixelexperience.ota.activities;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalChangelogActivity extends Activity {

    private static final String CHANGELOG_PATH = "/system/etc/Changelog.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        InputStreamReader inputReader = null;
        String text = "";

        StringBuilder data = new StringBuilder();
        Pattern p2 = Pattern.compile("\\s+\\*\\s(([\\w_.-]+/)+)");
        Pattern p3 = Pattern.compile("(\\d\\d\\-\\d\\d\\-\\d{4})");
        try {
            char tmp[] = new char[2048];
            int numRead;

            inputReader = new FileReader(CHANGELOG_PATH);
            while ((numRead = inputReader.read(tmp)) >= 0) {
                data.append(tmp, 0, numRead);
            }
        } catch (IOException ignored) {
        } finally {
            try {
                if (inputReader != null) {
                    inputReader.close();
                }
            } catch (IOException ignored) {
            }
        }

        SpannableStringBuilder sb = new SpannableStringBuilder(data);
        Resources.Theme theme = getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        final int color = getColor(typedValue.resourceId);
        Matcher m = p2.matcher(data);
        while (m.find()){
            sb.setSpan(new StyleSpan(Typeface.BOLD),m.start(0), m.end(0), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(color),m.start(0),m.end(0),Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        m = p3.matcher(data);
        while (m.find()){
            sb.setSpan(new StyleSpan(Typeface.BOLD+ Typeface.ITALIC),m.start(1), m.end(1), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        final TextView textView = new TextView(this);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        llp.setMargins(20, 0, 0, 0); // llp.setMargins(left, top, right, bottom);
        textView.setLayoutParams(llp);
        textView.setText(sb);

        final ScrollView scrollView = new ScrollView(this);
        scrollView.addView(textView);

        setContentView(scrollView);
    }
}