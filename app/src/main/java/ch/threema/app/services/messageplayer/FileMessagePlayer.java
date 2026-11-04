package ch.threema.app.services.messageplayer;

import android.content.Context;
import android.content.Intent;

import java.io.File;

import androidx.annotation.NonNull;
import ch.threema.app.activities.MediaViewerActivity;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.services.MessageService;
import ch.threema.app.utils.FileProviderUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.storage.models.MessageModel;
import ch.threema.storage.models.data.media.FileDataModel;
import ch.threema.storage.models.data.media.MediaMessageDataInterface;

public class FileMessagePlayer extends MessagePlayer {
    private final MessageService messageService;

    protected FileMessagePlayer(
        @NonNull Context context,
        @NonNull MessageService messageService,
        @NonNull MessageFileDecryptionHelper messageFileDecryptionHelper,
        @NonNull MessageReceiver<?> messageReceiver,
        @NonNull MessageModel messageModel
    ) {
        super(context, messageService, messageFileDecryptionHelper, messageReceiver, messageModel);
        this.messageService = messageService;
    }

    @Override
    public MediaMessageDataInterface getData() {
        return this.getMessageModel().getFileData();
    }

    @Override
    protected MessageModel setData(MediaMessageDataInterface data) {
        MessageModel messageModel = this.getMessageModel();
        messageModel.setFileData((FileDataModel) data);
        return messageModel;
    }

    @Override
    protected void play(boolean autoPlay) {
        if (!autoPlay && this.isReceiverMatch(this.currentMessageReceiver)) {
            if (FileUtil.isImageFile(getMessageModel().getFileData()) || FileUtil.isAudioFile(getMessageModel().getFileData()) || FileUtil.isVideoFile(getMessageModel().getFileData())) {
                RuntimeUtil.runOnUiThread(() -> {
                    if (currentActivityRef != null && currentActivityRef.get() != null) {
                        Intent intent = new Intent(getContext(), MediaViewerActivity.class);
                        IntentDataUtil.append(getMessageModel(), intent);
                        intent.putExtra(MediaViewerActivity.EXTRA_ID_IMMEDIATE_PLAY, true);
                        intent.putExtra(MediaViewerActivity.EXTRA_ID_REVERSE_ORDER, true);
                        currentActivityRef.get().startActivityForResult(intent, ThreemaActivity.ACTIVITY_ID_MEDIA_VIEWER);
                    }
                });
                // don't call super - the gallery will handle the decryption
                return;
            }
        }
        super.play(autoPlay);
    }

    @Override
    protected void open(final File decryptedFile) {
        if (this.isReceiverMatch(this.currentMessageReceiver)) {
            RuntimeUtil.runOnUiThread(() -> {
                if (decryptedFile.exists()) {
                    final @NonNull FileDataModel fileData = getMessageModel().getFileData();
                    if (!FileUtil.isImageFile(fileData) && !FileUtil.isVideoFile(fileData)) {
                        messageService.viewMediaMessage(
                            getContext(),
                            getMessageModel(),
                            FileProviderUtil.getUriForFile(getContext(), decryptedFile, fileData.getFileName())
                        );
                    }
                }
            });
        }
    }
}
