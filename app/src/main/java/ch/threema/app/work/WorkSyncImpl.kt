package ch.threema.app.work

import ch.threema.app.applogo.UpdateAppLogoUseCase
import ch.threema.app.asynctasks.AddOrUpdateWorkContactBackgroundTask
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.restrictions.AppRestrictionService
import ch.threema.app.restrictions.AppRestrictions
import ch.threema.app.services.UserService
import ch.threema.app.services.license.LicenseService
import ch.threema.app.services.notification.NotificationService
import ch.threema.app.tasks.BatchUpdateContactAvailabilityStatusTask
import ch.threema.app.tasks.BatchUpdateContactWorkLastFullSyncAtTask
import ch.threema.app.workers.ContactUpdateWorker
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.common.Http
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.UserCredentials
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.protocol.api.work.WorkContact
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.types.IdentityString
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val logger = getThreemaLogger("WorkSyncImpl")

/**
 * The work sync is only run on work and onprem and is used to update work data in the app. To achieve this, it sends the user's identity together
 * with the credentials and the contacts' identities to the work server (fetch2). In return, mdm parameters, work contacts and company logo are
 * returned. This data is then applied locally.
 *
 * Note that the work sync currently also runs the [LegacyWorkPropertiesUpdater] and also runs [ContactUpdateWorker.fetchAndUpdateContactModels] for
 * all upgraded work contacts. If [ContactUpdateWorker.fetchAndUpdateContactModels] does not succeed, the work sync itself does also return an error.
 */
@Suppress("unused")
class WorkSyncImpl(
    private val preferenceService: PreferenceService,
    private val licenseService: LicenseService<*>,
    private val apiConnector: APIConnector,
    private val notificationService: NotificationService,
    private val userService: UserService,
    private val identityStore: IdentityStore,
    private val contactModelRepository: ContactModelRepository,
    private val appRestrictions: AppRestrictions,
    private val updateAppLogoUseCase: UpdateAppLogoUseCase,
    private val appRestrictionService: AppRestrictionService,
    private val legacyWorkPropertiesUpdater: LegacyWorkPropertiesUpdater,
    private val taskManager: TaskManager,
    private val dispatcherProvider: DispatcherProvider,
) : WorkSync {
    override suspend fun run(
        forceAppLogoFetch: Boolean,
    ): WorkSync.WorkSyncResult = mutex.withLock {
        logger.info("Running work sync. Force to fetch app logos = {}", forceAppLogoFetch)

        val credentials = licenseService.loadCredentials() as? UserCredentials
            ?: return WorkSync.WorkSyncResult.MissingCredentials

        return performWorkSync(
            credentials = credentials,
            forceAppLogoFetch = forceAppLogoFetch,
        )
    }

    private suspend fun performWorkSync(
        credentials: UserCredentials,
        forceAppLogoFetch: Boolean,
    ): WorkSync.WorkSyncResult = withContext(dispatcherProvider.io) {
        val workData = try {
            val identities = contactModelRepository
                .getAll()
                .map(ContactModel::identity)
                .toTypedArray()
            apiConnector.fetchWorkData(
                /* username = */
                credentials.username,
                /* password = */
                credentials.password,
                /* identities = */
                identities,
            )
        } catch (e: Exception) {
            logger.error("Failed to fetch2 work data from API", e)
            notificationService.cancelWorkSyncProgress()
            return@withContext WorkSync.WorkSyncResult.Fetch2Failed
        }

        if (workData.responseCode > 0) {
            logger.error("Failed to fetch work data. Server response code = {}", workData.responseCode)
            notificationService.cancelWorkSyncProgress()
            return@withContext if (workData.responseCode == Http.StatusCode.UNAUTHORIZED) {
                WorkSync.WorkSyncResult.Fetch2Unauthorized
            } else {
                WorkSync.WorkSyncResult.Fetch2Failed
            }
        }

        val existingWorkIdentities = contactModelRepository
            .getAll()
            .mapNotNull(ContactModel::data)
            .filter { it.workVerificationLevel == WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED }
            .map(ContactModelData::identity)
            .toSet()
        val fetchedWorkIdentities = workData.workContacts
            .map(WorkContact::threemaId)
            .toSet()

        val pendingContactWorkLastFullSyncAtUpdates = mutableMapOf<IdentityString, Instant>()
        val pendingContactAvailabilityStatusUpdates = mutableMapOf<IdentityString, AvailabilityStatus>()

        // Create or update work contacts
        workData
            .workContacts
            .mapNotNull { workContact ->
                AddOrUpdateWorkContactBackgroundTask(
                    workContact = workContact,
                    myIdentity = userService.identity!!,
                    contactModelRepository = contactModelRepository,
                    pendingUpdateContactWorkLastFullSyncAt = { identity, workLastFullSyncAt ->
                        pendingContactWorkLastFullSyncAtUpdates[identity] = workLastFullSyncAt
                    },
                    pendingUpdateContactAvailabilityStatus = { identity, availabilityStatus ->
                        pendingContactAvailabilityStatusUpdates[identity] = availabilityStatus
                    },
                ).runSynchronously()
            }

        // Schedule tasks to batch-update contacts workLastFullSyncAtTimestamp values
        if (pendingContactWorkLastFullSyncAtUpdates.isNotEmpty()) {
            val batchUpdateContactWorkLastFullSyncAtTasks = BatchUpdateContactWorkLastFullSyncAtTask.createBatchedTasks(
                workLastFullSyncAtTimestamps = pendingContactWorkLastFullSyncAtUpdates,
            )
            batchUpdateContactWorkLastFullSyncAtTasks.forEach(taskManager::schedule)
        }

        // Schedule tasks to batch-update contacts availability status values
        if (pendingContactAvailabilityStatusUpdates.isNotEmpty()) {
            val batchUpdateContactAvailabilityStatusTasks = BatchUpdateContactAvailabilityStatusTask.createBatchedTasks(
                availabilityStatuses = pendingContactAvailabilityStatusUpdates,
            )
            batchUpdateContactAvailabilityStatusTasks.forEach(taskManager::schedule)
        }

        val upgradedContacts = (fetchedWorkIdentities - existingWorkIdentities)
            .mapNotNull(contactModelRepository::getByIdentity)

        // Downgrade work contacts
        (existingWorkIdentities - fetchedWorkIdentities)
            .mapNotNull(contactModelRepository::getByIdentity)
            .forEach { downgradedContact ->
                // The contact is no longer a work contact, so set work verification level to none
                downgradedContact.setWorkVerificationLevelFromLocal(WorkVerificationLevel.NONE)

                // Additionally, the contact may not be server verified anymore (except it has been
                // fully verified before)
                if (downgradedContact.data?.verificationLevel == VerificationLevel.SERVER_VERIFIED) {
                    downgradedContact.setVerificationLevelFromLocal(VerificationLevel.UNVERIFIED)
                }
            }

        // Update app-logos
        logger.trace("Updating app logos")
        val updateAppLogoJob = coroutineScope {
            launch {
                updateAppLogoUseCase.call(
                    lightUrl = workData.logoLight,
                    darkUrl = workData.logoDark,
                    forceUpdate = forceAppLogoFetch,
                )
            }
        }
        preferenceService.setCustomSupportUrl(workData.supportUrl)
        // Save the Mini-MDM Parameters to a local file
        appRestrictionService.storeWorkMDMSettings(workData.mdm)

        updateLegacyWorkProperties()

        preferenceService.setWorkDirectoryEnabled(workData.directory.enabled)
        preferenceService.setWorkDirectoryCategories(workData.directory.categories)
        preferenceService.setWorkOrganization(workData.organization)
        logger.info("Received work sync check interval: {}", workData.checkInterval)
        if (workData.checkInterval > 0) {
            // schedule next interval
            preferenceService.setWorkSyncCheckInterval(workData.checkInterval.seconds)
        }

        notificationService.cancelWorkSyncProgress()

        updateAppLogoJob.join()

        logger.info("Refreshing work data successfully finished")

        if (upgradedContacts.isEmpty()) {
            return@withContext WorkSync.WorkSyncResult.Success
        }
        val wasSuccessful = ContactUpdateWorker.fetchAndUpdateContactModels(
            contactModels = upgradedContacts,
            apiConnector = apiConnector,
            preferenceService = preferenceService,
        )
        return@withContext if (wasSuccessful) {
            WorkSync.WorkSyncResult.Success
        } else {
            WorkSync.WorkSyncResult.ContactUpdateFailed
        }
    }

    suspend fun updateLegacyWorkProperties() {
        val result = legacyWorkPropertiesUpdater.run()
        when (result) {
            LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult.Success -> logger.info("Legacy work properties successfully updated")
            LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult.MissingIdentity -> error("Identity cannot be missing at this point")
            LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult.MissingCredentials -> error("Credentials are missing")
            LegacyWorkPropertiesUpdater.UpdateLegacyWorkPropertiesResult.Failed -> logger.error("Could not update legacy work properties")
        }
    }

    companion object {
        private val mutex = Mutex()
    }
}
