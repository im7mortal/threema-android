package ch.threema.app.backuprestore;

import java.io.File;

import androidx.annotation.AnyThread;
import androidx.annotation.WorkerThread;
import ch.threema.base.SessionScoped;
import androidx.annotation.NonNull;
import ch.threema.storage.models.ConversationModel;

@SessionScoped
public interface ExportConversationService {

    @WorkerThread
    @NonNull
    ExportResult exportToZip(
        @NonNull ConversationModel conversationModel,
        @NonNull File outputFile,
        @NonNull String password,
        boolean includeMedia
    );

    @AnyThread
    void cancel();

    enum ExportResult {
        SUCCESS,
        FAILURE,
        CANCELLED,
    }
}
