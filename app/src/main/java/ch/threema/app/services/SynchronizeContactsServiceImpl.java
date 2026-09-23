package ch.threema.app.services;

import android.accounts.Account;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;

import org.slf4j.Logger;

import java.util.List;

import androidx.annotation.NonNull;
import ch.threema.app.androidcontactsync.usecases.SynchronizeAndroidContactsUseCase;
import ch.threema.app.preference.service.SynchronizedSettingsService;
import ch.threema.app.utils.AndroidContactUtil;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.data.models.ModelDeletedException;
import ch.threema.data.repositories.ContactModelRepository;
import ch.threema.domain.models.VerificationLevel;

@Deprecated
public class SynchronizeContactsServiceImpl implements SynchronizeContactsService {
    private static final Logger logger = getThreemaLogger("SynchronizeContactsServiceImpl");
    private final ContactService contactService;
    private final @NonNull ContactModelRepository contactModelRepository;
    private final UserService userService;
    @NonNull
    private final SynchronizedSettingsService synchronizedSettingsService;
    private final DeviceService deviceService;
    @NonNull
    private final SynchronizeAndroidContactsUseCase synchronizeAndroidContactsUseCase;

    public SynchronizeContactsServiceImpl(
        ContactService contactService,
        @NonNull ContactModelRepository contactModelRepository,
        UserService userService,
        @NonNull SynchronizedSettingsService synchronizedSettingsService,
        DeviceService deviceService,
        @NonNull SynchronizeAndroidContactsUseCase synchronizeAndroidContactsUseCase
        ) {
        this.synchronizedSettingsService = synchronizedSettingsService;
        this.deviceService = deviceService;
        this.contactService = contactService;
        this.contactModelRepository = contactModelRepository;
        this.userService = userService;
        this.synchronizeAndroidContactsUseCase = synchronizeAndroidContactsUseCase;
    }

    @Override
    public boolean instantiateSynchronizationAndRun() {
            if (this.deviceService != null && this.deviceService.isOnline()) {
                new Thread(() -> {
                    try {
                        synchronizeAndroidContactsUseCase.callBlocking();
                    } catch (SecurityException exception) {
                        logger.error("Could not run contact sync", exception);
                    }
                }, "SynchronizeContactsRoutine").start();
                return true;
            }
        return false;
    }

    @Override
    public boolean isSynchronizationInProgress() {
        return SynchronizeAndroidContactsUseCase.isRunning();
    }

    @Override
    public boolean enableSyncFromLocal() {
        logger.info("Enabling contact sync");
        boolean success = false;

        if (this.userService != null) {
            Account account = this.userService.getAccount(true);
            success = account != null;
        }

        if (success) {
            synchronizedSettingsService.getContactSyncPolicySetting().setFromLocal(true);
        }
        return success;
    }

    @Override
    public boolean disableSyncFromLocal(final Runnable runAfterRemovedAccount) {
        logger.info("Disabling contact sync");
        if (this.userService != null) {
            // TODO(ANDR-4436): This should be refactored.
            while (SynchronizeAndroidContactsUseCase.isRunning()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return false;
                }
            }

            int numDeleted = AndroidContactUtil.getInstance().deleteAllThreemaRawContacts();
            logger.debug("Deleted {} raw contacts", numDeleted);

            if (!this.userService.removeAccount(new AccountManagerCallback<Boolean>() {
                @Override
                public void run(AccountManagerFuture<Boolean> future) {
                    disableSyncFromLocalFinished(runAfterRemovedAccount);
                }
            })) {
                this.disableSyncFromLocalFinished(runAfterRemovedAccount);
            }
        }
        return true;
    }

    private void disableSyncFromLocalFinished(Runnable run) {
        synchronizedSettingsService.getContactSyncPolicySetting().setFromLocal(false);

        if (contactService != null) {
            contactService.removeAllSystemContactLinks();

            // cleanup / degrade remaining identities that are still server verified
            List<String> identities = contactService.getIdentitiesByVerificationLevel(VerificationLevel.SERVER_VERIFIED);
            if (identities != null && !identities.isEmpty()) {
                for (String identity : identities) {
                    ch.threema.data.models.ContactModel model = contactModelRepository.getByIdentity(identity);
                    if (model != null) {
                        try {
                            model.setVerificationLevelFromLocal(VerificationLevel.UNVERIFIED);
                        } catch (ModelDeletedException e) {
                            logger.info("Could not set verification level because contact {} has been deleted", identity, e);
                        }
                    }
                }
            }
        }

        if (run != null) {
            run.run();
        }
    }
}
