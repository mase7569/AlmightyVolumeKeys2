package com.masel.almightyvolumekeys;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.masel.rec_utils.RecUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

class Utils {
    private Utils(){}

    interface Question {
        boolean ask();
    }

    static int loadVolumeKeysAudioStream(SharedPreferences sharedPreferences) {
        String value = sharedPreferences.getString("ListPreference_VolumeKeysChange", null);
        int backupStream = AudioManager.STREAM_MUSIC;
        if (value == null) return backupStream;

        if (value.equals("Ringtone volume")) return AudioManager.STREAM_RING;
        if (value.equals("Media volume after 5 clicks")) return AudioManager.STREAM_MUSIC;
        if (value.equals("Ringtone volume after 5 clicks")) return AudioManager.STREAM_RING;
        else return AudioManager.STREAM_MUSIC;
    }

    static long loadDisableAppTime(SharedPreferences sharedPreferences) {
        int disableAppMinutes = sharedPreferences.getInt("SeekBarPreference_disableAppTimeout", 30);
        return disableAppMinutes * 60000;
    }

//
//    static boolean loadPreventMaxAndMinVolume(SharedPreferences sharedPreferences) {
//        return sharedPreferences.getBoolean("SwitchPreferenceCompat_preventMaxAndMinVolume", true);
//    }


//    static boolean loadFiveClicksBeforeVolumeChange(MyContext myContext) {
//        String value = myContext.sharedPreferences.getString("ListPreference_VolumeKeysChange", null);
//        if (value == null) return false;
//        return value.equals("Media volume after 5 clicks") ||
//                value.equals("Ringtone volume after 5 clicks");
//    }



//    static boolean loadDefaultVolumeKeyActionWhenCameraActive(MyContext myContext) {
//        return myContext.sharedPreferences.getBoolean("SwitchPreferenceCompat_defaultVolumeKeyActionWhenCameraActive", true);
//    }

//    /**
//     * @return Audio stream to be adjusted on a volume changing key-event.
//     */
//    static int getRelevantAudioStream(MyContext myContext) {
//        int activeStream = RecUtils.getActiveAudioStream(myContext.audioManager);
//        if (activeStream != AudioManager.USE_DEFAULT_STREAM_TYPE) {
//            return activeStream;
//        }
//        else {
//            return Utils.loadVolumeKeysAudioStream(myContext.sharedPreferences);
//        }
//    }



    /**
     * Good for stability of a service.
     */
    static final int FOREGROUND_NOTIFICATION_ID = 6664867;
    static void requestForeground(Service service) {
        final String MONITOR_SERVICE_NOTIFICATION_CHANNEL_ID = "MONITOR_SERVICE_NOTIFICATION_CHANNEL_ID";
        final int NOTIFICATION_ID = 6664867;

        if (Build.VERSION.SDK_INT >= 26) {
            String name = "Monitor service (Hide me!)";
            String description = "This notification is necessary to ensure stability of the monitor service. But you can simply hide it if you like, just switch the toggle.";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(MONITOR_SERVICE_NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = service.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent();
        notificationIntent.setComponent(new ComponentName("com.masel.almightyvolumekeys", "com.masel.almightyvolumekeys.MainActivity"));
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(service, 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(service, MONITOR_SERVICE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.avk_notification_icon)
                .setContentTitle("Capturing volume key presses")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .build();

        service.startForeground(NOTIFICATION_ID, notification);
    }


    /**
     * @return Current mappings specified by user. Key=command, value=action
     */
    static Set<Map.Entry<String, String>> getMappings(SharedPreferences sharedPreferences) {
        Map<String, String> mappings = new HashMap<>();

        for (Map.Entry<String, ?> entry : sharedPreferences.getAll().entrySet()) {
            if (entry.getKey().matches("mappingListPreference_.+_command_.+")) {
                mappings.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return mappings.entrySet();
    }

    static Set<Map.Entry<String, String>> getMappings(Context context) {
        return getMappings(PreferenceManager.getDefaultSharedPreferences(context));
    }

    static void runOnMainThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    // region Debug

    static void logMediaSessions(Context context) {
        MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        List<MediaController> controllers = manager.getActiveSessions(new ComponentName(context, MonitorService.class));

        RecUtils.log(" ");
        RecUtils.log("Media sessions:");
        for (MediaController controller : controllers) {
            String info = String.format("%s:\n%s\n%s", controller.getPackageName(), controller.getPlaybackInfo(), controller.getPlaybackState());
//            String info = String.format("%s ... %s ... %s", controller.getPackageName(), controller.getMetadata(), controller.getExtras());
//            if (controller.getPlaybackState() == null) info = null;
//            else info = controller.getPackageName() + ": " + controller.getPlaybackState().getState();
            RecUtils.log(info);
        }
        RecUtils.log(" ");
    }

    static void validateActions(Context context) {
        List<String> actionNames = new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.idle_actions)));
        actionNames.addAll(new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.music_actions))));
        actionNames.addAll(new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.soundrec_actions))));

        for (String name : actionNames) {
            Action action = Actions.getActionFromName(name);

            if (action == null || (!(action instanceof MultiAction) && !action.getName().equals(name))) {
                throw new RuntimeException("Action inconsistency: " + name);
            }
        }
    }

    static void gotoTtsSettings(Context context) {
        Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        try {
            context.startActivity(intent);
        }
        catch (Exception e) {
            RecUtils.toast(context, "Find Text-to-speech in device settings");
        }
    }

    // endregion
}
