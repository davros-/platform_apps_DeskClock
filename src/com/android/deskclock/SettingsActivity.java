/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.deskclock;

import android.app.ActionBar;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.android.deskclock.worldclock.Cities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import net.margaritov.preference.colorpicker.ColorPickerPreference;

/**
 * Settings for the Alarm Clock.
 */
public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    private static final int ALARM_STREAM_TYPE_BIT =
            1 << AudioManager.STREAM_ALARM;

    static final String KEY_ALARM_IN_SILENT_MODE =
            "alarm_in_silent_mode";
    static final String KEY_SHOW_STATUS_BAR_ICON =
            "show_status_bar_icon";
    static final String KEY_ALARM_SNOOZE =
            "snooze_duration";
	static final String KEY_FLIP_ACTION =
			"flip_action";
	static final String KEY_SHAKE_ACTION =
			"shake_action";

    static final String KEY_VOLUME_BEHAVIOR =
            "volume_button_setting";
    static final String KEY_AUTO_SILENCE =
            "auto_silence";
    public static final String KEY_CLOCK_STYLE =
            "clock_style";
    public static final String KEY_HOME_TZ =
            "home_time_zone";
    public static final String KEY_AUTO_HOME_CLOCK =
            "automatic_home_clock";
    public static final String KEY_KEEP_DISPLAY_ON_STOPWATCH =
            "keep_display_on_stopwatch";
    static final String KEY_VOLUME_BUTTONS =
            "volume_button_setting";
    static final String KEY_DIGITAL_CLOCK_TIME_COLOR =
            "digital_clock_time_color";
    static final String KEY_DIGITAL_CLOCK_DATE_COLOR =
            "digital_clock_date_color";
    static final String KEY_DIGITAL_CLOCK_ALARM_COLOR =
            "digital_clock_alarm_color";

    public static final String DEFAULT_VOLUME_BEHAVIOR = "0";

    private static CharSequence[][] mTimezones;
    private long mTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        }

        // We don't want to reconstruct the timezone list every single time
        // onResume() is called so we do it once in onCreate
        ListPreference listPref;
        listPref = (ListPreference) findPreference(KEY_HOME_TZ);
        if (mTimezones == null) {
            mTime = System.currentTimeMillis();
            mTimezones = getAllTimezones();
        }

        listPref.setEntryValues(mTimezones[0]);
        listPref.setEntries(mTimezones[1]);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public boolean onCreateOptionsMenu (Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        MenuItem help = menu.findItem(R.id.menu_item_help);
        if (help != null) {
            Utils.prepareHelpMenuItem(this, help);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (KEY_ALARM_IN_SILENT_MODE.equals(preference.getKey())) {
            CheckBoxPreference pref = (CheckBoxPreference) preference;
            int ringerModeStreamTypes = Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

            if (pref.isChecked()) {
                ringerModeStreamTypes &= ~ALARM_STREAM_TYPE_BIT;
            } else {
                ringerModeStreamTypes |= ALARM_STREAM_TYPE_BIT;
            }

            Settings.System.putInt(getContentResolver(),
                    Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                    ringerModeStreamTypes);

            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        if (KEY_AUTO_SILENCE.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            String delay = (String) newValue;
            updateAutoSnoozeSummary(listPref, delay);
        } else if (KEY_SHOW_STATUS_BAR_ICON.equals(pref.getKey())) {
            // Check if any alarms are active. If yes and
            // we allow showing the alarm icon, the icon will be shown.
            Alarms.updateStatusBarIcon(getApplicationContext(), (Boolean) newValue);
        } else if (KEY_CLOCK_STYLE.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_HOME_TZ.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
            notifyHomeTimeZoneChanged();
        } else if (KEY_AUTO_HOME_CLOCK.equals(pref.getKey())) {
            boolean state =((CheckBoxPreference) pref).isChecked();
            Preference homeTimeZone = findPreference(KEY_HOME_TZ);
            homeTimeZone.setEnabled(!state);
            notifyHomeTimeZoneChanged();
        } else if (KEY_VOLUME_BUTTONS.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_FLIP_ACTION.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            String action = (String) newValue;
            updateFlipActionSummary(listPref, action);
        } else if (KEY_SHAKE_ACTION.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            String action = (String) newValue;
            updateShakeActionSummary(listPref, action);
        } else if (KEY_DIGITAL_CLOCK_TIME_COLOR.equals(pref.getKey())
                || KEY_DIGITAL_CLOCK_DATE_COLOR.equals(pref.getKey())
                || KEY_DIGITAL_CLOCK_ALARM_COLOR.equals(pref.getKey())) {
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(getApplicationContext());
            int[] widgetIds = widgetManager.getAppWidgetIds(
                    new ComponentName(getApplicationContext(), com.android.alarmclock.DigitalAppWidgetProvider.class));
            Intent update = new Intent(getApplicationContext(), com.android.alarmclock.DigitalAppWidgetProvider.class);
            update.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            update.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
            getApplicationContext().sendBroadcast(update);
        }
        return true;
    }

    private void updateAutoSnoozeSummary(ListPreference listPref,
            String delay) {
        int i = Integer.parseInt(delay);
        if (i == -1) {
            listPref.setSummary(R.string.auto_silence_never);
        } else {
            listPref.setSummary(getString(R.string.auto_silence_summary, i));
        }
    }

    private void updateFlipActionSummary(ListPreference listPref, String action) {
        int i = Integer.parseInt(action);
        listPref.setSummary(getString(R.string.flip_action_summary,
            getResources().getStringArray(R.array.flip_action_entries)[i]
                .toLowerCase()));
    }

    private void updateShakeActionSummary(ListPreference listPref, String action) {
        int i = Integer.parseInt(action);
        listPref.setSummary(getString(R.string.shake_summary, getResources()
            .getStringArray(R.array.flip_action_entries)[i].toLowerCase()));
    }

    private void notifyHomeTimeZoneChanged() {
        Intent i = new Intent(Cities.WORLDCLOCK_UPDATE_INTENT);
        sendBroadcast(i);
    }

    private void refresh() {
        ListPreference listPref = (ListPreference) findPreference(KEY_AUTO_SILENCE);
        String delay = listPref.getValue();
        updateAutoSnoozeSummary(listPref, delay);
        listPref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference) findPreference(KEY_CLOCK_STYLE);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        Preference pref = findPreference(KEY_AUTO_HOME_CLOCK);
        boolean state =((CheckBoxPreference) pref).isChecked();
        pref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference)findPreference(KEY_HOME_TZ);
        listPref.setEnabled(state);
        listPref.setSummary(listPref.getEntry());

        listPref = (ListPreference) findPreference(KEY_VOLUME_BUTTONS);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference) findPreference(KEY_FLIP_ACTION);
        String action = listPref.getValue();
        updateFlipActionSummary(listPref, action);
        listPref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference) findPreference(KEY_SHAKE_ACTION);
        String shake = listPref.getValue();
        updateShakeActionSummary(listPref, shake);
        listPref.setOnPreferenceChangeListener(this);


        ColorPickerPreference clockTimeColor = (ColorPickerPreference) findPreference(KEY_DIGITAL_CLOCK_TIME_COLOR);
        clockTimeColor.setOnPreferenceChangeListener(this);

        ColorPickerPreference clockDateColor = (ColorPickerPreference) findPreference(KEY_DIGITAL_CLOCK_DATE_COLOR);
        clockDateColor.setOnPreferenceChangeListener(this);

        ColorPickerPreference clockAlarmColor = (ColorPickerPreference) findPreference(KEY_DIGITAL_CLOCK_ALARM_COLOR);
        clockAlarmColor.setOnPreferenceChangeListener(this);

        SnoozeLengthDialog snoozePref = (SnoozeLengthDialog) findPreference(KEY_ALARM_SNOOZE);
        snoozePref.setSummary();
        CheckBoxPreference hideStatusbarIcon = (CheckBoxPreference) findPreference(KEY_SHOW_STATUS_BAR_ICON);
        hideStatusbarIcon.setOnPreferenceChangeListener(this);
    }

    private class TimeZoneRow implements Comparable<TimeZoneRow> {
        private static final boolean SHOW_DAYLIGHT_SAVINGS_INDICATOR = false;

        public final String mId;
        public final String mDisplayName;
        public final int mOffset;

        public TimeZoneRow(String id, String name) {
            mId = id;
            TimeZone tz = TimeZone.getTimeZone(id);
            boolean useDaylightTime = tz.useDaylightTime();
            mOffset = tz.getOffset(mTime);
            mDisplayName = buildGmtDisplayName(id, name, useDaylightTime);
        }

        @Override
        public int compareTo(TimeZoneRow another) {
            return mOffset - another.mOffset;
        }

        public String buildGmtDisplayName(String id, String displayName, boolean useDaylightTime) {
            int p = Math.abs(mOffset);
            StringBuilder name = new StringBuilder("(GMT");
            name.append(mOffset < 0 ? '-' : '+');

            name.append(p / DateUtils.HOUR_IN_MILLIS);
            name.append(':');

            int min = p / 60000;
            min %= 60;

            if (min < 10) {
                name.append('0');
            }
            name.append(min);
            name.append(") ");
            name.append(displayName);
            if (useDaylightTime && SHOW_DAYLIGHT_SAVINGS_INDICATOR) {
                name.append(" \u2600"); // Sun symbol
            }
            return name.toString();
        }
    }


    /**
     * Returns an array of ids/time zones. This returns a double indexed array
     * of ids and time zones for Calendar. It is an inefficient method and
     * shouldn't be called often, but can be used for one time generation of
     * this list.
     *
     * @return double array of tz ids and tz names
     */
    public CharSequence[][] getAllTimezones() {
        Resources resources = this.getResources();
        String[] ids = resources.getStringArray(R.array.timezone_values);
        String[] labels = resources.getStringArray(R.array.timezone_labels);
        if (ids.length != labels.length) {
            Log.wtf("Timezone ids and labels have different length!");
        }
        List<TimeZoneRow> timezones = new ArrayList<TimeZoneRow>();
        for (int i = 0; i < ids.length; i++) {
            timezones.add(new TimeZoneRow(ids[i], labels[i]));
        }
        Collections.sort(timezones);

        CharSequence[][] timeZones = new CharSequence[2][timezones.size()];
        int i = 0;
        for (TimeZoneRow row : timezones) {
            timeZones[0][i] = row.mId;
            timeZones[1][i++] = row.mDisplayName;
        }
        return timeZones;
    }

}
