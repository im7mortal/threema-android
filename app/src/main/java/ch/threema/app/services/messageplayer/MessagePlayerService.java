package ch.threema.app.services.messageplayer;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.session.MediaController;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.ref.WeakReference;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.base.SessionScoped;
import ch.threema.storage.models.MessageModel;

@SessionScoped
public interface MessagePlayerService {
    MessagePlayer createPlayer(MessageModel m, @Nullable WeakReference<Activity> activityWeakReference, MessageReceiver<?> messageReceiver, @Nullable ListenableFuture<MediaController> mediaControllerFuture);

    void release();

    void stopAll();

    void pauseAll(int source);

    void resumeAll(Activity activity, MessageReceiver<?> messageReceiver, int source);

    void setTranscodeProgress(@NonNull MessageModel messageModel, int progress);

    void setTranscodeStart(@NonNull MessageModel messageModel);
}
