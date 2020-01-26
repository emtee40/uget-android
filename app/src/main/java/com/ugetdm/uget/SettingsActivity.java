/*
 *
 *   Copyright (C) 2018-2020 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;

import java.util.List;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {
    // Application data
    private static MainApp app = null;
    public  static boolean aria2Changed = false;
    public  static boolean sortChanged = false;

    public static void reset() {
        aria2Changed = false;
        sortChanged = false;
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        //app = (MainApp) getApplicationContext();    // throw RuntimeException
        app = (MainApp) getApplication();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // --- setResult() can not work with PreferenceActivity
        // --- MainActivity.onResume() will check & handle app.settingsResult
        Intent resultData = app.settingsResult;
        if (resultData == null) {
            resultData = new Intent();
            app.settingsResult = resultData;
        }
        resultData.putExtra("aria2Changed", aria2Changed);
        resultData.putExtra("sortChanged", sortChanged);
        // setResult(RESULT_OK, resultData);
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // --- for Android <= 5.0 (API 21) --- click home button in ActionBar
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return UiSettingFragment.class.getName().equals(fragmentName)
                || ClipboardSettingFragment.class.getName().equals(fragmentName)
                || SpeedSettingFragment.class.getName().equals(fragmentName)
                || PluginSettingFragment.class.getName().equals(fragmentName)
                || MediaSettingFragment.class.getName().equals(fragmentName)
                || OtherSettingFragment.class.getName().equals(fragmentName)
                || AboutFragment.class.getName().equals(fragmentName);
    }

    // ------------------------------------------------------------------------

    // header Fragment
    //
    public static class UiSettingFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_ui);
        }
    }

    public static class ClipboardSettingFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_clipboard);
        }
    }

    public static class SpeedSettingFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_speed);
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onResume() {
            super.onResume();
            updatePreferenceSummary();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updatePreferenceSummary();
        }

        private void updatePreferenceSummary() {
            String value;
            Preference preference;
            SharedPreferences sharedPrefs = getPreferenceManager().getSharedPreferences();

            preference = findPreference("pref_speed_download");
            value = sharedPrefs.getString("pref_speed_download", "0");
            if (value.equals(""))
                value = "0";
            preference.setSummary(value + " KiB/s");

            preference = findPreference("pref_speed_upload");
            value = sharedPrefs.getString("pref_speed_upload", "0");
            if (value.equals(""))
                value = "0";
            preference.setSummary(value + " KiB/s");

            // if (preference instanceof ListPreference) {
            //     ListPreference listPreference = (ListPreference) preference;
            //     listPreference.setSummary(listPreference.getEntry());
            //     return;
            // }
        }
    }

    public static class PluginSettingFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_plugin);
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

            Preference.OnPreferenceChangeListener changeListener;
            changeListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    SettingsActivity.aria2Changed = true;
                    // --- return true, write new value to Preference
                    return true;
                }
            };

            Preference preference;
            // pref_plugin
            preference = findPreference("pref_plugin_order");
            preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    if (preference instanceof ListPreference) {
                        decideAria2OptionEnable(value);
                    }
                    // --- return true, write new value to Preference
                    return true;
                }
            });
            // pref_aria2_xxx
            preference = findPreference("pref_aria2_uri");
            preference.setOnPreferenceChangeListener(changeListener);
            preference = findPreference("pref_aria2_token");
            preference.setOnPreferenceChangeListener(changeListener);
            preference = findPreference("pref_aria2_speed_download");
            preference.setOnPreferenceChangeListener(changeListener);
            preference = findPreference("pref_aria2_speed_upload");
            preference.setOnPreferenceChangeListener(changeListener);
            preference = findPreference("pref_aria2_local");
            preference.setOnPreferenceChangeListener(changeListener);
            preference = findPreference("pref_aria2_launch");
            preference.setOnPreferenceChangeListener(changeListener);
            preference = findPreference("pref_aria2_shutdown");
            preference.setOnPreferenceChangeListener(changeListener);
            preference = findPreference("pref_aria2_path");
            preference.setOnPreferenceChangeListener(changeListener);
            preference = findPreference("pref_aria2_args");
            preference.setOnPreferenceChangeListener(changeListener);

            // --- initialize status ---
            decideAria2OptionEnable(null);
        }

        @Override
        public void onResume() {
            super.onResume();
            updatePreferenceSummary();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updatePreferenceSummary();
        }

        private void decideAria2OptionEnable(Object value) {
            boolean enableAria2Option;
            int index;

            if (value == null) {
                SharedPreferences sharedPrefs = getPreferenceManager().getSharedPreferences();
                index = Integer.parseInt(sharedPrefs.getString("pref_plugin_order", "0"));
            }
            else {
                Preference preference = findPreference("pref_plugin_order");
                ListPreference listPreference = (ListPreference) preference;
                index = listPreference.findIndexOfValue(value.toString());
           }

            if (index > 0)
                enableAria2Option = true;
            else
                enableAria2Option = false;

            PreferenceCategory preferenceCategory = (PreferenceCategory)findPreference("pref_aria2");
            preferenceCategory.setEnabled(enableAria2Option);
        }

        private void updatePreferenceSummary() {
            String value;
            Preference preference;
            SharedPreferences sharedPrefs = getPreferenceManager().getSharedPreferences();

            preference = findPreference("pref_aria2_uri");
            preference.setSummary(sharedPrefs.getString("pref_aria2_uri", "http://localhost:6800/jsonrpc"));

            preference = findPreference("pref_aria2_speed_download");
            value = sharedPrefs.getString("pref_aria2_speed_download", "0");
            if (value.equals(""))
                value = "0";
            preference.setSummary(value + " KiB/s");

            preference = findPreference("pref_aria2_speed_upload");
            value = sharedPrefs.getString("pref_aria2_speed_upload", "0");
            if (value.equals(""))
                value = "0";
            preference.setSummary(value + " KiB/s");

            // if (preference instanceof ListPreference) {
            //     ListPreference listPreference = (ListPreference) preference;
            //     listPreference.setSummary(listPreference.getEntry());
            //     return;
            // }
        }
    }

    public static class MediaSettingFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_media);
        }
    }

    public static class OtherSettingFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_other);

            Preference.OnPreferenceClickListener clickListener;
            clickListener = new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // TODO Auto-generated method stub
                    SettingsActivity.sortChanged = true;
                    return false;
                }
            };

            Preference preference;
            preference = findPreference("pref_sort");
            preference.setOnPreferenceClickListener(clickListener);
        }
    }

    public static class AboutFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_about);
        }

        @Override
        public void onResume() {
            super.onResume();
            updatePreferenceSummary();
        }

        private void updatePreferenceSummary() {
            Preference preference;
            String     summary;
            String     myVerName;
            int        myVerCode;

            try {
                Activity activity = getActivity();
                PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                myVerCode = packageInfo.versionCode;
                myVerName = packageInfo.versionName;
            } catch (Exception e) {
                myVerName = "?";
                myVerCode = -1;
                e.printStackTrace();
            }

            preference = findPreference("pref_about_version");
            summary = getString(R.string.app_name) + " " + myVerName + " for Android" + "\n" +
                      getString(R.string.app_flavor_name) + "\n" +
                      "Code " + myVerCode;
            preference.setSummary(summary);
        }
    }

}
