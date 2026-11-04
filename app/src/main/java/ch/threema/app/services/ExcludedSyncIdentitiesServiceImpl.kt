@file:Suppress("LoggingSimilarMessage")

package ch.threema.app.services

import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.tasks.TaskCreator
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.taskmanager.TriggerSource
import ch.threema.domain.types.IdentityString
import java.lang.ref.WeakReference

private val logger = getThreemaLogger("ExcludedSyncIdentitiesServiceImpl")

class ExcludedSyncIdentitiesServiceImpl(
    private val preferenceService: PreferenceService,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskCreator: TaskCreator,
) : ExcludedSyncIdentitiesService {
    private var excludedSyncIdentitiesCache: WeakReference<Set<IdentityString>> = WeakReference(null)

    @Synchronized
    override fun excludeFromSync(identity: IdentityString, triggerSource: TriggerSource) {
        if (isExcluded(identity)) {
            logger.warn("Cannot exclude identity {} from address book sync as it is already excluded", identity)
            return
        }
        logger.info("Excluding {} from address book sync", identity)
        replaceExcludedIdentities(getAllExcludedIdentities() + identity, triggerSource)
    }

    @Synchronized
    override fun removeExcludedIdentity(identity: IdentityString, triggerSource: TriggerSource) {
        if (!isExcluded(identity)) {
            logger.warn("Cannot remove excluded identity {} from address book sync as it hasn't been excluded", identity)
            return
        }
        logger.info("Removing identity {} from exclusion list for sync", identity)
        replaceExcludedIdentities(getAllExcludedIdentities() - identity, triggerSource)
    }

    @Synchronized
    override fun replaceExcludedIdentities(identities: Set<IdentityString>, triggerSource: TriggerSource) {
        logger.info("Setting updated sync exclusion list")
        preferenceService.setEncryptedList(UNIQUE_LIST_NAME, identities.toTypedArray())
        excludedSyncIdentitiesCache = WeakReference(identities)
        if (multiDeviceManager.isMultiDeviceActive && triggerSource != TriggerSource.SYNC) {
            taskCreator.scheduleReflectExcludeFromSyncIdentitiesTask()
        }
    }

    @Synchronized
    override fun replaceExcludedIdentitiesWithoutReflection(identities: Set<IdentityString>) {
        logger.info("Adding excluded identities without reflection")
        val existingExcludedIdentities = getAllExcludedIdentities()
        val updatedExcludedIdentities = existingExcludedIdentities + identities
        if (existingExcludedIdentities != updatedExcludedIdentities) {
            preferenceService.setEncryptedList(UNIQUE_LIST_NAME, updatedExcludedIdentities.toTypedArray())
            excludedSyncIdentitiesCache = WeakReference(updatedExcludedIdentities)
        }
    }

    @Synchronized
    override fun getAllExcludedIdentities(): Set<IdentityString> {
        return excludedSyncIdentitiesCache.get() ?: run {
            val excludedIdentities = preferenceService.getEncryptedList(UNIQUE_LIST_NAME).toMutableSet()
            excludedSyncIdentitiesCache = WeakReference(excludedIdentities)
            excludedIdentities
        }
    }

    @Synchronized
    override fun isExcluded(identity: IdentityString): Boolean {
        return getAllExcludedIdentities().contains(identity)
    }

    companion object {
        private const val UNIQUE_LIST_NAME = "identity_list_sync_excluded"
    }
}
