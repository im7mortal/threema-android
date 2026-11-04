package ch.threema.app.services.messageplayer;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.session.MediaController;

import com.google.common.util.concurrent.ListenableFuture;

import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ch.threema.app.eventbus.GlobalEventBuses;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.services.NotificationPreferenceService;
import ch.threema.app.preference.service.PreferenceService;
import ch.threema.app.utils.MimeUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.base.SessionScoped;
import ch.threema.domain.protocol.csp.messages.file.FileData;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;

@SessionScoped
public class MessagePlayerServiceImpl implements MessagePlayerService {
    private static final Logger logger = getThreemaLogger("MessagePlayerServiceImpl");

    private final Map<Integer, MessagePlayer> messagePlayers = new ConcurrentHashMap<>();
    private final Context context;
    @NonNull
    private final MessageService messageService;
    @NonNull
    private final PreferenceService preferenceService;
    @NonNull
    private final NotificationPreferenceService notificationPreferenceService;
    @NonNull
    private final GlobalEventBuses globalEventBuses;
    @NonNull
    private final MessageFileDecryptionHelper messageFileDecryptionHelper;

    public MessagePlayerServiceImpl(
        @NonNull Context context,
        @NonNull MessageService messageService,
        @NonNull PreferenceService preferenceService,
        @NonNull NotificationPreferenceService notificationPreferenceService,
        @NonNull GlobalEventBuses globalEventBuses,
        @NonNull MessageFileDecryptionHelper messageFileDecryptionHelper
    ) {
        this.context = context;
        this.messageService = messageService;
        this.preferenceService = preferenceService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.globalEventBuses = globalEventBuses;
        this.messageFileDecryptionHelper = messageFileDecryptionHelper;
    }

    @Override
    public MessagePlayer createPlayer(
            MessageModel messageModel,
            @Nullable WeakReference<Activity> activityWeakReference,
            MessageReceiver<?> messageReceiver,
            @Nullable ListenableFuture<MediaController> mediaControllerFuture
    ) {
        int key = messageModel.getId();
        MessagePlayer o;

        synchronized (this.messagePlayers) {
            o = this.messagePlayers.get(key);

            if (o == null) {
                if (messageModel.getType() == MessageType.FILE) {
                    if (MimeUtil.isAudioFile(messageModel.getFileData().getMimeType())
                        && messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA) {
                        o = new AudioMessagePlayer(
                            this.context,
                            this.messageService,
                            this.messageFileDecryptionHelper,
                            this.preferenceService,
                            this.notificationPreferenceService,
                            this.globalEventBuses,
                            messageReceiver,
                            mediaControllerFuture,
                            messageModel
                        );
                    } else if (MimeUtil.isAnimatedImageFormat(messageModel.getFileData().getMimeType())
                        && (messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA
                        || messageModel.getFileData().getRenderingType() == FileData.RENDERING_STICKER)) {
                        o = new AnimatedImageDrawableMessagePlayer(
                            this.context,
                            this.messageService,
                            this.messageFileDecryptionHelper,
                            this.preferenceService,
                            messageReceiver,
                            messageModel
                        );
                    } else {
                        o = new FileMessagePlayer(
                            this.context,
                            this.messageService,
                            this.messageFileDecryptionHelper,
                            messageReceiver,
                            messageModel
                        );
                    }
                }
                logger.debug("creating new player {}", key);
            } else {
                // make sure data model is updated as its status may have changed after the player has been created
                if (messageModel.getType() == MessageType.FILE &&
                    MimeUtil.isAudioFile(messageModel.getFileData().getMimeType()) &&
                    messageModel.getFileData().getRenderingType() == FileData.RENDERING_MEDIA) {
                    o.setData(messageModel.getFileData());
                }
                logger.debug("recycling existing player {}", key);
            }
            if (o != null) {
                if (activityWeakReference != null) {
                    Activity activity = activityWeakReference.get();
                    if (activity != null) {
                        if (o.isReceiverMatch(messageReceiver)) {
                            o.setCurrentActivity(activity, messageReceiver);
                        } else {
                            o.release();
                        }
                    }
                }
                this.messagePlayers.put(key, o);
            }
        }
        if (o != null) {
            o.addListener("service", new MessagePlayer.PlaybackListener() {
                @Override
                public void onPlay(MessageModel messageModel, boolean autoPlay) {
                    //call stop other players first!
                    logger.debug("onPlay autoPlay = {}", autoPlay);

                    if (!autoPlay) {
                        stopOtherPlayers(messageModel);
                    }
                }
            });
        }
        return o;
    }

    private void stopOtherPlayers(MessageModel messageModel) {
        logger.debug("stopOtherPlayers");
        synchronized (this.messagePlayers) {
            for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
                if (!entry.getKey().equals(messageModel.getId())) {
                    logger.debug("maybe stopping player {} if not running ", entry.getKey());
                    entry.getValue().stop();
                }
            }
        }
        logger.debug("otherPlayers stopped");
    }

    @Override
    public void release() {
        logger.debug("release all players");
        synchronized (this.messagePlayers) {
            Iterator<Map.Entry<Integer, MessagePlayer>> iterator = messagePlayers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, MessagePlayer> pair = iterator.next();
                MessagePlayer mp = pair.getValue();
                mp.stop();
                if (mp.release()) {
                    iterator.remove();
                    logger.debug("Releasing player {}", pair.getKey());
                } else {
                    // remove ties to activity
                    mp.setCurrentActivity(null, null);
                    mp.removeListeners();
                    logger.debug("Keep downloading player {}", pair.getKey());
                }
            }
            messagePlayers.clear();
        }
    }

    @Override
    public void stopAll() {
        logger.debug("stop all players");
        synchronized (this.messagePlayers) {
            for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
                entry.getValue().stop();
            }
        }
    }

    @Override
    public void pauseAll(int source) {
        logger.debug("pause all players");
        synchronized (this.messagePlayers) {
            for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
                entry.getValue().pause(source);
            }
        }
    }

    @Override
    public void resumeAll(Activity activity, MessageReceiver messageReceiver, int source) {
        logger.debug("resume all players");
        synchronized (this.messagePlayers) {
            for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
                // re-attach message players to current activity
                if (entry.getValue().isReceiverMatch(messageReceiver)) {
                    entry.getValue().setCurrentActivity(activity, messageReceiver);
                } else {
                    entry.getValue().release();
                }
            }
        }
    }

    @Override
    public void setTranscodeProgress(@NonNull MessageModel messageModel, int progress) {
        synchronized (this.messagePlayers) {
            for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
                if (entry.getKey().equals(messageModel.getId())) {
                    entry.getValue().setTranscodeProgress(progress);
                    return;
                }
            }
        }
    }

    @Override
    public void setTranscodeStart(@NonNull MessageModel messageModel) {
        synchronized (this.messagePlayers) {
            for (Map.Entry<Integer, MessagePlayer> entry : messagePlayers.entrySet()) {
                if (entry.getKey().equals(messageModel.getId())) {
                    entry.getValue().setTranscodeStart();
                    return;
                }
            }
        }
    }
}
