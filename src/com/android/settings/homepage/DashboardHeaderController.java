/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.settings.homepage;

import android.content.Context;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settings.deviceinfo.DeviceNamePreferenceController;
import com.android.settingslib.widget.LayoutPreference;
import com.android.settings.R;

public class DashboardHeaderController extends BasePreferenceController {

    private LayoutPreference mPreference;
    private TextView aboutSummary, basecampTitle, basecampSummary;

    public DashboardHeaderController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
		aboutSummary = mPreference.findViewById(R.id.dashboard_about_summary);
                basecampTitle = mPreference.findViewById(R.id.dashboard_basecamp_title);
		basecampSummary = mPreference.findViewById(R.id.dashboard_basecamp_summary);
		aboutSummary.setText(getDeviceSummary());
		aboutSummary.setSelected(true);
                basecampTitle.setSelected(true);
		basecampSummary.setSelected(true);
    }

    private CharSequence getDeviceSummary() {
        final DeviceNamePreferenceController deviceNamePreferenceController = new DeviceNamePreferenceController(mContext, "unused_key");
        return deviceNamePreferenceController.getSummary();
    }

}
