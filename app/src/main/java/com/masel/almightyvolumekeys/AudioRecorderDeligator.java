package com.masel.almightyvolumekeys;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.masel.rec_utils.AudioRecorder;
import com.masel.rec_utils.TheSoundRecorderSharedPrefs;
import com.masel.rec_utils.Utils;

/**
 * This class controls the local recorder AND TheSoundRecorder's recorder (whichever is running).
 * Rec-props defined in TheSoundRecorder's shared-prefs.
 * Uses the local recorder for recordings started by AlmightyVolumeKeys.
 *
 * Also maintains the AlmightyVolumeKeys-is-recording-flag of TheSoundRecorder's shared-prefs
 * (to reflect the local recorder's state). For TheSoundRecorder to read to know what's going on.
 * This flag is initialized to false on TheSoundRecorder-start if it doesn't exist, so
 * it will always be available to read by TheSoundRecorder.
 */
class AudioRecorderDeligator {

    private Context context;

    /**
     * NULL unless currently recording locally. */
    @Nullable
    private AudioRecorder localRecorder = null;

    @NonNull
    private TheSoundRecorderConnection theSoundRecorder;

    AudioRecorderDeligator(Context context) {
        this.context = context;

        Runnable onStopAndSaveButtonClick = this::stopAndSave;
        Runnable onStopAndDiscardButtonClick = this::stopAndDiscard;
        theSoundRecorder = new TheSoundRecorderConnection(context, onStopAndSaveButtonClick, onStopAndDiscardButtonClick);
    }

    /**
     * @return True if local rec or(/and) TheSoundRecorder.
     */
    boolean isRecording()  {
        return (localRecorder != null && localRecorder.isRecording()) || theSoundRecorder.isRecording();
    }

    /**
     * Stop local rec or(/and) TheSoundRecorder.
     *
     * If local rec stopped:
     * Flag in TheSoundRecorder's shared-prefs: local rec stopped.
     * Send broadcast: local rec stopped.
     */
    void stopAndSave() {
        if (localRecorder != null) {

            try {
                Context theSoundRecorderContext = TheSoundRecorderConnection.getTheSoundRecorderContext(context);
                localRecorder.coldStopAndSave(theSoundRecorderContext);
                localRecorder = null;
                TheSoundRecorderSharedPrefs.setAlmightyVolumeKeysIsRecording(theSoundRecorderContext, false);
            }
            catch (TheSoundRecorderConnection.TheSoundRecorderNotInstalledException e) {}
            TheSoundRecorderConnection.broadcastLocalRecStop(context);
        }

        theSoundRecorder.stopAndSave();
        AudioRecorder.removeNotification(context);
    }


    /**
     * Stop and discard local rec or(/and) TheSoundRecorder.
     *
     * If local rec stopped:
     * Flag in TheSoundRecorder's shared-prefs: local rec stopped.
     * Send broadcast: local rec stopped.
     */
    void stopAndDiscard() {
        if (localRecorder != null) {
            localRecorder.stopAndDiscard();
            localRecorder = null;

            try {
                Context theSoundRecorderContext = TheSoundRecorderConnection.getTheSoundRecorderContext(context);
                TheSoundRecorderSharedPrefs.setAlmightyVolumeKeysIsRecording(theSoundRecorderContext, false);
            }
            catch (TheSoundRecorderConnection.TheSoundRecorderNotInstalledException e) {}
            TheSoundRecorderConnection.broadcastLocalRecStop(context);
        }

        theSoundRecorder.stopAndDiscard();
        AudioRecorder.removeNotification(context);
    }

    /**
     * Start local recording (not TheSoundRecorder).
     * If TheSoundRecorder not installed, open play-store (needs rec-props).
     * If already in rec, does nothing.
     *
     * Flag in TheSoundRecorder's shared-prefs: local rec started.
     * Send broadcast: local rec started.
     */
    void start() throws Action.ExecutionException {
        if (isRecording()) return;

        try {
            Context theSoundRecorderContext = TheSoundRecorderConnection.getTheSoundRecorderContext(context);
            localRecorder = AudioRecorder.coldStart(theSoundRecorderContext);
            if (localRecorder != null) {
                AudioRecorder.showNotification(context);
                TheSoundRecorderSharedPrefs.setAlmightyVolumeKeysIsRecording(theSoundRecorderContext, true);
                TheSoundRecorderConnection.broadcastLocalRecStart(context);
            }
            else {
                throw new Action.ExecutionException("Failed to start rec");
            }
        }
        catch (TheSoundRecorderConnection.TheSoundRecorderNotInstalledException e) {
            // todo: install TheSoundRecorder
            throw new Action.ExecutionException("TheSoundRecorder not installed");
        }
    }

    void destroy() {
        if (localRecorder != null) localRecorder.stopAndSave();
        theSoundRecorder.disconnect();
    }

}
