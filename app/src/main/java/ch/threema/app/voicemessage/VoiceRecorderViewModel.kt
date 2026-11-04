package ch.threema.app.voicemessage

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.core.net.toUri
import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.framework.BaseViewModel
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.FileService
import ch.threema.app.services.MessageService
import ch.threema.app.ui.MediaItem
import ch.threema.app.utils.MediaPlayerStateWrapper
import ch.threema.app.utils.MimeUtil
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.deleteOrThrow
import ch.threema.common.renameOrThrow
import ch.threema.domain.protocol.csp.messages.file.FileData
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.koin.core.annotation.InjectedParam

private val logger = getThreemaLogger("VoiceRecorderViewModel")

class VoiceRecorderViewModel(
    private val appContext: Context,
    private val messageService: MessageService,
    private val preferenceService: PreferenceService,
    private val fileService: FileService,
    private val appDirectoryProvider: AppDirectoryProvider,
    private val dispatcherProvider: DispatcherProvider,
    @InjectedParam private val messageReceiver: MessageReceiver<*>,
) : BaseViewModel<VoiceRecorderScreenState, VoiceRecorderViewModelEvent>() {

    private var mediaRecorder: MediaRecorder? = null
    var mediaPlayer: MediaPlayerStateWrapper? = null
        private set

    private var recordingTimerJob: Job? = null

    private val onStopRecordingListener = object : AudioRecorder.OnStopListener {
        override fun onRecordingReachedMaxDuration() {
            runAction {
                emitEvent(VoiceRecorderViewModelEvent.RecorderReachedMaxDuration)
            }
        }

        override fun onRecordingReachedMaxFileSize() {
            runAction {
                emitEvent(VoiceRecorderViewModelEvent.RecorderReachedMaxFileSize)
            }
        }

        override fun onRecordingError() {
            runAction {
                stopRecording()
                emitEvent(VoiceRecorderViewModelEvent.RecorderError)
            }
        }
    }

    override suspend fun initialize(): VoiceRecorderScreenState {
        val outputFile = try {
            createAudioOutputFile()
        } catch (e: IOException) {
            logger.error("Failed to create temp audio file", e)
            emitEvent(VoiceRecorderViewModelEvent.FailedToCreateAudioOutputFile)
            awaitCancellation()
        }
        if (!outputFile.exists()) {
            logger.error("Output file {} does not exist after creating it", outputFile)
            emitEvent(VoiceRecorderViewModelEvent.FailedToCreateAudioOutputFile)
            awaitCancellation()
        }
        logger.info("Created recording output file {}", outputFile)
        return VoiceRecorderScreenState.initial(file = outputFile)
    }

    /**
     * Since only ever one voice recording can be active at a time, we always use the same file for storing the recording.
     * We store it in the [AppDirectoryProvider.appDataDirectory] instead of [AppDirectoryProvider.cacheDirectory] to ensure
     * that the file doesn't get deleted mid-recording.
     */
    @Throws(IOException::class)
    private suspend fun createAudioOutputFile(): File = withContext(dispatcherProvider.io) {
        val file = File(appDirectoryProvider.appDataDirectory, "voice_recording.aac")
        file.deleteOrThrow()
        file.createNewFile()
        file
    }

    fun onRequestRecordingStart() = runAction {
        val currentMediaRecordState = currentViewState.mediaState
        if (currentMediaRecordState !is MediaState.Record || currentMediaRecordState.isRecording) {
            return@runAction
        }
        logger.info("Start recording")
        val audioRecorder = AudioRecorder().apply {
            setOnStopListener(onStopRecordingListener)
        }
        try {
            mediaRecorder = audioRecorder.prepare(appContext, currentMediaRecordState.file)
                .also { mediaRecorder ->
                    mediaRecorder.start()
                    logger.info("Started recording with {}", mediaRecorder)
                }
        } catch (e: Exception) {
            emitEvent(VoiceRecorderViewModelEvent.FailedToOpenAudioRecorder)
            logger.error("AudioRecorder exception occurred", e)
            runCatching {
                mediaRecorder?.reset()
                mediaRecorder?.release()
                mediaRecorder = null
            }
            return@runAction
        }
        updateViewState {
            copy(
                mediaState = MediaState.Record(
                    file = currentMediaRecordState.file,
                    isRecording = true,
                    duration = Duration.ZERO,
                ),
            )
        }
        startOrResumeRecordingTimer()
    }

    private fun startOrResumeRecordingTimer() {
        val currentMediaRecordState = currentViewState.mediaState
        if (currentMediaRecordState !is MediaState.Record || !currentMediaRecordState.isRecording || recordingTimerJob?.isActive == true) {
            return
        }
        var fileErrorLogged = false
        recordingTimerJob = launchAction {
            while (isActive) {
                delay(1.seconds)
                val currentState = currentViewState
                if (currentState.mediaState is MediaState.Record && currentState.mediaState.isRecording) {
                    if (!fileErrorLogged && !currentState.mediaState.file.exists()) {
                        fileErrorLogged = true
                        logger.error("File was missing after {}", currentState.mediaState.duration)
                    }
                    updateViewState {
                        if (this == currentState) {
                            currentState.copy(
                                mediaState = currentState.mediaState.copy(
                                    duration = currentState.mediaState.duration + 1.seconds,
                                ),
                            )
                        } else {
                            this
                        }
                    }
                } else {
                    break
                }
            }
        }
    }

    fun onClickPause() = runAction {
        pauseRecording()
    }

    private suspend fun pauseRecording() {
        val currentMediaRecordState = currentViewState.mediaState
        if (currentMediaRecordState !is MediaState.Record || !currentMediaRecordState.isRecording) {
            return
        }
        logger.info("Pause recording")
        try {
            mediaRecorder?.pause()
        } catch (e: Exception) {
            logger.error("Exception while pausing recording", e)
        }
        updateViewState {
            copy(
                mediaState = currentMediaRecordState.copy(
                    isRecording = false,
                ),
            )
        }
    }

    fun onClickResumeRecording() = runAction {
        val currentMediaRecordState = currentViewState.mediaState
        if (currentMediaRecordState !is MediaState.Record || currentMediaRecordState.isRecording) {
            return@runAction
        }
        logger.info("Resume recording")
        try {
            mediaRecorder?.resume()
        } catch (e: Exception) {
            logger.error("Exception while resuming recording", e)
        }
        updateViewState {
            copy(
                mediaState = currentMediaRecordState.copy(
                    isRecording = true,
                ),
            )
        }
        startOrResumeRecordingTimer()
    }

    fun onClickStopRecording() = runAction {
        stopRecording()
        when (currentViewState.mediaState) {
            is MediaState.FinishedRecording,
            is MediaState.Playback,
            -> Unit
            is MediaState.Record -> emitEvent(VoiceRecorderViewModelEvent.RecorderError)
        }
    }

    private suspend fun stopRecording() {
        val currentMediaRecordState = currentViewState.mediaState
        if (currentMediaRecordState !is MediaState.Record) {
            return
        }
        recordingTimerJob?.cancel()
        val recordingFile = currentMediaRecordState.file
        if (!recordingFile.exists()) {
            logger.error("Output file does not exist before stopping recording")
        }
        logger.info("Stop recording to {}, after {}", currentMediaRecordState.file, currentMediaRecordState.duration)
        try {
            mediaRecorder?.let { recorder ->
                recorder.stop()
                logger.info("Stopped recording to {} with {}", recordingFile, recorder)
                if (!recordingFile.exists()) {
                    logger.error("Output file does not exist after stopping recording")
                }
            }
        } catch (e: Exception) {
            logger.error("Exception while stopping recording", e)
        }
        releaseMediaRecorder()

        val sendFile = try {
            moveRecordingFile(recordingFile)
        } catch (e: IOException) {
            logger.error("Failed to move recording file", e)
            return
        }
        val duration = getDurationFromFile(sendFile)
        updateViewState {
            copy(
                mediaState = MediaState.FinishedRecording(
                    file = sendFile,
                    duration = duration,
                ),
            )
        }
    }

    private suspend fun moveRecordingFile(recordingFile: File): File = withContext(dispatcherProvider.io) {
        val sendFile = fileService.createTempFile("voice", ".aac")
        sendFile.deleteOrThrow()
        recordingFile.renameOrThrow(sendFile)
        sendFile
    }

    private suspend fun getDurationFromFile(file: File): Duration = withContext(dispatcherProvider.io) {
        logger.info("Attempting to retrieve duration from file {}", file)
        if (!file.exists()) {
            logger.error("File was not found")
            return@withContext Duration.ZERO
        }
        val durationCheckMediaPlayer: MediaPlayer = try {
            MediaPlayer.create(appContext, file.toUri())
                ?: run {
                    logger.warn("Unable to create a media player for checking size")
                    return@withContext Duration.ZERO
                }
        } catch (e: IllegalStateException) {
            logger.error("Failed to create media player to check duration", e)
            return@withContext Duration.ZERO
        }

        val duration = durationCheckMediaPlayer.duration.coerceAtLeast(0).milliseconds
        durationCheckMediaPlayer.release()
        logger.info("Duration is {}", duration)
        duration
    }

    fun onClickStartPlayback() = runAction {
        val currentMediaFinishedRecordingState = currentViewState.mediaState
        if (currentMediaFinishedRecordingState !is MediaState.FinishedRecording) {
            return@runAction
        }
        val mediaPlayer = MediaPlayerStateWrapper().apply {
            if (currentViewState.scoAudioState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                setAudioStreamType(AudioManager.STREAM_VOICE_CALL)
            } else {
                setAudioStreamType(AudioManager.STREAM_MUSIC)
            }
        }
        logger.info("Initializing media player")
        try {
            mediaPlayer.apply {
                setDataSource(appContext, currentMediaFinishedRecordingState.file.toUri())
                setOnPreparedListener { player: MediaPlayer ->
                    mediaPlayer.start()
                    logger.info("Started media player {}", player)
                    runAction {
                        updateViewState {
                            copy(
                                mediaState = MediaState.Playback(
                                    file = currentMediaFinishedRecordingState.file,
                                    isPlaying = true,
                                    duration = player.duration.coerceAtLeast(0).milliseconds,
                                ),
                            )
                        }
                    }
                }
                setOnCompletionListener { player: MediaPlayer ->
                    runAction {
                        updateViewState {
                            if (mediaState is MediaState.Playback) {
                                copy(
                                    mediaState = mediaState.copy(
                                        isPlaying = false,
                                    ),
                                )
                            } else {
                                this
                            }
                        }
                    }
                    runAction {
                        emitEvent(VoiceRecorderViewModelEvent.PlaybackFinished(player.duration))
                    }
                }
                prepare()
            }
            this@VoiceRecorderViewModel.mediaPlayer = mediaPlayer
        } catch (e: Exception) {
            emitEvent(VoiceRecorderViewModelEvent.FailedToPlayRecording)
            logger.error("Failed to play recording", e)
            releaseMediaPlayer()
        }
    }

    fun onClickPausePlayback() = runAction {
        pausePlayback()
    }

    private suspend fun pausePlayback() {
        val currentMediaPlaybackState = currentViewState.mediaState
        val mediaPlayer = this@VoiceRecorderViewModel.mediaPlayer
        if (currentMediaPlaybackState !is MediaState.Playback || !currentMediaPlaybackState.isPlaying || mediaPlayer == null) {
            return
        }
        logger.info("Pause media player {}", mediaPlayer)
        try {
            mediaPlayer.pause()
        } catch (e: Exception) {
            logger.error("Exception while pausing media player {}", mediaPlayer, e)
        }
        updateViewState {
            copy(
                mediaState = currentMediaPlaybackState.copy(isPlaying = false),
            )
        }
    }

    fun onSeekPlaybackTo(duration: Duration) = runAction {
        val currentMediaPlaybackState = currentViewState.mediaState
        val mediaPlayer = this@VoiceRecorderViewModel.mediaPlayer
        if (currentMediaPlaybackState !is MediaState.Playback || mediaPlayer == null) {
            return@runAction
        }
        logger.info("Seek media player {} to {}", mediaPlayer, duration)
        if (duration > currentMediaPlaybackState.duration) {
            logger.warn("Cant seek media player to a frame exceeding its total duration")
            return@runAction
        }
        mediaPlayer.seekTo(
            duration.inWholeMilliseconds.toInt(),
        )
    }

    fun onClickResumePlayback() = runAction {
        val currentMediaPlaybackState = currentViewState.mediaState
        val mediaPlayer = this@VoiceRecorderViewModel.mediaPlayer
        if (currentMediaPlaybackState !is MediaState.Playback || currentMediaPlaybackState.isPlaying || mediaPlayer == null) {
            return@runAction
        }
        logger.info("Resume media player {}", mediaPlayer)
        try {
            mediaPlayer.start()
        } catch (e: Exception) {
            logger.error("Exception while resuming media player {}", mediaPlayer, e)
        }
        updateViewState {
            copy(
                mediaState = currentMediaPlaybackState.copy(isPlaying = true),
            )
        }
    }

    fun onPause() = runAction {
        when (currentViewState.mediaState) {
            is MediaState.Record -> pauseRecording()
            is MediaState.Playback -> pausePlayback()
            else -> Unit
        }
    }

    fun onClickSend() = runAction {
        currentViewState.mediaState.let { mediaState ->
            if (mediaState is MediaState.Record && mediaState.duration == Duration.ZERO) {
                // send button was likely clicked by accident
                return@runAction
            }
        }

        logger.info("Preparing to send voice recording")
        when (currentViewState.mediaState) {
            is MediaState.Record -> stopRecording()
            is MediaState.Playback -> releaseMediaPlayer()
            else -> Unit
        }
        // Verify that we are in a valid media state after stopping recording and playback
        val mediaState = currentViewState.mediaState
        when (mediaState) {
            is MediaState.FinishedRecording,
            is MediaState.Playback,
            -> Unit
            is MediaState.Record -> {
                emitEvent(VoiceRecorderViewModelEvent.RecorderError)
                return@runAction
            }
        }

        val file = mediaState.file
        val audioFileDuration = mediaState.duration
        if (audioFileDuration == Duration.ZERO) {
            emitEvent(VoiceRecorderViewModelEvent.FailedToDetermineDuration)
            return@runAction
        }

        val mimeType = MimeUtil.MIME_TYPE_AUDIO_AAC
        val uri = file.toUri()
        val type = MimeUtil.getMediaTypeFromMimeType(appContext, mimeType, uri)
        val mediaItem = MediaItem(uri, type, mimeType, null).apply {
            if (type == MediaItem.TYPE_FILE) {
                renderingType = FileData.RENDERING_DEFAULT
            }
            durationMs = audioFileDuration.coerceAtLeast(1.seconds).inWholeMilliseconds
        }
        logger.info("Sending voice recording")
        messageService.sendMediaAsync(
            /* mediaItems = */
            listOf(mediaItem),
            /* messageReceivers = */
            listOf(messageReceiver),
        )
        emitEvent(VoiceRecorderViewModelEvent.Sent)
    }

    fun onClickDiscard(force: Boolean = false) = runAction {
        logger.info("Discarding")
        when (val currentMediaState = currentViewState.mediaState) {
            is MediaState.Record -> {
                stopRecording()
                if (currentMediaState.duration >= discardConfirmationThresholdDuration && !force) {
                    emitEvent(VoiceRecorderViewModelEvent.ConfirmationRequiredToDiscard)
                } else {
                    deleteRecordingFile()
                    emitEvent(VoiceRecorderViewModelEvent.Discarded)
                }
            }
            is MediaState.FinishedRecording -> {
                if (currentMediaState.duration >= discardConfirmationThresholdDuration && !force) {
                    emitEvent(VoiceRecorderViewModelEvent.ConfirmationRequiredToDiscard)
                } else {
                    deleteRecordingFile()
                    emitEvent(VoiceRecorderViewModelEvent.Discarded)
                }
            }
            is MediaState.Playback -> {
                releaseMediaPlayer()
                deleteRecordingFile()
                emitEvent(VoiceRecorderViewModelEvent.Discarded)
            }
        }
    }

    private suspend fun deleteRecordingFile() = withContext(dispatcherProvider.io) {
        try {
            currentViewState.mediaState.file.deleteOrThrow()
        } catch (e: IOException) {
            logger.error("Failed to delete voice recording file", e)
        }
    }

    fun onLostAudioFocus() = runAction {
        when (val currentMediaState = currentViewState.mediaState) {
            is MediaState.Record -> stopRecording()
            is MediaState.FinishedRecording -> Unit
            is MediaState.Playback -> {
                releaseMediaPlayer()
                updateViewState {
                    copy(
                        mediaState = currentMediaState.copy(isPlaying = false),
                    )
                }
            }
        }
    }

    /**
     *  Safe to call for an already released and cleared local media recorder
     */
    private fun releaseMediaRecorder() {
        mediaRecorder?.let { recorder ->
            logger.info("Releasing media recorder {}", recorder)
            runCatching {
                recorder.reset()
                recorder.release()
                mediaRecorder = null
                logger.info("Released media recorder {}", recorder)
            }
        }
    }

    /**
     *  Safe to call for an already released and cleared local media player
     */
    private fun releaseMediaPlayer() {
        mediaPlayer?.let { player ->
            runCatching {
                logger.info("Releasing media player {}", player)
                player.reset()
                player.release()
                mediaPlayer = null
                logger.info("Released media player {}", player)
            }
        }
    }

    fun onScoStateChanged(scoAudioState: Int) = runAction {
        val voiceRecorderBluetoothDisableSetting: Boolean? = when (scoAudioState) {
            AudioManager.SCO_AUDIO_STATE_CONNECTED -> false
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> true
            AudioManager.SCO_AUDIO_STATE_ERROR -> false
            else -> null
        }
        voiceRecorderBluetoothDisableSetting?.let { setting ->
            preferenceService.setVoiceRecorderBluetoothDisabled(setting)
        }
        updateViewState {
            copy(
                scoAudioState = scoAudioState,
            )
        }
        logger.info(
            "SCO audio state: {}",
            when (scoAudioState) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> "connected"
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "disconnected"
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> "connecting"
                AudioManager.SCO_AUDIO_STATE_ERROR -> "error"
                else -> ""
            },
        )
    }

    override fun onCleared() {
        releaseMediaRecorder()
        releaseMediaPlayer()
    }

    companion object {
        private val discardConfirmationThresholdDuration = 10.seconds
    }
}
