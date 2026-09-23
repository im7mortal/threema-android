package ch.threema.app.listeners;

import androidx.annotation.AnyThread;

public interface SynchronizeContactsListener {
    @AnyThread
    default void onStarted() {}

    @AnyThread
    default void onFinished() {}

    @AnyThread
    default void onError() {}
}
