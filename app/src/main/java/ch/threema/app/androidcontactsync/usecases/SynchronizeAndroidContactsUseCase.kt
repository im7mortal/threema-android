package ch.threema.app.androidcontactsync.usecases

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.annotation.WorkerThread
import ch.threema.app.androidcontactsync.read.AndroidContactReader
import ch.threema.app.androidcontactsync.synchronization.AndroidContactMatcher
import ch.threema.app.androidcontactsync.synchronization.ThreemaRawContactManager
import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.androidcontactsync.types.RawContact
import ch.threema.app.listeners.NewSyncedContactsListener
import ch.threema.app.managers.ListenerManager
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.preference.service.SynchronizedSettingsService
import ch.threema.app.services.ContactService
import ch.threema.app.services.ExcludedSyncIdentitiesService
import ch.threema.app.services.UserService
import ch.threema.app.utils.AndroidContactUtil
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.TimeProvider
import ch.threema.common.minus
import ch.threema.data.IdentityProvider
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactCreateException
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import ch.threema.logging.logAndReportError
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val logger = getThreemaLogger("SynchronizeAndroidContactsUseCase")

class SynchronizeAndroidContactsUseCase(
    private val appContext: Context,
    private val androidContactMatcher: AndroidContactMatcher,
    private val androidContactReader: AndroidContactReader,
    private val contactModelRepository: ContactModelRepository,
    private val contactService: ContactService,
    private val excludedSyncIdentitiesService: ExcludedSyncIdentitiesService,
    private val getAndroidContactNameUseCase: GetAndroidContactNameUseCase,
    private val identityProvider: IdentityProvider,
    private val preferenceService: PreferenceService,
    private val synchronizedSettingsService: SynchronizedSettingsService,
    private val threemaRawContactManager: ThreemaRawContactManager,
    private val timeProvider: TimeProvider,
    private val userService: UserService,
) {
    // TODO(ANDR-4436): Remove this blocking call and solve this properly using coroutines.
    @RequiresPermission(allOf = [Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS])
    @WorkerThread
    @Deprecated("Use call instead")
    fun callBlocking() = runBlocking {
        call()
    }

    @RequiresPermission(allOf = [Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS])
    suspend fun call() = mutex.withLock {
        ListenerManager.synchronizeContactsListeners.handle { it.onStarted() }
        try {
            synchronizeAndroidContacts()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logger.error("Could not synchronize android contacts due to network problems", e)
            ListenerManager.synchronizeContactsListeners.handle { it.onError() }
        } catch (e: Exception) {
            logger.logAndReportError("Could not synchronize android contacts", e)
            ListenerManager.synchronizeContactsListeners.handle { it.onError() }
        }
        ListenerManager.synchronizeContactsListeners.handle { it.onFinished() }
    }

    @RequiresPermission(allOf = [Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS])
    private suspend fun synchronizeAndroidContacts() {
        logger.info("Synchronizing android contacts")

        if (!ConfigUtils.isPermissionGranted(appContext, Manifest.permission.WRITE_CONTACTS)) {
            logger.warn("No contacts permission. Aborting.")
            return
        }

        if (!synchronizedSettingsService.isSyncContacts()) {
            logger.error("Cannot synchronize android contacts as it is disabled")
            return
        }

        val account = userService.getAccount()
        if (account == null) {
            logger.error("Cannot synchronize android contacts as no account exists")
            return
        }

        val androidContacts = androidContactReader.readAllAndroidContacts()
        val phoneNumbersHash = androidContacts.getPhoneNumbersHash()
        val emailAddressesHash = androidContacts.getEmailAddressesHash()

        if (isUnchangedPhoneNumbersHash(phoneNumbersHash) && isUnchangedEmailAddressesHash(emailAddressesHash) && !isGraceTimeReached()) {
            logger.info("Android contacts are unchanged and grace time is not yet reached. Aborting.")
            return
        }

        preferenceService.setPhoneNumberSyncHashCode(phoneNumbersHash)
        preferenceService.setEmailSyncHashCode(emailAddressesHash)
        preferenceService.setTimeOfLastContactSync(timeProvider.get())

        logger.info("Attempting to synchronize {} android contacts", androidContacts.size)

        val androidContactMatch = androidContactMatcher.matchContacts(androidContacts)
        val androidContactMatches = androidContactMatch.androidContactMatches
            .filterValues { matchedIdentity -> matchedIdentity.identity != identityProvider.getIdentity() }

        applyResults(
            androidContactMatches = androidContactMatches,
            identityToPublicKey = androidContactMatch.identityToPublicKey,
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS])
    private suspend fun applyResults(
        androidContactMatches: Map<AndroidContact, AndroidContactMatcher.MatchedIdentity>,
        identityToPublicKey: Map<Identity, ByteArray>,
    ) {
        val preSynchronizedIdentities = contactService.synchronizedIdentities
            .map(::Identity)
            .toMutableSet()

        val newlyCreatedContactIdentities = mutableSetOf<IdentityString>()
        val synchronizedIdentities = mutableSetOf<Identity>()

        androidContactMatches.forEach { (androidContact, matchedIdentity) ->
            val identity = matchedIdentity.identity

            if (excludedSyncIdentitiesService.isExcluded(identity.value)) {
                logger.info("Identity {} is excluded from sync", identity)
                return@forEach
            }

            val publicKey = identityToPublicKey[identity]
            if (publicKey == null) {
                logger.warn("No public key for identity {} found. Skipping contact.", identity)
                return@forEach
            }

            preSynchronizedIdentities.remove(identity)

            val contactModel = contactModelRepository.getByIdentity(identity)
                ?.also { contactModel ->
                    updateContact(
                        contactModel = contactModel,
                        publicKey = publicKey,
                        androidContact = androidContact,
                    )
                }
                ?: run {
                    createContact(
                        identity = identity,
                        publicKey = publicKey,
                        androidContact = androidContact,
                    )
                        ?.also {
                            newlyCreatedContactIdentities.add(identity.value)
                        }
                }

            if (contactModel != null) {
                synchronizedIdentities.add(Identity(contactModel.identity))
                AndroidContactUtil.getInstance().updateAvatarByAndroidContact(contactModel, appContext)
            }
        }

        if (!preSynchronizedIdentities.isEmpty()) {
            logger.info("Found {} synchronized contacts that are no longer synchronized", preSynchronizedIdentities.size)

            preSynchronizedIdentities
                .mapNotNull(contactModelRepository::getByIdentity)
                .forEach(ContactModel::removeAndroidContactLink)
        }

        // We exclude identities where no contact model exists as we do not want to create raw contacts for contacts that do not exist in Threema
        threemaRawContactManager.updateThreemaRawContacts(
            androidContactMatches
                .filterValues { matchedIdentity -> synchronizedIdentities.contains(matchedIdentity.identity) },
        )

        // Let the user know that new contact(s) was/were added
        val newlyCreatedContacts = contactModelRepository.getByIdentities(newlyCreatedContactIdentities)
        ListenerManager.newSyncedContactListener.handle { listener: NewSyncedContactsListener ->
            listener.onNew(newlyCreatedContacts)
        }
    }

    private suspend fun createContact(
        identity: Identity,
        publicKey: ByteArray,
        androidContact: AndroidContact,
    ): ContactModel? {
        logger.info("Creating new contact for identity {} as result from android contact synchronization", identity)
        val contactName = getAndroidContactNameUseCase.call(androidContact)
        if (contactName == null) {
            logger.warn("Could not get contact name for identity {}", identity)
        }
        val contactModelData = ContactModelData(
            identity = identity.value,
            publicKey = publicKey,
            createdAt = timeProvider.get(),
            lastUpdateAt = null,
            firstName = contactName?.firstName ?: "",
            lastName = contactName?.lastName ?: "",
            nickname = null,
            verificationLevel = VerificationLevel.SERVER_VERIFIED,
            workVerificationLevel = WorkVerificationLevel.NONE,
            identityType = IdentityType.REGULAR, // TODO(ANDR-3044): Fetch identity type
            acquaintanceLevel = AcquaintanceLevel.DIRECT,
            activityState = IdentityState.ACTIVE, // TODO(ANDR-3044): Fetch identity state
            featureMask = 0u, // TODO(ANDR-3044): Fetch feature mask
            syncState = ContactSyncState.IMPORTED,
            readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
            typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
            conversationVisibility = ConversationVisibility.NORMAL,
            androidContactLookupInfo = androidContact.getAndroidContactLookupInfo(),
            localAvatarExpires = null,
            isRestored = false,
            profilePictureBlobId = null,
            jobTitle = null,
            department = null,
            notificationTriggerPolicyOverride = null,
            availabilityStatus = AvailabilityStatus.None,
            workLastFullSyncAt = null,
        )

        return try {
            contactModelRepository.createFromLocal(contactModelData)
        } catch (e: ContactCreateException) {
            logger.error("Could not create contact", e)
            null
        }
    }

    private fun updateContact(
        contactModel: ContactModel,
        publicKey: ByteArray,
        androidContact: AndroidContact,
    ) {
        logger.info("Updating contact {} as a result of the android contact synchronization", contactModel.identity)
        if (contactModel.data?.publicKey?.contentEquals(publicKey) != true) {
            logger.warn("Cannot update contact due to different public keys")
            if (contactModel.data?.verificationLevel == VerificationLevel.SERVER_VERIFIED) {
                logger.warn("Downgrading verification level of contact {} because of public key mismatch", contactModel.identity)
                contactModel.setVerificationLevelFromLocal(VerificationLevel.UNVERIFIED)
            }
            return
        }

        contactModel.setAndroidContactLookupKey(androidContact.getAndroidContactLookupInfo())
        val contactName = getAndroidContactNameUseCase.call(androidContact)
        if (contactName != null) {
            contactModel.setNameFromLocal(
                firstName = contactName.firstName,
                lastName = contactName.lastName,
            )
        } else {
            logger.warn("Could get contact name for identity {}", contactModel.identity)
        }
        if (contactModel.data?.verificationLevel == VerificationLevel.UNVERIFIED) {
            contactModel.setVerificationLevelFromLocal(VerificationLevel.SERVER_VERIFIED)
        }
    }

    private fun isUnchangedPhoneNumbersHash(phoneNumbersHash: Int): Boolean =
        preferenceService.getPhoneNumberSyncHashCode() == phoneNumbersHash

    private fun isUnchangedEmailAddressesHash(emailAddressesHash: Int): Boolean =
        preferenceService.getEmailSyncHashCode() == emailAddressesHash

    private fun isGraceTimeReached(): Boolean =
        timeProvider.get() - (preferenceService.getTimeOfLastContactSync() ?: Instant.EPOCH) >= 23.hours

    /**
     * Computes an order-independent hash of the distinct phone numbers across all raw contacts in this set, regardless of which
     * AndroidContact/RawContact each number belongs to. Numbers are deduplicated: moving a number between raw contacts, or having it appear on
     * multiple raw contacts, does not change the hash.
     */
    private fun Set<AndroidContact>.getPhoneNumbersHash(): Int = asSequence()
        .flatMap(AndroidContact::rawContacts)
        .flatMap(RawContact::phoneNumbers)
        .map(PhoneNumber::phoneNumber)
        .toSet()
        .hashCode()

    /**
     * Computes an order-independent hash of the distinct email addresses across all raw contacts in this set, regardless of which
     * AndroidContact/RawContact each email address belongs to. Email addresses are deduplicated: moving an email address between raw contacts, or
     * having it appear on multiple raw contacts, does not change the hash.
     */
    private fun Set<AndroidContact>.getEmailAddressesHash(): Int = asSequence()
        .flatMap(AndroidContact::rawContacts)
        .flatMap(RawContact::emailAddresses)
        .map(EmailAddress::emailAddress)
        .toSet()
        .hashCode()

    private fun AndroidContact.getAndroidContactLookupInfo() = AndroidContactLookupInfo(
        lookupKey = lookupInfo.lookupKey.key,
        contactId = lookupInfo.contactId.id.toLong(),
    )

    companion object {
        private val mutex = Mutex()

        // TODO(ANDR-4436): The use case should not manage the lock state itself. We should introduce a dedicated handler for this (e.g. a
        //  ContactSyncLockProvider).
        @JvmStatic
        val isRunning: Boolean
            get() = mutex.isLocked
    }
}
