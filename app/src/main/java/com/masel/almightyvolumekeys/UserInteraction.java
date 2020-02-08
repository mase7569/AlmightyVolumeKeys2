package com.masel.almightyvolumekeys;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.net.rtp.AudioStream;
import android.os.Build;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.media.VolumeProviderCompat;

import com.masel.rec_utils.Utils;

import java.util.Calendar;

/**
 * Enables user interaction through volume key presses.
 */
class UserInteraction {
    private MyContext myContext;
    private ActionCommand actionCommand;
    private Runnable onAccessibilityServiceFail;

    /**
     * Audio stream to be changed by a long press. Updated at start of each volume longpress. */
    private int longPressAudioStream = AudioManager.STREAM_MUSIC;

    private UserInteractionWhenScreenOffAndMusic userInteractionWhenScreenOffAndMusic;

    UserInteraction(Context context, Runnable onAccessibilityServiceFail) {
        this.myContext = new MyContext(context);
        this.onAccessibilityServiceFail = onAccessibilityServiceFail;

        actionCommand = new ActionCommand(myContext);
        setupMediaSessionForScreenOffCallbacks();
        userInteractionWhenScreenOffAndMusic = new UserInteractionWhenScreenOffAndMusic(myContext, actionCommand);
        actionCommand.setManualMusicVolumeChanger(userInteractionWhenScreenOffAndMusic.getManualMusicVolumeChanger());

        setupWakeLockWhenScreenOff();
    }

    void destroy() {
        longPressHandler.removeCallbacksAndMessages(null);
        userInteractionWhenScreenOffAndMusic.destroy(myContext);
        myContext.destroy();
    }

    void onVolumeKeyEvent(KeyEvent event) {
        boolean volumeUp = event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP;

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            longPressHandler.removeCallbacksAndMessages(null);
            longPressHandler.postDelayed(() -> {
                longPressAudioStream = getLongPressAudioStream();
                longPress(volumeUp);
            }, LONG_PRESS_TIME);
        }
        else if (event.getAction() == KeyEvent.ACTION_UP) {
            if (!currentlyVolumeLongPress) {
                handleVolumeKeyPress(volumeUp);
            }
            longPressHandler.removeCallbacksAndMessages(null);
            currentlyVolumeLongPress = false;
        }
        else if (event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            Utils.log("MULTIPLE KEY PRESS");
        }
        else throw new RuntimeException("Dead end");
    }

    // region volume long-press

    private static final long LONG_PRESS_TIME = 400;
    private static final long LONG_PRESS_VOLUME_CHANGE_TIME = 40;
    private Handler longPressHandler = new Handler();
    private boolean currentlyVolumeLongPress = false;

    private void longPress(boolean volumeUp) {
        currentlyVolumeLongPress = true;
        longPressHandler.removeCallbacksAndMessages(null);
        int currentRelevantStreamVolumePercentage = Utils.getStreamVolumePercentage(myContext.audioManager, getRelevantAudioStream());
        if ((currentRelevantStreamVolumePercentage == 100 && volumeUp) ||
                (currentRelevantStreamVolumePercentage == 0 && !volumeUp)) {
            adjustRelevantStreamVolume(volumeUp);
            return;
        }

        adjustRelevantStreamVolume(volumeUp);
        actionCommand.reset();
        longPressHandler.postDelayed(() -> longPress(volumeUp), LONG_PRESS_VOLUME_CHANGE_TIME);
    }

    // endregion

    /**
     * Adds press to action command if appropriate. Else changes volume as normal.
     * Default volume change if more than 4 volume presses.
     * @param up True if volume up pressed, false if down.
     */
    private void handleVolumeKeyPress(boolean up) {
        DeviceState state = DeviceState.getCurrent(myContext);

        if (state.equals(DeviceState.IDLE) || state.equals(DeviceState.SOUNDREC)) {
            if (actionCommand.getLength() >= 4) {
                adjustRelevantStreamVolume(up);
            }
            actionCommand.addBit(up);
        }
        else if (state.equals(DeviceState.MUSIC)) {
            adjustRelevantStreamVolume(up);
            actionCommand.addBit(up);
        }
        else {
            adjustRelevantStreamVolume(up);
        }
    }

    /**
     * Finds relevant stream and changes its volume.
     * @param up else down
     *
     * todo: investigate AudioManager.adjustVolume(), USE_DEFAULT_STREAM_TYPE -constant
     * todo: when alarm..
     */
    private void adjustRelevantStreamVolume(boolean up) {
        int dir = up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
        int volumeChangeFlag = AudioManager.FLAG_SHOW_UI;
        int relevantStream = getRelevantAudioStream();

        try {
            myContext.audioManager.adjustStreamVolume(relevantStream, dir, volumeChangeFlag);
        }
        catch (SecurityException e) {
            myContext.audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, volumeChangeFlag);
        }
    }

    /**
     * @return Audio stream to be adjusted on a volume changing key-event.
     */
    private int getRelevantAudioStream() {
        int activeStream = Utils.getActiveAudioStream(myContext.audioManager);
        if (activeStream != AudioManager.USE_DEFAULT_STREAM_TYPE) {
            return activeStream;
        }
        else {
            if (currentlyVolumeLongPress) return longPressAudioStream;
            else return getFiveClicksAudioStream();
        }
    }

    private int getLongPressAudioStream() {
        String value = actionCommand.getLength() >= 5 ?
                myContext.sharedPreferences.getString("ListPreference_FiveVolumeClicksChanges", null) :
                myContext.sharedPreferences.getString("ListPreference_LongVolumePressChanges", null);

        if (value == null || value.equals("Ringtone volume")) return AudioManager.STREAM_RING;
        else return AudioManager.STREAM_MUSIC;
    }

    private int getFiveClicksAudioStream() {
        String value = myContext.sharedPreferences.getString("ListPreference_FiveVolumeClicksChanges", null);
        if (value == null || value.equals("Ringtone volume")) return AudioManager.STREAM_RING;
        else return AudioManager.STREAM_MUSIC;
    }

    private void setupMediaSessionForScreenOffCallbacks() {
        MediaSessionCompat mediaSession = myContext.mediaSession;
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1);
        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setPlaybackToRemote(screenOffCallback());
        mediaSession.setActive(true);
    }

    /**
     * Fallback when onKeyEvent doesn't catch the event (happens when screen is off).
     * Music active, in call, phone ringing etc "should" take control over the volume-keys and
     * therefor suppress this callback. To be safe, check if to add press to action-command OR
     * change volume of appropriate stream.
     */
    private VolumeProviderCompat screenOffCallback() {
        return new VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_RELATIVE, 2, 1) {
            @Override
            public void onAdjustVolume(int direction) {
                if (Utils.isScreenOn(myContext.powerManager)) {
                    onAccessibilityServiceFail.run();
                    return;
                }

                if (direction != 0) {
                    boolean up = direction > 0;
                    handleVolumeKeyPress(up);
                }
            }
        };
    }

    /**
     * Keep cpu (and volume keys) on after screen off, for minimum time defined in user-settings.
     */
    private void setupWakeLockWhenScreenOff() {
        myContext.context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int preventSleepMinutes = myContext.sharedPreferences.getInt("SeekBarPreference_preventSleepTimeout", 60);
                boolean allowSleepSwitch = myContext.sharedPreferences.getBoolean("SwitchPreferenceCompat_allowSleep", false);
                int allowSleepStartHour = myContext.sharedPreferences.getInt("SeekBarPreference_allowSleepStart", 0);
                int allowSleepEndHour = myContext.sharedPreferences.getInt("SeekBarPreference_allowSleepEnd", 0);

                long timeout = preventSleepMinutes * 60000;
                boolean allowSleep = allowSleepSwitch && currentlyAllowSleep(allowSleepStartHour, allowSleepEndHour);

                if (!allowSleep) {
                    myContext.wakeLock.acquire(timeout);
                    Utils.log("Wake lock acquired for (minutes): " + preventSleepMinutes);
                }
                else {
                    Utils.log("Wake lock not acquired (prevention bypassed by user settings)");
                }
            }
        }, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    private boolean currentlyAllowSleep(int allowStartHour, int allowStopHour) {
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hourInterval(allowStartHour, allowStopHour) > hourInterval(allowStartHour, currentHour);
    }

    private int hourInterval(int from, int to) {
        return from <= to ? to - from : (to + 24) - from;
    }
}


