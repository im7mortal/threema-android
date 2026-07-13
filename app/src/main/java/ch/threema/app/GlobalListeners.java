package ch.threema.app;

import android.app.ForegroundServiceStartNotAllowedException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import ch.threema.android.ToastDuration;
import ch.threema.app.androidcontactsync.AndroidContactChangeMonitor;
import ch.threema.app.listeners.NewSyncedContactsListener;
import ch.threema.app.listeners.SynchronizeContactsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.routines.SynchronizeContactsRoutine;
import ch.threema.app.services.notification.NotificationService;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.services.SessionAndroidService;
import ch.threema.app.webclient.services.SessionWakeUpService;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.state.WebClientSessionState;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;

import ch.threema.storage.models.WebClientSessionModel;
import kotlinx.coroutines.sync.Mutex;
import kotlinx.coroutines.sync.MutexKt;

import static ch.threema.android.ToastKt.showToast;


// TODO(ANDR-4687) This code was originally moved out from ThreemaApplication and needs some heavy refactoring.
//  Most of it would be better suited inside a monitor class. For this, all listeners in use need to be migrated to flows.
public class GlobalListeners {

    private static final Logger logger = getThreemaLogger("GlobalListeners");

    public static final Mutex onAndroidContactChangeMutex = MutexKt.Mutex(false);

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ExecutorService workerExecutor = Executors.newCachedThreadPool();

    @NonNull
    private final AndroidContactChangeMonitor androidContactChangeMonitor;

    public GlobalListeners(
        @NonNull Context appContext,
        @NonNull AndroidContactChangeMonitor androidContactChangeMonitor,
        @NonNull ServiceManager serviceManager
    ) {
        this.appContext = appContext;
        this.androidContactChangeMonitor = androidContactChangeMonitor;
        this.serviceManager = serviceManager;
    }

    @NonNull
    private final Context appContext;
    @NonNull
    private final ServiceManager serviceManager;

    public void setUp() {
        ListenerManager.synchronizeContactsListeners.add(synchronizeContactsListener);
        ListenerManager.newSyncedContactListener.add(getNewSyncedContactListener());
        WebClientListenerManager.serviceListener.add(webClientServiceListener);
        registerContactNameChangeListener();
    }

    public void tearDown() {
        ListenerManager.synchronizeContactsListeners.remove(synchronizeContactsListener);
        ListenerManager.newSyncedContactListener.remove(getNewSyncedContactListener());
        WebClientListenerManager.serviceListener.remove(webClientServiceListener);
        unregisterContactNameChangeListener();
        handler.removeCallbacksAndMessages(null);
        workerExecutor.shutdownNow();
    }

    private void registerContactNameChangeListener() {
        androidContactChangeMonitor.start();
    }

    private void unregisterContactNameChangeListener() {
        androidContactChangeMonitor.stop();
    }

    @NonNull
    private final SynchronizeContactsListener synchronizeContactsListener = new SynchronizeContactsListener() {
        @Override
        public void onStarted(SynchronizeContactsRoutine startedRoutine) {
            androidContactChangeMonitor.stop();
        }

        @Override
        public void onFinished(SynchronizeContactsRoutine finishedRoutine) {
            androidContactChangeMonitor.start();
        }

        @Override
        public void onError(SynchronizeContactsRoutine finishedRoutine) {
            androidContactChangeMonitor.start();
        }
    };

    @Nullable
    private NewSyncedContactsListener newSyncedContactListener = null;

    @NonNull
    private NewSyncedContactsListener getNewSyncedContactListener() {
        if (newSyncedContactListener == null) {
            newSyncedContactListener = contactModels -> {
                NotificationService notificationService = serviceManager.getNotificationService();
                notificationService.showNewSyncedContactsNotification(contactModels);
            };
        }
        return newSyncedContactListener;
    }

    @NonNull
    private final WebClientServiceListener webClientServiceListener = new WebClientServiceListener() {
        @Override
        public void onEnabled() {
            SessionWakeUpService sessionWakeUpService = KoinJavaComponent.get(SessionWakeUpService.class);
            sessionWakeUpService.processPendingWakeupsAsync();
        }

        @Override
        public void onStarted(
            @NonNull final WebClientSessionModel model,
            @NonNull final byte[] permanentKey,
            @NonNull final String browser
        ) {
            logger.info("WebClientListenerManager: onStarted");

            handler.post(() -> {
                String toastText = appContext.getString(R.string.webclient_new_connection_toast);
                if (model.getLabel() != null) {
                    toastText += " (" + model.getLabel() + ")";
                }
                showToast(appContext, toastText, ToastDuration.LONG);

                final Intent intent = new Intent(appContext, SessionAndroidService.class);

                if (SessionAndroidService.isRunning()) {
                    intent.setAction(SessionAndroidService.ACTION_UPDATE);
                    logger.info("sending ACTION_UPDATE to SessionAndroidService");
                    appContext.startService(intent);
                } else {
                    logger.info("SessionAndroidService not running...starting");
                    intent.setAction(SessionAndroidService.ACTION_START);
                    logger.info("sending ACTION_START to SessionAndroidService");
                    if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        // Starting on version S, foreground services cannot be started from the background.
                        // When battery optimizations are disabled (recommended for Threema Web), then no
                        // exception is thrown. Otherwise, we just log it.
                        try {
                            ContextCompat.startForegroundService(appContext, intent);
                        } catch (ForegroundServiceStartNotAllowedException exception) {
                            logger.error("Couldn't start foreground service", exception);
                        }
                    } else {
                        ContextCompat.startForegroundService(appContext, intent);
                    }
                }
            });
        }

        @Override
        public void onStateChanged(
            @NonNull final WebClientSessionModel model,
            @NonNull final WebClientSessionState oldState,
            @NonNull final WebClientSessionState newState
        ) {
            logger.info("WebClientListenerManager: onStateChanged");

            if (newState == WebClientSessionState.DISCONNECTED) {
                handler.post(() -> {
                    logger.info("updating SessionAndroidService");
                    if (SessionAndroidService.isRunning()) {
                        final Intent intent = new Intent(appContext, SessionAndroidService.class);
                        intent.setAction(SessionAndroidService.ACTION_UPDATE);
                        logger.info("sending ACTION_UPDATE to SessionAndroidService");
                        appContext.startService(intent);
                    } else {
                        logger.info("SessionAndroidService not running...not updating");
                    }
                });
            }
        }

        @Override
        public void onStopped(@NonNull final WebClientSessionModel model, @NonNull final DisconnectContext reason) {
            logger.info("WebClientListenerManager: onStopped");

            handler.post(() -> {
                if (SessionAndroidService.isRunning()) {
                    final Intent intent = new Intent(appContext, SessionAndroidService.class);
                    intent.setAction(SessionAndroidService.ACTION_STOP);
                    logger.info("sending ACTION_STOP to SessionAndroidService");
                    appContext.startService(intent);
                } else {
                    logger.info("SessionAndroidService not running...not stopping");
                }
            });
        }
    };
}
