package ch.threema.app.work

import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.license.LicenseService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.stores.IdentityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("LegacyWorkPropertiesUpdaterImpl")

@Suppress("unused")
class LegacyWorkPropertiesUpdaterImpl(
    private val apiConnector: APIConnector,
    private val identityStore: IdentityStore,
    private val licenseService: LicenseService<*>,
    private val appRestrictions: AppRestrictions,
    private val appRestrictionService: AppRestrictionService,
    private val dispatcherProvider: DispatcherProvider,
) : LegacyWorkPropertiesUpdater {
    private val coroutineScope by lazy { CoroutineScope(dispatcherProvider.io) }

    override fun runAsynchronously() {
        coroutineScope.launch {
            run()
        }
    }

    override suspend fun run(): LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult = mutex.withLock {
        if (identityStore.getIdentity() == null) {
            logger.warn("Cannot update legacy work properties without identity")
            return LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult.MissingIdentity
        }

        logger.info("Updating (legacy) work properties")

        return updateLegacyWorkProperties(appRestrictionService.mdmSource)
    }

    private suspend fun updateLegacyWorkProperties(mdmSource: MdmSource): LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult =
        withContext(dispatcherProvider.io) {
            val userCredentials = licenseService.loadCredentials() as? UserCredentials
                ?: return@withContext LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult.MissingCredentials

            val updateWorkInfoSuccess = try {
                apiConnector.updateWorkInfo(
                    userCredentials.username,
                    userCredentials.password,
                    identityStore,
                    appRestrictions.getFirstName(),
                    appRestrictions.getLastName(),
                    appRestrictions.getJobTitle(),
                    appRestrictions.getDepartment(),
                    appRestrictions.getCsi(),
                    appRestrictions.getCategory(),
                    mdmSource.getStringRepresentation(),
                )
            } catch (e: Exception) {
                logger.error("Failed to update legacy work properties", e)
                false
            }

            return@withContext if (updateWorkInfoSuccess) {
                LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult.Success
            } else {
                LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult.Failed
            }
        }

    companion object {
        private val mutex = Mutex()
    }
}
