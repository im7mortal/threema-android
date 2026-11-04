package ch.threema.app.services.messageplayer;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

/**
 * A subclass of the MessagePlayer made for downloading files and sending them to Threema Web.
 */
public class WebClientMessagePlayer extends MessagePlayer {
    public WebClientMessagePlayer(
        @NonNull Context context,
        @NonNull MessageService messageService,
        @NonNull MessageFileDecryptionHelper messageFileDecryptionHelper,
        @NonNull MessageReceiver<?> messageReceiver,
        @NonNull MessageModel messageModel
    ) {
        super(context, messageService, messageFileDecryptionHelper, messageReceiver, messageModel);
    }

    @Override
    protected MediaMessageDataInterface getData() {
        var messageModel = getMessageModel();
        if (messageModel.getType() == MessageType.FILE) {
            return this.getMessageModel().getFileData();
        }
        return null;
    }

    @Override
    protected MessageModel setData(MediaMessageDataInterface data) {
        return null;
    }

    @Override
    public boolean open() {
        markAsConsumed();
        return super.open();
    }

    @Override
    protected void open(File decryptedFile) {
    }
}
