package ch.threema.app.mediaattacher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import ch.threema.android.ToastDuration
import ch.threema.android.showToast
import ch.threema.app.R
import ch.threema.app.utils.VideoUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import com.alexvasilkov.gestures.GestureController.OnStateChangeListener
import com.alexvasilkov.gestures.State
import com.alexvasilkov.gestures.views.GestureFrameLayout

private val logger = getThreemaLogger("VideoPreviewFragment")

class VideoPreviewFragment(
    private val mediaAttachItem: MediaAttachItem,
) : Fragment(),
    Player.Listener,
    PreviewFragmentInterface {
    init {
        logScreenVisibility(logger)
        setRetainInstance(true)
    }

    private var rootView: View? = null
    private var videoView: PlayerView? = null
    private var videoPlayer: ExoPlayer? = null
    private var gestureFrameLayout: GestureFrameLayout? = null

    private val onGestureStateChangeListener: OnStateChangeListener =
        object : OnStateChangeListener {
            @OptIn(UnstableApi::class)
            override fun onStateChanged(state: State) {
                if (state.zoom !in 0.95f..1.05f) {
                    if (videoView?.isControllerFullyVisible == true) {
                        videoView?.hideController()
                    }
                }
            }

            override fun onStateReset(oldState: State?, newState: State?) {
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(R.layout.fragment_video_preview, container, false)
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                initOrPlay()
            }

            override fun onPause(owner: LifecycleOwner) {
                videoPlayer?.pause()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                releasePlayer()
                gestureFrameLayout
                    ?.controller
                    ?.removeOnStateChangeListener(onGestureStateChangeListener)
            }
        })
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rootView = rootView ?: return

        videoView = rootView.findViewById(R.id.video_view)
        gestureFrameLayout = rootView.findViewById<GestureFrameLayout>(R.id.video_gesture_frame)
            ?.apply {
                controller.settings.setMaxZoom(2.5f)
                controller.addOnStateChangeListener(onGestureStateChangeListener)
            }
    }

    private fun initOrPlay() {
        val videoPlayer = videoPlayer
        if (videoPlayer == null) {
            initializePlayer(true)
        } else if (!videoPlayer.isPlaying) {
            videoPlayer.play()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        logger.error("Video playback error", error)
        showToast(R.string.an_error_occurred, ToastDuration.LONG)

        releasePlayer()
        initializePlayer(false)
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer(playWhenReady: Boolean) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
            .build()

        val videoPlayer = VideoUtil.getExoPlayer(requireContext())
        videoPlayer.setAudioAttributes(audioAttributes, true)
        videoPlayer.addListener(this)

        videoView?.apply {
            setPlayer(videoPlayer)
            setControllerHideOnTouch(true)
            setControllerShowTimeoutMs(1500)
            showController()
        }

        videoPlayer.setMediaItem(MediaItem.fromUri(mediaAttachItem.uri))
        videoPlayer.playWhenReady = playWhenReady
        videoPlayer.prepare()
        this.videoPlayer = videoPlayer
    }

    fun releasePlayer() {
        videoPlayer?.stop()
        videoPlayer?.release()
        videoPlayer = null
    }
}
