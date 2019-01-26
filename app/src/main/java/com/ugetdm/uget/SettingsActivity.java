package com.ugetdm.uget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
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
    public static MainApp app = null;

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            // String stringValue = value.toString();
            return true;
        }
    };

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();

        app = (MainApp)getApplicationContext();
    }

    @Override
    protected void onPause() {
        app.getSettingFromPreferences();
        app.applySetting();
        super.onPause();
    }

    /**
     * Set up the {@link android.app.ActionBar}, if the API is available.
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);

            actionBar.setSubtitle(R.string.title_activity_settings);
        }
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
                || Aria2SettingFragment.class.getName().equals(fragmentName)
                || MediaSettingFragment.class.getName().equals(fragmentName)
                || OtherSettingFragment.class.getName().equals(fragmentName);
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
            Preference preference;
            SharedPreferences sharedPrefs = getPreferenceManager().getSharedPreferences();

            preference = findPreference("pref_speed_download");
            preference.setSummary(sharedPrefs.getString("pref_speed_download", "0") + " KiB/s");

            preference = findPreference("pref_speed_upload");
            preference.setSummary(sharedPrefs.getString("pref_speed_upload", "0") + " KiB/s");

            // if (preference instanceof ListPreference) {
            //     ListPreference listPreference = (ListPreference) preference;
            //     listPreference.setSummary(listPreference.getEntry());
            //     return;
            // }
        }
    }

    public static class Aria2SettingFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_aria2);
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

            Preference.OnPreferenceClickListener clickListener;
            clickListener = new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // TODO Auto-generated method stub
                    app.preferenceAria2Changed = true;
                    return false;
                }
            };

            Preference preference;
            // pref_aria2_uri
            preference = findPreference("pref_aria2_uri");
            preference.setOnPreferenceClickListener(clickListener);
            preference = findPreference("pref_aria2_token");
            preference.setOnPreferenceClickListener(clickListener);
            preference = findPreference("pref_aria2_speed_download");
            preference.setOnPreferenceClickListener(clickListener);
            preference = findPreference("pref_aria2_speed_upload");
            preference.setOnPreferenceClickListener(clickListener);
            preference = findPreference("pref_aria2_local");
            preference.setOnPreferenceClickListener(clickListener);
            preference = findPreference("pref_aria2_launch");
            preference.setOnPreferenceClickListener(clickListener);
            preference = findPreference("pref_aria2_shutdown");
            preference.setOnPreferenceClickListener(clickListener);
            preference = findPreference("pref_aria2_path");
            preference.setOnPreferenceClickListener(clickListener);
            preference = findPreference("pref_aria2_args");
            preference.setOnPreferenceClickListener(clickListener);
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
            Preference preference;
            SharedPreferences sharedPrefs = getPreferenceManager().getSharedPreferences();

            preference = findPreference("pref_aria2_uri");
            preference.setSummary(sharedPrefs.getString("pref_aria2_uri", "http://localhost:6800/jsonrpc"));

            preference = findPreference("pref_aria2_speed_download");
            preference.setSummary(sharedPrefs.getString("pref_aria2_speed_download", "0") + " KiB/s");
            preference = findPreference("pref_aria2_speed_upload");
            preference.setSummary(sharedPrefs.getString("pref_aria2_speed_upload", "0") + " KiB/s");

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
        }
    }

}
