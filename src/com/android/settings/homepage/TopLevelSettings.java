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

import static com.android.settings.search.actionbar.SearchMenuController.NEED_SEARCH_ICON_IN_ACTION_BAR;
import static com.android.settingslib.search.SearchIndexable.MOBILE;

import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.VisibleForTesting;
import android.os.UserHandle;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;
import androidx.window.embedding.ActivityEmbeddingController;
import androidx.window.embedding.SplitController;
import androidx.recyclerview.widget.RecyclerView;

import android.provider.Settings;
import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.support.SupportPreferenceController;
import com.android.settings.widget.HomepagePreference;
import com.android.settings.widget.HomepagePreferenceLayoutHelper.HomepagePreferenceLayout;
import com.android.settingslib.core.instrumentation.Instrumentable;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.search.SearchIndexable;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@SearchIndexable(forTarget = MOBILE)
public class TopLevelSettings extends DashboardFragment implements SplitLayoutListener,
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final String TAG = "TopLevelSettings";
    private static final String KEY_BASECAMP = "top_level_basecamp";
    private static final String SAVED_HIGHLIGHT_MIXIN = "highlight_mixin";
    private static final String PREF_KEY_SUPPORT = "top_level_support";

    private int mAboutPhoneStyle;
    private int mDashBoardStyle;

    private boolean mIsEmbeddingActivityEnabled;
    private TopLevelHighlightMixin mHighlightMixin;
    private int mPaddingHorizontal;
    private boolean mScrollNeeded = true;
    private boolean mFirstStarted = true;
    private ActivityEmbeddingController mActivityEmbeddingController;
    private boolean gAppsExists;

    public TopLevelSettings() {
        final Bundle args = new Bundle();
        // Disable the search icon because this page uses a full search view in actionbar.
        args.putBoolean(NEED_SEARCH_ICON_IN_ACTION_BAR, false);
        setArguments(args);
    }

    /** Dependency injection ctor only for testing. */
    @VisibleForTesting
    public TopLevelSettings(TopLevelHighlightMixin highlightMixin) {
        this();
        mHighlightMixin = highlightMixin;
    }

    @Override
    protected int getPreferenceScreenResId() {
        switch (mDashBoardStyle) {
           case 0:
               return R.xml.top_level_settings;
           case 1:
               return R.xml.top_level_settings_non_header;
           case 2:
               return R.xml.top_level_settings_single;
           default:
               return R.xml.top_level_settings_non_header;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final RecyclerView recyclerView = getView().findViewById(R.id.recycler_view);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DASHBOARD_SUMMARY;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Check if Google Apps exist and set the gAppsExists flag accordingly
        gAppsExists = checkIfGoogleAppsExist(context);
        HighlightableMenu.fromXml(context, getPreferenceScreenResId());
        use(SupportPreferenceController.class).setActivity(getActivity());
        updateLabSummary();
        getAboutPhoneStyle(context);
        setDashboardStyle(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLabSummary();
    }

    @Override
    public int getHelpResource() {
        // Disable the help icon because this page uses a full search view in actionbar.
        return 0;
    }

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (isDuplicateClick(preference)) {
            return true;
        }

        // Register SplitPairRule for SubSettings.
        ActivityEmbeddingRulesController.registerSubSettingsPairRule(getContext(),
                true /* clearTop */);

        setHighlightPreferenceKey(preference.getKey());
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        new SubSettingLauncher(getActivity())
                .setDestination(pref.getFragment())
                .setArguments(pref.getExtras())
                .setSourceMetricsCategory(caller instanceof Instrumentable
                        ? ((Instrumentable) caller).getMetricsCategory()
                        : Instrumentable.METRICS_CATEGORY_UNKNOWN)
                .setTitleRes(-1)
                .setIsSecondLayerPage(true)
                .launch();
        return true;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mIsEmbeddingActivityEnabled =
                ActivityEmbeddingUtils.isEmbeddingActivityEnabled(getContext());
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }

        boolean activityEmbedded = isActivityEmbedded();
        if (icicle != null) {
            mHighlightMixin = icicle.getParcelable(SAVED_HIGHLIGHT_MIXIN);
            if (mHighlightMixin != null) {
                mScrollNeeded = !mHighlightMixin.isActivityEmbedded() && activityEmbedded;
                mHighlightMixin.setActivityEmbedded(activityEmbedded);
            }
        }
        if (mHighlightMixin == null) {
            mHighlightMixin = new TopLevelHighlightMixin(activityEmbedded);
        }
    }

    /** Wrap ActivityEmbeddingController#isActivityEmbedded for testing. */
    @VisibleForTesting
    public boolean isActivityEmbedded() {
        if (mActivityEmbeddingController == null) {
            mActivityEmbeddingController = ActivityEmbeddingController.getInstance(getActivity());
        }
        return mActivityEmbeddingController.isActivityEmbedded(getActivity());
    }

    @Override
    public void onStart() {
        if (mFirstStarted) {
            mFirstStarted = false;
            FeatureFactory.getFeatureFactory().getSearchFeatureProvider().sendPreIndexIntent(
                    getContext());
        } else if (mIsEmbeddingActivityEnabled && isOnlyOneActivityInTask()
                && !isActivityEmbedded()) {
            // Set default highlight menu key for 1-pane homepage since it will show the placeholder
            // page once changing back to 2-pane.
            Log.i(TAG, "Set default menu key");
            setHighlightMenuKey(getString(SettingsHomepageActivity.DEFAULT_HIGHLIGHT_MENU_KEY),
                    /* scrollNeeded= */ false);
        }
        super.onStart();
    }

    private boolean isOnlyOneActivityInTask() {
        final ActivityManager.RunningTaskInfo taskInfo = getSystemService(ActivityManager.class)
                .getRunningTasks(1).get(0);
        return taskInfo.numActivities == 1;
    }

    private boolean checkIfGoogleAppsExist(Context context) {
        // Perform the necessary check to determine if Google Apps exist
        // For example, you might use PackageManager to check for the existence of a Google app package
        PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getPackageInfo("com.google.android.gsf", 0);
            return true; // Google Apps exist
        } catch (PackageManager.NameNotFoundException e) {
            return false; // Google Apps do not exist
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mHighlightMixin != null) {
            outState.putParcelable(SAVED_HIGHLIGHT_MIXIN, mHighlightMixin);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        int tintColor = Utils.getHomepageIconColor(getContext());
        iteratePreferences(preference -> {
            Drawable icon = preference.getIcon();
            if (icon != null) {
                icon.setTint(tintColor);
            }
        });
        onSetPrefCard();
    }

    private void onSetPrefCard() {
        final PreferenceScreen screen = getPreferenceScreen();
        final int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            final Preference preference = screen.getPreference(i);

            String key = preference.getKey();
            
       switch (mDashBoardStyle) {
	case 0:
            if (key.equals("top_level_network")
            	|| key.equals("top_level_display")
            	|| key.equals("top_level_apps")
            	|| key.equals("top_level_accessibility")
                || key.equals("top_level_system")){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_top);
            } else if (key.equals("top_level_battery")
              || key.equals("top_level_wallpaper")
            	|| key.equals("top_level_security")
            	|| key.equals("top_level_privacy")
            	|| key.equals("top_level_safety_center")
            	|| key.equals("top_level_wellbeing")
            	|| key.equals("top_level_location")
            	|| key.equals("top_level_notifications")){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_middle);
            } else if ("top_level_google".equals(key)){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_bottom);
            } else if (key.equals("top_level_accounts") && gAppsExists){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_middle);
            } else if (key.equals("top_level_basecamp")){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_single);
            } else if (key.equals("top_level_about_device")){
                if (mAboutPhoneStyle == 0) {
                    preference.setLayoutResource(R.layout.top_about_blur);
                } else if (mAboutPhoneStyle == 1) {
                    preference.setLayoutResource(R.layout.top_about_scrim);
                } else if (mAboutPhoneStyle == 2) {
                    preference.setLayoutResource(R.layout.top_about_accent);
                } else if (mAboutPhoneStyle == 3) {
                    preference.setLayoutResource(R.layout.custom_dashboard_top);
                } else if (mAboutPhoneStyle == 4) {
                    preference.setLayoutResource(R.layout.top_about_lottie1);
                } else if (mAboutPhoneStyle == 5) {
                    preference.setLayoutResource(R.layout.top_about_lottie2);
                } else if (mAboutPhoneStyle == 6) {
                    preference.setLayoutResource(R.layout.top_about_lottie3);
                }
             } else {
                preference.setLayoutResource(R.layout.everest_dashboard_preference_bottom);
            }
            break;
	case 1:
            if (key.equals("top_level_network")
            	|| key.equals("top_level_display")
            	|| key.equals("top_level_notifications")
            	|| key.equals("top_level_accessibility")
            	|| key.equals("top_level_emergency")
                || key.equals("top_level_system")){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_top);
            } else if (key.equals("top_level_wallpaper")
                || key.equals("top_level_battery")
                || key.equals("top_level_apps")
            	|| key.equals("top_level_security")
            	|| key.equals("top_level_privacy")
            	|| key.equals("top_level_safety_center")
            	|| key.equals("top_level_wellbeing")){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_middle);
            } else if ("top_level_google".equals(key)){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_bottom);
            } else if (key.equals("top_level_accounts") && gAppsExists){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_middle);
            } else if (key.equals("top_level_basecamp")){
                preference.setLayoutResource(R.layout.everest_dashboard_preference_single);
            } else {
                preference.setLayoutResource(R.layout.everest_dashboard_preference_bottom);
            }
            break;
        case 2:
            preference.setLayoutResource(R.layout.single_dashboard_preference);
            break;
	default:
            break;
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        highlightPreferenceIfNeeded();
    }

    @Override
    public void onSplitLayoutChanged(boolean isRegularLayout) {
        iteratePreferences(preference -> {
            if (preference instanceof HomepagePreferenceLayout) {
                ((HomepagePreferenceLayout) preference).getHelper().setIconVisible(isRegularLayout);
            }
        });
    }

    @Override
    public void highlightPreferenceIfNeeded() {
        if (mHighlightMixin != null) {
            mHighlightMixin.highlightPreferenceIfNeeded();
        }
    }

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        RecyclerView recyclerView = super.onCreateRecyclerView(inflater, parent,
                savedInstanceState);
        recyclerView.setPadding(mPaddingHorizontal, 0, mPaddingHorizontal, 0);
        return recyclerView;
    }

    /** Sets the horizontal padding */
    public void setPaddingHorizontal(int padding) {
        mPaddingHorizontal = padding;
        RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            recyclerView.setPadding(padding, 0, padding, 0);
        }
    }

    /** Updates the preference internal paddings */
    public void updatePreferencePadding(boolean isTwoPane) {
        iteratePreferences(new PreferenceJob() {
            private int mIconPaddingStart;
            private int mTextPaddingStart;

            @Override
            public void init() {
                mIconPaddingStart = getResources().getDimensionPixelSize(isTwoPane
                        ? R.dimen.homepage_preference_icon_padding_start_two_pane
                        : R.dimen.homepage_preference_icon_padding_start);
                mTextPaddingStart = getResources().getDimensionPixelSize(isTwoPane
                        ? R.dimen.homepage_preference_text_padding_start_two_pane
                        : R.dimen.homepage_preference_text_padding_start);
            }

            @Override
            public void doForEach(Preference preference) {
                if (preference instanceof HomepagePreferenceLayout) {
                    ((HomepagePreferenceLayout) preference).getHelper()
                            .setIconPaddingStart(mIconPaddingStart);
                    ((HomepagePreferenceLayout) preference).getHelper()
                            .setTextPaddingStart(mTextPaddingStart);
                }
            }
        });
    }

    /** Returns a {@link TopLevelHighlightMixin} that performs highlighting */
    public TopLevelHighlightMixin getHighlightMixin() {
        return mHighlightMixin;
    }

    /** Highlight a preference with specified preference key */
    public void setHighlightPreferenceKey(String prefKey) {
        // Skip Tips & support since it's full screen
        if (mHighlightMixin != null && !TextUtils.equals(prefKey, PREF_KEY_SUPPORT)) {
            mHighlightMixin.setHighlightPreferenceKey(prefKey);
        }
    }

    /** Returns whether clicking the specified preference is considered as a duplicate click. */
    public boolean isDuplicateClick(Preference pref) {
        /* Return true if
         * 1. the device supports activity embedding, and
         * 2. the target preference is highlighted, and
         * 3. the current activity is embedded */
        return mHighlightMixin != null
                && TextUtils.equals(pref.getKey(), mHighlightMixin.getHighlightPreferenceKey())
                && isActivityEmbedded();
    }

    /** Show/hide the highlight on the menu entry for the search page presence */
    public void setMenuHighlightShowed(boolean show) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setMenuHighlightShowed(show);
        }
    }

    /** Highlight and scroll to a preference with specified menu key */
    public void setHighlightMenuKey(String menuKey, boolean scrollNeeded) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setHighlightMenuKey(menuKey, scrollNeeded);
        }
    }

    @Override
    protected boolean shouldForceRoundedIcon() {
        return getContext().getResources()
                .getBoolean(R.bool.config_force_rounded_icon_TopLevelSettings);
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        if (!mIsEmbeddingActivityEnabled || !(getActivity() instanceof SettingsHomepageActivity)) {
            return super.onCreateAdapter(preferenceScreen);
        }
        return mHighlightMixin.onCreateAdapter(this, preferenceScreen, mScrollNeeded);
    }

    @Override
    protected Preference createPreference(Tile tile) {
        return new HomepagePreference(getPrefContext());
    }


    void reloadHighlightMenuKey() {
        if (mHighlightMixin != null) {
            mHighlightMixin.reloadHighlightMenuKey(getArguments());
        }
    }

    private void iteratePreferences(PreferenceJob job) {
        if (job == null || getPreferenceManager() == null) {
            return;
        }
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        job.init();
        int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference preference = screen.getPreference(i);
            if (preference == null) {
                break;
            }
            job.doForEach(preference);
        }
    }

    private interface PreferenceJob {
        default void init() {
        }

        void doForEach(Preference preference);
    }

    private void updateLabSummary() {
        Preference basecamp = findPreference(KEY_BASECAMP);
        if (basecamp != null) {
            String[] summaries = getContext().getResources().getStringArray(
                    R.array.basecamp_summaries);
            Random rnd = new Random();
            int summNO = rnd.nextInt(summaries.length);
            basecamp.setSummary(summaries[summNO]);
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.top_level_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    // Never searchable, all entries in this page are already indexed elsewhere.
                    return false;
                }
            };
            private void getAboutPhoneStyle(Context context) {
        mAboutPhoneStyle = Settings.System.getIntForUser(context.getContentResolver(),
                    "header_style", 0, UserHandle.USER_CURRENT);
    }
    
            private void setDashboardStyle(Context context) {
        mDashBoardStyle = Settings.System.getIntForUser(context.getContentResolver(),
                    "settings_dashboard_style", 0, UserHandle.USER_CURRENT);
    }
}
