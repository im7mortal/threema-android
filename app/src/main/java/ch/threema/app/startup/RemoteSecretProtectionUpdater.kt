package ch.threema.app.startup

import ch.threema.app.services.UserService
import ch.threema.app.services.license.LicenseService
import ch.threema.base.ThreemaException
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.toCryptographicByteArray
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.protocol.ServerAddressProvider
import ch.threema.domain.types.toIdentityOrNull
import ch.threema.localcrypto.MasterKeyManager
import ch.threema.localcrypto.exceptions.InvalidCredentialsException
import ch.threema.localcrypto.exceptions.PassphraseRequiredException
import ch.threema.localcrypto.models.RemoteSecretClientParameters
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("RemoteSecretProtectionUpdater")

class RemoteSecretProtectionUpdater(
    private val masterKeyManager: MasterKeyManager,
    private val serverAddressProvider: ServerAddressProvider,
    private val userService: UserService,
    private val licenseService: LicenseService<*>,
    private val dispatcherProvider: DispatcherProvider,
) {
    enum class RemoteSecretProtectionUpdateResult {
        Success,
        NetworkError,
        InvalidCredentialsError,
        ServiceInstantiationError,
        PassphraseRequiredError,
        KeyStorageError,
    }

    suspend fun runRemoteSecretProtectionUpdate(): RemoteSecretProtectionUpdateResult = withContext(dispatcherProvider.worker) {
        mutex.withLock {
            try {
                masterKeyManager.updateRemoteSecretProtectionStateIfNeeded(
                    getRemoteSecretClientParameters(),
                )
            } catch (e: IOException) {
                logger.error("Network error occurred", e)
                return@withContext RemoteSecretProtectionUpdateResult.NetworkError
            } catch (e: InvalidCredentialsException) {
                logger.error("Credentials are invalid", e)
                return@withContext RemoteSecretProtectionUpdateResult.InvalidCredentialsError
            } catch (e: ThreemaException) {
                logger.error("Could not instantiate services", e)
                return@withContext RemoteSecretProtectionUpdateResult.ServiceInstantiationError
            } catch (e: PassphraseRequiredException) {
                logger.info("Passphrase required", e)
                return@withContext RemoteSecretProtectionUpdateResult.PassphraseRequiredError
            }
            try {
                masterKeyManager.persistKeyDataIfNeeded()
            } catch (e: Exception) {
                logger.error("Could not persist key data", e)
                return@withContext RemoteSecretProtectionUpdateResult.KeyStorageError
            }
            RemoteSecretProtectionUpdateResult.Success
        }
    }

    private fun getRemoteSecretClientParameters(): RemoteSecretClientParameters =
        RemoteSecretClientParameters(
            workServerBaseUrl = serverAddressProvider
                .getWorkServerUrlLegacy()
                ?: error("No work server URL found"),
            userIdentity = userService.identity
                ?.toIdentityOrNull()
                ?: error("No user identity found"),
            clientKey = userService.privateKey
                ?.toCryptographicByteArray()
                ?: error("No client key found"),
            credentials = licenseService.loadCredentials() as? UserCredentials
                ?: error("No user credentials found"),
        )

    companion object {
        private val mutex = Mutex()
    }
}
