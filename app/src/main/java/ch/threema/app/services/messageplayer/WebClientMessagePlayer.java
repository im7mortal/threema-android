package ch.threema.app.services.messageplayer;

import android.content.Context;

import java.io.File;

import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.FileService;
import ch.threema.app.services.MessageService;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.MessageType;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

/**
 * A subclass of the MessagePlayer made for downloading files and sending them to Threema Web.
 */
public class WebClientMessagePlayer extends MessagePlayer {
    public WebClientMessagePlayer(Context context,
                                  MessageService messageService,
                                  FileService fileService,
                                  MessageReceiver messageReceiver,
                                  AbstractMessageModel messageModel) {
        super(context, messageService, fileService, messageReceiver, messageModel);
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
    protected AbstractMessageModel setData(MediaMessageDataInterface data) {
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

    @Override
    protected void makePause(int source) {
    }

    @Override
    protected void makeResume(int source) {
    }

    @Override
    public void seekTo(int pos) {
    }

    @Override
    public int getDuration() {
        return 0;
    }

    @Override
    public int getPosition() {
        return 0;
    }
}
