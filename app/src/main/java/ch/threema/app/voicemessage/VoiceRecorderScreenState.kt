package ch.threema.app.voicemessage

import android.media.AudioManager
import java.io.File
import kotlin.time.Duration

data class VoiceRecorderScreenState(
    val mediaState: MediaState,
    val scoAudioState: Int,
) {
    companion object {
        fun initial(file: File) = VoiceRecorderScreenState(
            mediaState = MediaState.Record(
                file = file,
                isRecording = false,
                duration = Duration.ZERO,
            ),
            scoAudioState = AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
        )
    }
}

sealed interface MediaState {

    val file: File
    val duration: Duration

    /**
     *  @param duration The current duration of the recorder (only accurate to one full second)
     */
    data class Record(
        override val file: File,
        override val duration: Duration,
        val isRecording: Boolean,
    ) : MediaState

    data class FinishedRecording(
        override val file: File,
        override val duration: Duration,
    ) : MediaState

    data class Playback(
        override val file: File,
        override val duration: Duration,
        val isPlaying: Boolean,
    ) : MediaState
}
