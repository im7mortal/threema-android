package ch.threema.app.systemupdates.updates;

import org.koin.java.KoinJavaComponent;

import ch.threema.app.preference.service.PreferenceService;
import ch.threema.storage.DatabaseProvider;
import ch.threema.storage.models.WebClientSessionModel;
import kotlin.Lazy;

import static ch.threema.common.JavaCompat.isNullOrEmpty;

public class SystemUpdateToVersion40 implements SystemUpdate {

    private final Lazy<PreferenceService> preferenceServiceLazy = KoinJavaComponent.inject(PreferenceService.class);
    private final Lazy<DatabaseProvider> databaseProviderLazy = KoinJavaComponent.inject(DatabaseProvider.class);

    @Override
    public void run() {
        PreferenceService preferenceService = preferenceServiceLazy.getValue();
        String currentPushToken = preferenceService.getPushToken();

        if (!isNullOrEmpty(currentPushToken)) {
            // update all
            databaseProviderLazy.getValue().getWritableDatabase()
                .execSQL(
                    "UPDATE " + WebClientSessionModel.TABLE + " " + "SET " + WebClientSessionModel.COLUMN_PUSH_TOKEN + "=?",
                    new String[]{currentPushToken}
                );
        }
    }

    @Override
    public int getVersion() {
        return 40;
    }
}
