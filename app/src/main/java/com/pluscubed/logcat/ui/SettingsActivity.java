package com.pluscubed.logcat.ui;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Toast;

import com.pluscubed.logcat.data.LogLine;
import com.pluscubed.logcat.helper.PreferenceHelper;
import com.pluscubed.logcat.util.ArrayUtil;
import com.pluscubed.logcat.util.StringUtil;

import org.omnirom.logcat.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.TwoStatePreference;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityManager.TaskDescription td = new ActivityManager.TaskDescription.Builder()
                    .setPrimaryColor(getAttrColor(android.R.attr.colorBackground)).build();
            setTaskDescription(td);
        }

        setContentView(R.layout.activity_settings);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(R.string.settings);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(R.id.content, new SettingsFragment()).commit();
    }

    private void setResultAndFinish() {
        Intent data = new Intent();
        SettingsFragment f = (SettingsFragment) getSupportFragmentManager().findFragmentById(R.id.content);
        data.putExtra("bufferChanged", f.getBufferChanged());
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void onBackPressed() {
        setResultAndFinish();
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                setResultAndFinish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private int getAttrColor(int attr) {
        TypedArray ta = obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }
    
    public static class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

        private static final int MAX_LOG_LINE_PERIOD = 1000;
        private static final int MIN_LOG_LINE_PERIOD = 1;
        private static final int MAX_DISPLAY_LIMIT = 100000;
        private static final int MIN_DISPLAY_LIMIT = 1000;

        private EditTextPreference logLinePeriodPreference, displayLimitPreference;
        private ListPreference textSizePreference, defaultLevelPreference;
        private MultiSelectListPreference bufferPreference;
        private TwoStatePreference scrubberPreference;

        private boolean bufferChanged = false;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.settings);

            setUpPreferences();
        }

        public boolean getBufferChanged() {
            return bufferChanged;
        }

        private void setUpPreferences() {

            displayLimitPreference = (EditTextPreference) findPreference(getString(R.string.pref_display_limit));

            int displayLimitValue = PreferenceHelper.getDisplayLimitPreference(getActivity());

            displayLimitPreference.setSummary(getString(R.string.pref_display_limit_summary,
                    displayLimitValue, getString(R.string.pref_display_limit_default)));

            displayLimitPreference.setOnPreferenceChangeListener(this);

            logLinePeriodPreference = (EditTextPreference) findPreference(getString(R.string.pref_log_line_period));

            int logLinePrefValue = PreferenceHelper.getLogLinePeriodPreference(getActivity());

            logLinePeriodPreference.setSummary(getString(R.string.pref_log_line_period_summary,
                    logLinePrefValue, getString(R.string.pref_log_line_period_default)));

            logLinePeriodPreference.setOnPreferenceChangeListener(this);

            textSizePreference = (ListPreference) findPreference(getString(R.string.pref_text_size));
            textSizePreference.setSummary(textSizePreference.getEntry());
            textSizePreference.setOnPreferenceChangeListener(this);

            defaultLevelPreference = (ListPreference) findPreference(getString(R.string.pref_default_log_level));
            defaultLevelPreference.setOnPreferenceChangeListener(this);
            setDefaultLevelPreferenceSummary(defaultLevelPreference.getEntry());

            bufferPreference = (MultiSelectListPreference) findPreference(getString(R.string.pref_buffer));
            bufferPreference.setOnPreferenceChangeListener(this);
            setBufferPreferenceSummary(TextUtils.join(PreferenceHelper.DELIMITER, bufferPreference.getValues()));

            scrubberPreference = (TwoStatePreference) getPreferenceScreen().findPreference("scrubber");
            scrubberPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    LogLine.isScrubberEnabled = (boolean) newValue;
                    return true;
                }
            });
        }

        private void setDefaultLevelPreferenceSummary(CharSequence entry) {
            defaultLevelPreference.setSummary(
                    getString(R.string.pref_default_log_level_summary, entry));

        }

        public boolean onPreferenceChange(Preference preference, Object newValue) {

            if (preference.getKey().equals(getString(R.string.pref_display_limit))) {

                // display buffer preference; update summary

                String input = ((String) newValue).trim();

                try {

                    int value = Integer.parseInt(input);
                    if (value >= MIN_DISPLAY_LIMIT && value <= MAX_DISPLAY_LIMIT) {
                        PreferenceHelper.setDisplayLimitPreference(getActivity(), value);
                        displayLimitPreference.setSummary(getString(R.string.pref_display_limit_summary,
                                value, getString(R.string.pref_display_limit_default)));

                        // notify that a restart is required
                        Toast.makeText(getActivity(), R.string.toast_pref_changed_restart_required, Toast.LENGTH_LONG).show();

                        return true;
                    }

                } catch (NumberFormatException ignore) {
                }


                String invalidEntry = getString(R.string.toast_invalid_display_limit, MIN_DISPLAY_LIMIT, MAX_DISPLAY_LIMIT);
                Toast.makeText(getActivity(), invalidEntry, Toast.LENGTH_LONG).show();
                return false;

            } else if (preference.getKey().equals(getString(R.string.pref_log_line_period))) {

                // log line period preference; update summary

                String input = ((String) newValue).trim();

                try {

                    int value = Integer.parseInt(input);
                    if (value >= MIN_LOG_LINE_PERIOD && value <= MAX_LOG_LINE_PERIOD) {
                        PreferenceHelper.setLogLinePeriodPreference(getActivity(), value);
                        logLinePeriodPreference.setSummary(getString(R.string.pref_log_line_period_summary,
                                value, getString(R.string.pref_log_line_period_default)));
                        return true;
                    }

                } catch (NumberFormatException ignore) {
                }


                Toast.makeText(getActivity(), R.string.pref_log_line_period_error, Toast.LENGTH_LONG).show();
                return false;
            } else if (preference.getKey().equals(getString(R.string.pref_buffer))) {
                // buffers pref

                // check to make sure nothing was left unchecked
                if (TextUtils.isEmpty(newValue.toString())) {
                    Toast.makeText(getActivity(), R.string.pref_buffer_none_checked_error, Toast.LENGTH_SHORT).show();
                    return false;
                }

                // notify the LogcatActivity that the buffer has changed
                if (!newValue.toString().equals(bufferPreference.getValues().toString())) {
                    bufferChanged = true;
                }

                Set<String> newValueSet = (Set<String>) newValue;
                setBufferPreferenceSummary(TextUtils.join(PreferenceHelper.DELIMITER, newValueSet));
                return true;
            } else if (preference.getKey().equals(getString(R.string.pref_default_log_level))) {
                // default log level preference

                // update the summary to reflect changes

                ListPreference listPreference = (ListPreference) preference;

                int index = ArrayUtil.indexOf(listPreference.getEntryValues(), newValue);
                CharSequence newEntry = listPreference.getEntries()[index];
                setDefaultLevelPreferenceSummary(newEntry);

                return true;

            } else { // text size pref

                // update the summary to reflect changes

                ListPreference listPreference = (ListPreference) preference;

                int index = ArrayUtil.indexOf(listPreference.getEntryValues(), newValue);
                CharSequence newEntry = listPreference.getEntries()[index];
                listPreference.setSummary(newEntry);

                return true;
            }

        }


        private void setBufferPreferenceSummary(String value) {

            String[] commaSeparated = StringUtil.split(StringUtil.nullToEmpty(value), PreferenceHelper.DELIMITER);

            List<CharSequence> checkedEntries = new ArrayList<CharSequence>();

            for (String entryValue : commaSeparated) {
                int idx = ArrayUtil.indexOf(bufferPreference.getEntryValues(), entryValue);
                checkedEntries.add(bufferPreference.getEntries()[idx]);
            }

            String summary = TextUtils.join(PreferenceHelper.DELIMITER, checkedEntries);

            // add the word "simultaneous" to make it clearer what's going on with 2+ buffers
            if (checkedEntries.size() > 1) {
                summary += getString(R.string.simultaneous);
            }
            bufferPreference.setSummary(summary);
        }
    }
}
