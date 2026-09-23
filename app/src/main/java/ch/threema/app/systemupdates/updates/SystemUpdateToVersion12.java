package ch.threema.app.systemupdates.updates;

import org.koin.java.KoinJavaComponent;

import ch.threema.app.androidcontactsync.usecases.SynchronizeAndroidContactsUseCase;
import ch.threema.app.preference.service.SynchronizedSettingsService;
import ch.threema.app.services.UserService;
import kotlin.Lazy;

public class SystemUpdateToVersion12 implements SystemUpdate {

    private final Lazy<SynchronizeAndroidContactsUseCase> synchronizeAndroidContactsUseCaseLazy = KoinJavaComponent.inject(SynchronizeAndroidContactsUseCase.class);
    private final Lazy<SynchronizedSettingsService> synchronizedSettingsServiceLazy = KoinJavaComponent.inject(SynchronizedSettingsService.class);
    private final Lazy<UserService> userServiceLazy = KoinJavaComponent.inject(UserService.class);

    @Override
    public void run() {
        try {
            synchronizeAndroidContactsUseCaseLazy.getValue().callBlocking();
        } catch (SecurityException exception) {
            // Nothing to do
        }

        SynchronizedSettingsService synchronizedSettingsService = synchronizedSettingsServiceLazy.getValue();
        if (synchronizedSettingsService.isSyncContacts()) {
            UserService userService = userServiceLazy.getValue();
            userService.enableAccountAutoSync(true);
        }
    }

    @Override
    public int getVersion() {
        return 12;
    }
}
