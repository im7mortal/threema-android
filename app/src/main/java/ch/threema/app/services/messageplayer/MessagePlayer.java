package ch.threema.app.services.messageplayer;

import static ch.threema.domain.protocol.csp.messages.file.FileData.RENDERING_MEDIA;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CancellationException;

import ch.threema.app.R;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ProgressListener;
import ch.threema.base.ThreemaException;

import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.storage.models.ContactMessageModel;
import ch.threema.storage.models.GroupMessageModel;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;
import kotlinx.coroutines.Job;

public abstract class MessagePlayer {
    private static final Logger logger = getThreemaLogger("MessagePlayer");

    public static final int SOURCE_UNDEFINED = 0;
    public static final int SOURCE_UI_TOGGLE = 1;
    public static final int SOURCE_LIFECYCLE = 2;
    public static final int SOURCE_AUDIO_RECORDER = 4;
    public static final int SOURCE_VOIP = 5;

    private File decryptedFile;
    private Job decryptionJob;
    private int downloadProgress = 0;
    private int transcodeProgress = 0;

    protected WeakReference<Activity> currentActivityRef;
    protected MessageReceiver<?> currentMessageReceiver;

    public final static int State_NONE = 0;
    public final static int State_DOWNLOADING = 1;
    public final static int State_DOWNLOADED = 2;
    public final static int State_DECRYPTING = 3;
    public final static int State_DECRYPTED = 4;
    public final static int State_PLAYING = 5;
    public final static int State_PAUSE = 6;

    public interface DownloadListener {
        @AnyThread
        default void onStart(MessageModel messageModel) {}

        @AnyThread
        default void onStatusUpdate(MessageModel messageModel, int progress) {}

        @AnyThread
        default void onUnknownProgress(MessageModel messageModel) {}

        @AnyThread
        default void onEnd(MessageModel messageModel, boolean success, String message) {}
    }

    public interface DecryptionListener {
        @MainThread
        void onStart(MessageModel messageModel);

        @MainThread
        void onEnd(MessageModel messageModel, boolean success, String message, File decryptedFile);
    }

    public interface PlaybackListener {
        @AnyThread
        void onPlay(MessageModel messageModel, boolean autoPlay);

        @AnyThread
        default void onPause(MessageModel messageModel) {}

        @AnyThread
        default void onStatusUpdate(MessageModel messageModel, int position) {}

        @AnyThread
        default void onStop(MessageModel messageModel) {}
    }

    protected interface InternalListener {
        @AnyThread
        void onComplete(boolean ok);
    }

    public interface TranscodeListener {
        @AnyThread
        default void onStart() {}

        @AnyThread
        default void onStatusUpdate(int progress) {}
    }

    private final Context context;
    private final Map<String, DownloadListener> downloadListeners = new HashMap<>();
    private final Map<String, DecryptionListener> decryptingListeners = new HashMap<>();
    protected final Map<String, PlaybackListener> playbackListeners = new HashMap<>();
    private final Map<String, TranscodeListener> transcodeListeners = new HashMap<>();
    private final MessageService messageService;
    private final MessageModel messageModel;
    private final MessageReceiver<?> messageReceiver;
    private final MessageFileDecryptionHelper messageFileDecryptionHelper;
    protected int state = State_NONE;

    protected MessagePlayer(
        @NonNull
        Context context,
        @NonNull
        MessageService messageService,
        @NonNull
        MessageFileDecryptionHelper messageFileDecryptionHelper,
        @NonNull
        MessageReceiver<?> messageReceiver,
        @NonNull
        MessageModel messageModel
    ) {
        this.context = context;
        this.messageService = messageService;
        this.messageReceiver = messageReceiver;
        this.messageModel = messageModel;
        this.messageFileDecryptionHelper = messageFileDecryptionHelper;

        //init the state
        var data = getData();
        if (data != null && data.isDownloaded()) {
            this.state = State_DOWNLOADED;
        }
    }

    protected MessageModel getMessageModel() {
        return this.messageModel;
    }

    protected boolean isReceiverMatch(MessageReceiver receiver) {
        if (messageReceiver != null && receiver != null) {
            return this.messageReceiver.getConversationId().equals(receiver.getConversationId());
        }
        return false;
    }

    protected Context getContext() {
        return this.context;
    }

    public void setCurrentActivity(Activity activity, MessageReceiver messageReceiver) {
        // attach player to activity
        this.currentActivityRef = new WeakReference<>(activity);
        this.currentMessageReceiver = messageReceiver;
    }

    public boolean release() {
        logger.debug("release");

        //stop first!
        this.stop();

        //remove decrypted file!
        if (this.decryptedFile != null && this.decryptedFile.exists()) {
            FileUtil.deleteFileOrWarn(this.decryptedFile, "release", logger);
            this.decryptedFile = null;
            this.state = State_DOWNLOADED;
        }

        // do not release players that are in the process of downloading
        return this.state != State_DOWNLOADING;
    }

    public boolean stop() {
        logger.debug("stop");
        if (this.state == State_PLAYING) {
            this.state = State_DECRYPTED;
            synchronized (this.playbackListeners) {
                for (PlaybackListener playbackListener : this.playbackListeners.values()) {
                    playbackListener.onStop(messageModel);
                }
            }
        } else if (this.state == State_DECRYPTING) {
            Job decryptionJob = this.decryptionJob;
            if (decryptionJob != null) {
                if (!decryptionJob.isCancelled()) {
                    logger.debug("cancel decrypt");
                    decryptionJob.cancel(new CancellationException());
                }
            } else {
                this.state = State_DOWNLOADED;
            }
        }

        return true;
    }

    public boolean cancel() {
        // cancel all operations, including download
        logger.debug("cancel");

        boolean result = this.stop();
        if (this.state == State_DOWNLOADING) {
            RuntimeUtil.runOnWorkerThread(() -> this.messageService.cancelMessageDownload(this.getMessageModel()));
            this.state = State_NONE;
        }

        return result;
    }

    public void togglePlayPause() {
        logger.debug("togglePlayPause");

        switch (this.state) {
            case State_PLAYING:
                this.pause(SOURCE_UI_TOGGLE);
                break;
            case State_DOWNLOADING:
            case State_DECRYPTING:
                logger.debug("do nothing (state = {})", this.state);
                break;
            default:
                this.open();
        }
    }

    public void pause() {
        pause(SOURCE_UNDEFINED);
    }

    public void pause(int source) {
        logger.debug("pause. source = {} state = {}", source, this.state);
        if (this.state == State_PLAYING) {
            this.state = State_PAUSE;
            this.makePause(source);
            synchronized (this.playbackListeners) {
                for (PlaybackListener playbackListener : this.playbackListeners.values()) {
                    playbackListener.onPause(this.messageModel);
                }
            }

        }
    }

    public boolean open() {
        return this.open(false);
    }

    public boolean open(final boolean autoPlay) {
        final MediaMessageDataInterface data = this.getData();
        if (data == null) {
            return false;
        }
        if (data.isDownloaded()) {
            play(autoPlay);
        } else {
            download(ok -> {
                if (ok) {
                    data.setDownloaded(true);
                    messageService.save(setData(data));

                    if (autoPlay ||
                        getMessageModel().getFileData().getRenderingType() != RENDERING_MEDIA ||
                        FileUtil.isAudioFile(getMessageModel().getFileData())) {
                        open(autoPlay);
                    }
                }
            }, autoPlay);

        }
        return true;
    }

    public float togglePlaybackSpeed(float currentSpeed) {
        return 1f;
    }

    public MessagePlayer addListener(String key, PlaybackListener listener) {
        synchronized (this.playbackListeners) {
            this.playbackListeners.put(key, listener);
        }
        return this;
    }

    public MessagePlayer addListener(String key, DownloadListener listener) {
        synchronized (this.downloadListeners) {
            this.downloadListeners.put(key, listener);
        }
        return this;
    }

    public MessagePlayer addListener(String key, DecryptionListener listener) {
        synchronized (this.decryptingListeners) {
            this.decryptingListeners.put(key, listener);
        }
        return this;
    }

    public MessagePlayer addListener(String key, TranscodeListener listener) {
        synchronized (this.transcodeListeners) {
            this.transcodeListeners.put(key, listener);
        }
        return this;
    }

    public void removeListeners() {
        synchronized (this.playbackListeners) {
            Iterator<Map.Entry<String, PlaybackListener>> iterator = this.playbackListeners.entrySet().iterator();
            while (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        synchronized (this.downloadListeners) {
            Iterator<Map.Entry<String, DownloadListener>> iterator = this.downloadListeners.entrySet().iterator();
            while (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        synchronized (this.decryptingListeners) {
            Iterator<Map.Entry<String, DecryptionListener>> iterator = this.decryptingListeners.entrySet().iterator();
            while (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        synchronized (this.transcodeListeners) {
            Iterator<Map.Entry<String, TranscodeListener>> iterator = this.transcodeListeners.entrySet().iterator();
            while (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    abstract protected MediaMessageDataInterface getData();

    abstract protected MessageModel setData(MediaMessageDataInterface data);

    abstract protected void open(File decryptedFile);

    protected void makePause(int source) {}

    protected void makeResume(int source) {}

    public void seekTo(int pos) {}

    /**
     * Get duration of media that is currently loaded/playing
     *
     * @return duration in milliseconds
     */
    public int getDuration() {
        return 0;
    }

    /**
     * Get current position of media that is currently playing
     *
     * @return position in milliseconds
     */
    public int getPosition() {
        return 0;
    }

    public int getState() {
        return this.state;
    }

    final public int getDownloadProgress() {
        return this.downloadProgress;
    }

    final public int getTranscodeProgress() {
        return this.transcodeProgress;
    }

    protected void play(final boolean autoPlay) {
        logger.debug("play");
        if (this.state == State_PAUSE) {
            this.state = State_PLAYING;
            this.makeResume(SOURCE_UI_TOGGLE);

            synchronized (this.playbackListeners) {
                for (PlaybackListener playbackListener : this.playbackListeners.values()) {
                    playbackListener.onPlay(messageModel, autoPlay);
                }
            }
            return;
        }

        decryptionJob = messageFileDecryptionHelper.decrypt(messageModel, new MessageFileDecryptionHelper.Callback() {
            @MainThread
            @Override
            public void onStart() {
                logger.debug("Beginning to decrypt file");

                state = State_DECRYPTING;
                synchronized (decryptingListeners) {
                    for (DecryptionListener decryptionListener : decryptingListeners.values()) {
                        decryptionListener.onStart(messageModel);
                    }
                }
            }

            @MainThread
            @Override
            public void onDone(@Nullable File file) {
                if (file != null && file.exists()) {
                    state = State_DECRYPTED;
                    decryptedFile = file;
                    logger.debug("decrypt end {}", messageModel.getId());
                } else {
                    state = State_DOWNLOADED;
                    decryptedFile = null;
                    logger.debug("decrypt failed {}", messageModel.getId());
                }

                synchronized (decryptingListeners) {
                    for (DecryptionListener decryptionListener : decryptingListeners.values()) {
                        decryptionListener.onEnd(messageModel, state == State_DECRYPTED,
                            autoPlay ? "" : getContext().getString(R.string.media_file_not_found), decryptedFile);
                    }
                }

                if (state == State_DECRYPTED) {
                    logger.debug("open after decrypt {}", messageModel.getId());

                    if (currentActivityRef != null && currentActivityRef.get() != null && isReceiverMatch(currentMessageReceiver)) {
                        state = State_PLAYING;
                        open(decryptedFile);

                        synchronized (playbackListeners) {
                            for (PlaybackListener playbackListener : playbackListeners.values()) {
                                playbackListener.onPlay(messageModel, autoPlay);
                            }
                        }
                    }
                }
                decryptionJob = null;
            }

            @MainThread
            @Override
            public void onCancelled() {
                logger.debug("decrypt canceled");

                state = State_DOWNLOADED;
                synchronized (decryptingListeners) {
                    for (DecryptionListener decryptionListener : decryptingListeners.values()) {
                        decryptionListener.onEnd(messageModel, false, null, null);
                    }
                }
                decryptionJob = null;
            }
        });
    }

    protected void download(@NonNull final InternalListener internalListener, final boolean autoplay) {
        //download media first
        if (this.state == State_DOWNLOADING) {
            //do nothing, downloading in progress
            return;
        }
        state = State_DOWNLOADING;
        synchronized (this.downloadListeners) {
            for (DownloadListener downloadListener : this.downloadListeners.values()) {
                downloadListener.onStart(this.messageModel);
            }
        }
        logger.debug("download");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // TODO(ANDR-5276) Mark a message as read when the user manually starts the download.
                    //   This serves as a workaround for the problem that the message model instance
                    //   may be stale by the time the download completes. A stale message model
                    //   might have outdated data, in particular an outdated 'read' flag.
                    messageModel.setRead(true);
                    boolean success = messageService.downloadMediaMessage(messageModel, new ProgressListener() {
                        @Override
                        public void updateProgress(final int progress) {
                            downloadProgress = progress;

                            synchronized (downloadListeners) {
                                for (DownloadListener downloadListener : downloadListeners.values()) {
                                    downloadListener.onStatusUpdate(messageModel, progress);
                                }
                            }
                        }

                        @Override
                        public void noProgressAvailable() {
                            synchronized (downloadListeners) {
                                for (DownloadListener downloadListener : downloadListeners.values()) {
                                    downloadListener.onUnknownProgress(messageModel);
                                }
                            }
                        }

                        @Override
                        public void onFinished(boolean success) {
                            downloadProgress = 100;

                            synchronized (downloadListeners) {
                                for (DownloadListener downloadListener : downloadListeners.values()) {
                                    downloadListener.onStatusUpdate(messageModel, 100);
                                }
                            }
                        }
                    });

                    state = State_DOWNLOADED;
                    synchronized (downloadListeners) {
                        for (DownloadListener downloadListener : downloadListeners.values()) {
                            downloadListener.onEnd(messageModel, success, null);
                        }
                    }

                    internalListener.onComplete(true);
                } catch (Exception e) {
                    String errorMessage;
                    if (state == State_NONE) {
                        // cancelled by user
                        errorMessage = null;
                    } else {
                        // some other error
                        state = State_NONE;
                        errorMessage = autoplay ? null : getContext().getString(R.string.could_not_download_message);
                    }
                    synchronized (downloadListeners) {
                        for (DownloadListener downloadListener : downloadListeners.values()) {
                            downloadListener.onEnd(messageModel, false, errorMessage);
                        }
                    }
                    internalListener.onComplete(false);
                }
            }
        }, "MessagePlayerDownload").start();
    }

    protected final void updatePlayState() {
        synchronized (this.playbackListeners) {
            for (PlaybackListener playbackListener : this.playbackListeners.values()) {
                playbackListener.onStatusUpdate(this.messageModel, getPosition());
            }
        }
    }

    public void setTranscodeProgress(int transcodeProgress) {
        synchronized (this.transcodeListeners) {
            this.transcodeProgress = transcodeProgress;
            for (TranscodeListener transcodeListener : this.transcodeListeners.values()) {
                transcodeListener.onStatusUpdate(transcodeProgress);
            }
        }
    }

    public void setTranscodeStart() {
        synchronized (this.transcodeListeners) {
            for (TranscodeListener transcodeListener : this.transcodeListeners.values()) {
                transcodeListener.onStart();
            }
        }
    }

    @WorkerThread
    protected void markAsConsumed() {
        try {
            // TODO(ANDR-5276): The messageModel instance held by MessagePlayer can get stale,
            //  as it is not properly connected to the message cache. This is mainly a problem
            //  when the 'read' state changes, as it can cause a message to be erroneously moved
            //  back into an 'unread' state. As a workaround for this, we check if the message
            //  is not already read, and if so, we fetch a fresh instance from the database and
            //  use that one instead.
            MessageModel possiblyOutdatedMessageModel = getMessageModel();
            MessageModel messageModel = null;
            if (!possiblyOutdatedMessageModel.isRead()) {
                if (possiblyOutdatedMessageModel instanceof ContactMessageModel) {
                    messageModel = messageService.getContactMessageModel(possiblyOutdatedMessageModel.getId());
                } else if (possiblyOutdatedMessageModel instanceof GroupMessageModel) {
                    messageModel = messageService.getGroupMessageModel(possiblyOutdatedMessageModel.getId());
                }
            } else {
                messageModel = possiblyOutdatedMessageModel;
            }

            if (messageModel != null) {
                messageService.markAsConsumed(messageModel);
            }
        } catch (ThreemaException e) {
            logger.error("Unable to mark message as consumed", e);
        }
    }
}
