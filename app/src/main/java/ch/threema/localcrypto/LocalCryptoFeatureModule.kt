package ch.threema.localcrypto

import android.os.Build
import ch.threema.app.files.AppDirectoryProvider
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val localCryptoFeatureModule = module {
    single<MasterKeyManagerImpl>() bind MasterKeyManager::class

    factory<MasterKeyCrypto>()
    factory<MasterKeyGenerator>()
    factory<MasterKeyLockStateHolder>()
    factory<MasterKeyStorageStateConverter>()
    factory<MasterKeyStorageStateHolder>()
    factory<PassphraseStore>()
    factory<Version1MasterKeyCrypto>()
    factory<Version2MasterKeyCrypto>()
    factory<Version2MasterKeyStorageDecoder>()
    factory<Version2MasterKeyStorageEncoder>()
    factory<KeyStoreSecretKeyManager>()
    factory<KeyStoreCrypto>()

    factory {
        MasterKeyFileProvider(
            directory = get<AppDirectoryProvider>().appDataDirectory,
        )
    }

    factory<MasterKeyStorageManager> {
        val masterKeyFileProvider = get<MasterKeyFileProvider>()
        MasterKeyStorageManager(
            version2KeyFileManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Version2MasterKeyFileManagerImpl(
                    appContext = get(),
                    keyFile = masterKeyFileProvider.getVersion2KeyStoreProtectedMasterKeyFile(),
                    unencryptedKeyFile = masterKeyFileProvider.getVersion2UnencryptedMasterKeyFile(),
                    encoder = get(),
                    decoder = get(),
                    keyStoreCrypto = get(),
                )
            } else {
                Version2MasterKeyFileManagerAndroid7Impl(
                    appContext = get(),
                    legacyKeyFile = masterKeyFileProvider.getVersion2UnencryptedMasterKeyFile(),
                    encoder = get(),
                    decoder = get(),
                )
            },
            version1KeyFileManager = Version1MasterKeyFileManager(
                appContext = get(),
                keyFile = masterKeyFileProvider.getVersion1MasterKeyFile(),
            ),
            storageStateConverter = get(),
        )
    }
}
