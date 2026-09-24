package ch.threema.app.services;

import androidx.annotation.WorkerThread;
import ch.threema.base.SessionScoped;

@SessionScoped
@Deprecated
public interface SynchronizeContactsService {

    boolean instantiateSynchronizationAndRun();

    boolean isSynchronizationInProgress();

    boolean enableSyncFromLocal();

    @WorkerThread
    boolean disableSyncFromLocal(Runnable runAfterRemovedAccount);
}
