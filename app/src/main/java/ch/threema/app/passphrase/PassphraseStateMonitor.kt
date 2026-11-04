package ch.threema.app.passphrase

import ch.threema.app.monitors.Monitor
import ch.threema.app.services.PassphraseService
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.models.PassphraseLockState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map

class PassphraseStateMonitor(
    private val masterKeyManager: MasterKeyManager,
    private val passphraseServiceStarter: PassphraseService.Starter,
) : Monitor("PassphraseStateMonitor") {
    override suspend fun run() {
        masterKeyManager.passphraseLockState
            .map { lockState ->
                when (lockState) {
                    PassphraseLockState.NO_PASSPHRASE,
                    PassphraseLockState.LOCKED,
                    -> false
                    PassphraseLockState.UNLOCKED -> true
                }
            }
            .distinctUntilChanged()
            .dropWhile { isUnlocked -> !isUnlocked }
            .collect { serviceNeeded ->
                if (serviceNeeded) {
                    passphraseServiceStarter.startIfNeeded()
                } else {
                    passphraseServiceStarter.stop()
                }
            }
    }
}
