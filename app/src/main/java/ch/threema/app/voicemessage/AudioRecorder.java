package ch.threema.app.voicemessage;

import android.media.MediaRecorder;
import android.media.MicrophoneDirection;
import android.os.Build;

import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

import androidx.annotation.NonNull;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.app.utils.ConfigUtils;

public class AudioRecorder implements MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener {
    private static final Logger logger = getThreemaLogger("AudioRecorder");

    private OnStopListener onStopListener;

    private static final int defaultSamplingRate = ConfigUtils.hasBrokenAudioRecorder() ? 44000 : 44100;

    @NonNull
    public MediaRecorder prepare(@NonNull File outputFile) throws IOException {
        logger.info("Preparing MediaRecorder");
        MediaRecorder mediaRecorder = new MediaRecorder();

        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mediaRecorder.setPrivacySensitive(true);
        }
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(outputFile.getPath());
        mediaRecorder.setAudioChannels(1);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioEncodingBitRate(32000);
        mediaRecorder.setAudioSamplingRate(defaultSamplingRate);
        mediaRecorder.setMaxFileSize(20L * 1024 * 1024);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaRecorder.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER);
        }

        mediaRecorder.setOnErrorListener(this);
        mediaRecorder.setOnInfoListener(this);

        mediaRecorder.prepare();
        return mediaRecorder;
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        switch (what) {
            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                logger.info("Max recording duration reached. ({})", extra);
                onStopListener.onRecordingReachedMaxDuration();
                break;
            case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                logger.info("Max recording filesize reached. ({})", extra);
                onStopListener.onRecordingReachedMaxFileSize();
                break;
            case MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN:
                logger.info("Unknown media recorder info (What: {} / Extra: {})", what, extra);
                onStopListener.onRecordingError();
                break;
            default:
                logger.info("Undefined media recorder info type (What: {} / Extra: {})", what, extra);
                break;
        }
    }

    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            logger.info("Unknown media recorder error (What: {}, Extra: {})", what, extra);
            onStopListener.onRecordingError();
        } else {
            logger.info("Undefined media recorder error type (What: {}, Extra: {})", what, extra);
        }
    }

    public interface OnStopListener {
        void onRecordingReachedMaxDuration();

        void onRecordingReachedMaxFileSize();

        void onRecordingError();
    }

    public void setOnStopListener(OnStopListener listener) {
        this.onStopListener = listener;
    }
}
