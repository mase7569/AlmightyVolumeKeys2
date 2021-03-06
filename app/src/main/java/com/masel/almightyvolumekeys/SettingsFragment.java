package com.masel.almightyvolumekeys;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import com.masel.rec_utils.RecUtils;

public class SettingsFragment extends PreferenceFragmentCompat {

    //private ListPreference volumeKeysChange;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings, rootKey);

//        volumeKeysChange = findPreference("ListPreference_VolumeKeysChange");
//        requestDoNotDisturbPermissionIfRingtoneSet(volumeKeysChange);

        SeekBarPreference disableAppTimeout = findPreference("SeekBarPreference_disableAppTimeout");
        disableAppTimeout.setMin(0);
        disableAppTimeout.setMax(180);
        disableAppTimeout.setShowSeekBarValue(true);

//        SeekBarPreference allowSleepStart = findPreference("SeekBarPreference_allowSleepStart");
//        SeekBarPreference allowSleepEnd = findPreference("SeekBarPreference_allowSleepEnd");
//        designForTimeInput(allowSleepStart);
//        designForTimeInput(allowSleepEnd);

        Preference gotoSoundRec = findPreference("Preference_gotoSoundRec");
        gotoSoundRec.setOnPreferenceClickListener(preference -> {
            if (!RecUtils.gotoApp(getContext(), "com.masel.thesoundrecorder2")) {
                RecUtils.openAppOnPlayStore(getContext(), "com.masel.thesoundrecorder2");
            }
            return true;
        });

        Preference gotoTtsSettings = findPreference("Preference_gotoTtsSettings");
        gotoTtsSettings.setOnPreferenceClickListener(preference -> {
            Utils.gotoTtsSettings(getContext());
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();

//        if (!RecUtils.hasPermissionToSilenceDevice(getContext())) {
//            volumeKeysChange.setValue("Media volume");
//        }
    }

    private void requestDoNotDisturbPermissionIfRingtoneSet(ListPreference listPreference) {
        listPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String value = (String)newValue;
            if (value.equals("Ringtone volume")) {
                RecUtils.requestSilenceDevicePermission(getContext());
            }
            return true;
        });
    }

    private void designForTimeInput(SeekBarPreference seekBar) {
        seekBar.setMin(0);
        seekBar.setMax(24);
        seekBar.setShowSeekBarValue(true);
    }
}
