package ch.threema.app.restrictions

import android.content.Context
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import ch.threema.app.R
import ch.threema.app.di.injectNullableNonBinding
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.license.LicenseServiceUser
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.takeUnlessEmpty
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.protocol.api.work.WorkMDMSettings
import ch.threema.localcrypto.exceptions.MasterKeyLockedException
import kotlin.concurrent.Volatile
import org.koin.core.component.KoinComponent

private val logger = getThreemaLogger("AppRestrictionServiceImpl")

class AppRestrictionServiceImpl(
    private val appContext: Context,
    private val mdmSettingsStore: MdmSettingsStore,
    private val applyAppRestrictionsWorkerScheduler: ApplyAppRestrictionsWorker.Scheduler,
    private val getExternalMdmParameters: () -> Bundle,
    private val workInfoUpdater: WorkInfoUpdater,
) : AppRestrictionService, KoinComponent {
    private val licenseService: LicenseService<*>? by injectNullableNonBinding()

    override var appRestrictions: Bundle? = null
        private set

    /**
     * Always make sure, that only parameters allowed for the threema MDM are stored in these settings.
     */
    @Volatile
    private var workMDMSettings: WorkMDMSettings? = null

    /**
     * Determine if this app is under control of an external MDM/EMM with a local DPC and at least one parameter set
     */
    private var hasExternalMDMRestrictions = false

    private val externalMdmOnlyRestrictions: Set<String> by lazy {
        EXTERNAL_MDM_ONLY_RESTRICTIONS_IDS
            .map { resId ->
                appContext.getString(resId)
            }
            .toSet()
    }

    @VisibleForTesting
    @WorkerThread
    fun getWorkMDMSettings(): WorkMDMSettings? {
        workMDMSettings?.let { workMDMSettings ->
            return workMDMSettings
        }
        workMDMSettings = try {
            mdmSettingsStore.getWorkMDMSettings()
        } catch (_: MasterKeyLockedException) {
            null
        }
            ?.let { unfilteredSettings ->
                filterWorkMdmSettings(unfilteredSettings)
            }
        return workMDMSettings
    }

    @WorkerThread
    override fun storeWorkMDMSettings(settings: WorkMDMSettings) {
        if (workMDMSettings == settings) {
            return
        }
        try {
            mdmSettingsStore.storeWorkMDMSettings(settings)
        } catch (e: MasterKeyLockedException) {
            logger.error("Failed to store MDM settings, master key was locked", e)
        }
        workMDMSettings = filterWorkMdmSettings(settings)
        reload()
    }

    /**
     * Create a copy of the WorkMDMSettings with parameters removed that are not available for the threema mdm.
     * This is to prevent invalid parameters from a malicious threema mdm server.
     */
    private fun filterWorkMdmSettings(unfilteredSettings: WorkMDMSettings): WorkMDMSettings =
        unfilteredSettings.copy(
            parameters = buildMap {
                unfilteredSettings.parameters.forEach { (key, value) ->
                    if (!externalMdmOnlyRestrictions.contains(key)) {
                        put(key, value)
                    } else {
                        logger.warn("Non work mdm restriction in WorkMDMSettings: {}", key)
                    }
                }
            },
        )

    override val mdmSource: String?
        get() = buildString {
            if (hasThreemaMDMRestrictions()) {
                append("m")
            }
            if (hasExternalMDMRestrictions) {
                append("e")
            }
        }
            .takeUnlessEmpty()

    /**
     * Determine if this app is under control of Threema MDM and has at least one parameter set
     *
     * @return true if Threema MDM is active
     */
    private fun hasThreemaMDMRestrictions(): Boolean =
        workMDMSettings?.parameters?.isEmpty() == false

    @WorkerThread
    override fun reload() {
        logger.info("Reloading restrictions")
        val appRestrictions = getExternalMdmParameters()

        this.appRestrictions = appRestrictions
        hasExternalMDMRestrictions = !appRestrictions.isEmpty

        if (hasExternalMDMRestrictions) {
            updateUserCredentials()
        }

        // Get Threema MDM Settings and override
        getWorkMDMSettings()?.let { settings ->
            settings.parameters.forEach { (key, value) ->
                if (settings.override || !appRestrictions.containsKey(key)) {
                    when (value) {
                        is Int -> appRestrictions.putInt(key, value)
                        is Boolean -> appRestrictions.putBoolean(key, value)
                        is String? -> appRestrictions.putString(key, value)
                        is Long -> appRestrictions.putLong(key, value)
                        is Double -> appRestrictions.putDouble(key, value)
                    }
                }
            }
        }

        applyAppRestrictionsWorkerScheduler.applyAppRestrictions()

        logger.info("Reloaded restrictions, MDM source = {}", mdmSource)
    }

    /**
     * Update the stored credentials if changed username or password are provided via mdm.
     */
    private fun updateUserCredentials() {
        logger.info("Updating user credentials")
        val mdmUsername = getStringRestriction(R.string.restriction__license_username)
        val mdmPassword = getStringRestriction(R.string.restriction__license_password)

        if (mdmUsername == null && mdmPassword == null) {
            logger.info("No credentials provided via mdm")
            return
        }

        val licenseService = licenseService
            ?: run {
                logger.error("User license service not available")
                return
            }
        if (licenseService !is LicenseServiceUser) {
            logger.error("License service is of wrong type: {}", licenseService.javaClass)
            return
        }

        val currentCredentials = licenseService.loadCredentials()
        val mergedCredentials = mergeCurrentAndMdmCredentials(currentCredentials, mdmUsername, mdmPassword)
        if (mergedCredentials != null && mergedCredentials != currentCredentials) {
            logger.info("Update changed work credentials")
            licenseService.saveCredentials(mergedCredentials)
            workInfoUpdater.updateWorkInfo(licenseService)
        }
    }

    private fun getStringRestriction(@StringRes key: Int): String? =
        appRestrictions?.getString(appContext.getString(key))?.takeUnlessEmpty()

    private fun mergeCurrentAndMdmCredentials(
        currentCredentials: UserCredentials?,
        mdmUsername: String?,
        mdmPassword: String?,
    ): UserCredentials? {
        val currentUsername = currentCredentials?.username
        val currentPassword = currentCredentials?.password
        val username = mdmUsername ?: currentUsername
            ?: return null
        val password = mdmPassword ?: currentPassword
            ?: return null
        return UserCredentials(username, password)
    }

    companion object {
        /**
         * MDM restrictions that are ignored when provided by the Threema MDM
         */
        private val EXTERNAL_MDM_ONLY_RESTRICTIONS_IDS = arrayOf(
            R.string.restriction__id_backup,
            R.string.restriction__id_backup_password,
            R.string.restriction__safe_password,
            R.string.restriction__license_username,
            R.string.restriction__license_password,
        )
    }
}
