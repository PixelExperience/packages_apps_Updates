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
package org.pixelexperience.ota.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.widget.ListView;
import android.widget.Toast;

import org.pixelexperience.ota.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AddonsActivity extends PreferenceActivity implements Preference.OnPreferenceClickListener {
    private List<Map<String, String>> addons;

    @Override
    public boolean isValidFragment(String fragmentName) {
        return AddonsActivity.class.getName().equals(fragmentName);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        ListView lv = getListView();
        lv.setDivider(new ColorDrawable(Color.TRANSPARENT));
        lv.setDividerHeight(0);

        addPreferencesFromResource(R.xml.preference_addons);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        try {
            addons = (ArrayList<Map<String, String>>) getIntent().getSerializableExtra("addons");
        } catch (Exception ignored) {
        }
        try {
            if (addons.size() > 0) {
                preferenceScreen.removeAll();
                for (Map<String, String> addon : addons) {
                    Preference preference = new Preference(preferenceScreen.getContext());
                    preference.setTitle(addon.get("title"));
                    preference.setSummary(addon.get("summary"));
                    preference.setKey("addon_" + addon.get("url"));
                    preference.setOnPreferenceClickListener(this);
                    preference.setLayoutResource(R.layout.preference_material_settings);
                    preferenceScreen.addPreference(preference);
                }
            } else {
                Toast.makeText(AddonsActivity.this, getString(R.string.addons_error), Toast.LENGTH_SHORT).show();
                finish();
            }
        } catch (Exception ex) {
            Toast.makeText(AddonsActivity.this, getString(R.string.addons_error), Toast.LENGTH_SHORT).show();
            finish();
        }
    }


    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key;
        try {
            key = preference.getKey();
            if (key == null) {
                key = "";
            }
        } catch (Exception ex) {
            key = "";
        }

        if (key.startsWith("addon_")) {
            String url = key.substring(6);
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(AddonsActivity.this, getString(R.string.error_open_url), Toast.LENGTH_SHORT).show();
            }
        }
        return false;
    }


}