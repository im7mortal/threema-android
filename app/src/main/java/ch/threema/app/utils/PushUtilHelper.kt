package ch.threema.app.utils

import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.startup.models.AppSystem
import ch.threema.localcrypto.MasterKeyManager
import kotlinx.coroutines.runBlocking
import org.koin.mp.KoinPlatform

/**
 * This object only exists to allow [PushUtil] to make use of suspend function.
 */
object PushUtilHelper {
    @JvmStatic
    fun isProtected(masterKeyManager: MasterKeyManager): Boolean = runBlocking {
        masterKeyManager.isProtected()
    }

    @JvmStatic
    fun awaitSessionScope() = runBlocking {
        KoinPlatform.getKoin().get<AppStartupMonitor>().awaitSystem(AppSystem.UNLOCKED_MASTER_KEY)
    }
}
