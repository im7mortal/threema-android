package ch.threema.data.repositories

import android.database.sqlite.SQLiteException
import ch.threema.app.BuildConfig
import ch.threema.app.eventbus.GlobalEventBuses
import ch.threema.app.eventbus.GlobalEventFlows
import ch.threema.app.eventbus.events.ContactEvent
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.services.ContactService
import ch.threema.app.tasks.ReflectContactSyncCreateTask
import ch.threema.base.SessionScoped
import ch.threema.base.ThreemaException
import ch.threema.base.crypto.NaCl
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.getThreemaLogger
import ch.threema.common.DispatcherProvider
import ch.threema.data.IdentityProvider
import ch.threema.data.ModelTypeCache
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.PredefinedContact
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.models.ContactModelDataFactory
import ch.threema.data.storage.DatabaseBackend
import ch.threema.data.storage.DatabaseException
import ch.threema.data.storage.DbContact
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.protocol.csp.ProtocolDefines
import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.domain.taskmanager.TransactionScope
import ch.threema.domain.types.Identity
import ch.threema.domain.types.IdentityString
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("data.ContactModelRepository")

@SessionScoped
class ContactModelRepository(
    // Note: Synchronize access
    private val cache: ModelTypeCache<String, ContactModel>,
    private val databaseBackend: DatabaseBackend,
    private val identityProvider: IdentityProvider,
    private val multiDeviceManager: MultiDeviceManager,
    private val taskManager: TaskManager,
    private val nonceFactory: NonceFactory,
    private val globalEventBuses: GlobalEventBuses,
    private val globalEventFlows: GlobalEventFlows,
    dispatcherProvider: DispatcherProvider,
) : KoinComponent {
    private val coroutineScope = CoroutineScope(dispatcherProvider.worker)

    /**
     *  We have to make the bridge over to the old ContactService in order
     *  to keep the new and old caches both correct.
     *
     *  TODO(ANDR-4361): Remove this
     */
    private val deprecatedContactService: ContactService by inject()

    private object ContactModelRepositoryToken : RepositoryToken

    init {
        coroutineScope.launch {
            globalEventFlows.contacts
                .filterIsInstance<ContactEvent.ContactUpdated>()
                .collect { event ->
                    synchronized(this@ContactModelRepository) {
                        cache.get(event.identity.value)?.refreshFromDb(ContactModelRepositoryToken)
                    }
                }
        }
    }

    /**
     * Create a new contact from local. This also reflects the contact if MD is active.
     *
     * @throws ContactReflectException if reflecting the contact failed
     * @throws ContactStoreException if inserting the contact in the database failed
     */
    suspend fun createFromLocal(contactModelData: ContactModelData): ContactModel {
        requireValidContact(contactModelData)

        val createContactLocally: suspend () -> ContactModel = {
            persistNewContact(contactModelData)
        }

        return if (multiDeviceManager.isMultiDeviceActive) {
            try {
                taskManager.schedule(
                    task = ReflectContactSyncCreateTask(
                        contactModelData = contactModelData,
                        contactModelRepository = this,
                        nonceFactory = nonceFactory,
                        createLocally = createContactLocally,
                    ),
                ).await()
            } catch (e: TransactionScope.TransactionException) {
                logger.error("Could not reflect the contact")
                throw ContactReflectException(e)
            }
        } else {
            createContactLocally()
        }
    }

    /**
     * Create a new contact from remote. This also reflects the contact if MD is active.
     *
     * @throws ContactReflectException if reflecting the contact failed
     * @throws ContactStoreException if inserting the contact in the database failed
     */
    suspend fun createFromRemote(
        contactModelData: ContactModelData,
        handle: ActiveTaskCodec,
    ): ContactModel {
        requireValidContact(contactModelData)

        val createContactLocally: suspend () -> ContactModel = {
            persistNewContact(contactModelData)
        }

        return if (multiDeviceManager.isMultiDeviceActive) {
            try {
                ReflectContactSyncCreateTask(
                    contactModelData = contactModelData,
                    contactModelRepository = this,
                    nonceFactory = nonceFactory,
                    createLocally = createContactLocally,
                ).invoke(handle)
            } catch (e: TransactionScope.TransactionException) {
                logger.error("Could not reflect the contact", e)
                throw ContactReflectException(e)
            }
        } else {
            createContactLocally()
        }
    }

    /**
     * Create a new group contact. Note that this does *not* reflect the changes.
     *
     * @throws InvalidContactException if the provided contact data is invalid
     * @throws UnexpectedContactException if the provided contact has [AcquaintanceLevel.DIRECT]
     * @throws ContactStoreException if inserting the contact into the database failed
     */
    fun persistGroupContactFromRemote(contactModelData: ContactModelData) {
        if (contactModelData.acquaintanceLevel == AcquaintanceLevel.DIRECT) {
            throw UnexpectedContactException("A contact with acquaintance level group was expected")
        }
        persistNewContact(contactModelData)
    }

    /**
     * Create a new contact from sync.
     *
     * @throws ContactStoreException if the contact could not be stored in the database
     */
    @Throws(ContactStoreException::class)
    fun createFromSync(contactModelData: ContactModelData): ContactModel =
        persistNewContact(contactModelData)

    /**
     * Persist a new contact. Note that this does not reflect the contact and should therefore be used with care.
     *
     * @throws ContactStoreException if the contact could not be stored in the database
     */
    @Throws(ContactStoreException::class)
    fun persistNewContact(contactModelData: ContactModelData): ContactModel {
        val contactModel = synchronized(this) {
            try {
                databaseBackend.createContact(ContactModelDataFactory.toDbType(contactModelData))
            } catch (e: SQLiteException) {
                throw ContactStoreException(e)
            } catch (e: DatabaseException) {
                throw ContactStoreException(e)
            } catch (e: IllegalArgumentException) {
                throw InvalidContactException(e = e)
            }

            // We call the getter here to populate the cache and to validate that the contact was stored
            getByIdentity(contactModelData.identity)
                ?: error("Contact must exist at this point")
        }

        globalEventBuses.contacts.emit(ContactEvent.NewContact(Identity(contactModelData.identity)))

        return contactModel
    }

    /**
     * Persist new contacts. Note that this does not reflect the contacts and should therefore be used with care.
     *
     * Note that contacts are written in a transaction to the database meaning if one contact fails to be added, none of them will be added.
     *
     * @throws ContactStoreException if the contacts could not be stored in the database
     */
    @Throws(ContactStoreException::class)
    fun persistNewContacts(contactModelData: Set<ContactModelData>) {
        val identities = contactModelData
            .map(ContactModelData::identity)
        synchronized(this) {
            try {
                databaseBackend.createContact(*contactModelData.map(ContactModelDataFactory::toDbType).toTypedArray())
            } catch (e: SQLiteException) {
                throw ContactStoreException(e)
            } catch (e: DatabaseException) {
                throw ContactStoreException(e)
            } catch (e: IllegalArgumentException) {
                throw InvalidContactException(e = e)
            }

            // We call the getter here to populate the cache
            getByIdentities(identities.toSet())
        }

        identities.forEach { identity ->
            globalEventBuses.contacts.emit(ContactEvent.NewContact(Identity(identity)))
        }
    }

    @Throws(InvalidContactException::class)
    private fun requireValidContact(contactModelData: ContactModelData) {
        if (contactModelData.identity.length != ProtocolDefines.IDENTITY_LEN) {
            throw InvalidContactException("Invalid identity: ${contactModelData.identity}")
        }
        if (contactModelData.publicKey.size != NaCl.PUBLIC_KEY_BYTES) {
            throw InvalidContactException("Invalid public key size (${contactModelData.publicKey.size}) for identity ${contactModelData.identity}")
        }
        PredefinedContact.getPredefinedContact(contactModelData.identity)?.let { predefinedContact ->
            if (!predefinedContact.publicKey.contentEquals(contactModelData.publicKey)) {
                throw InvalidContactException("Invalid public key for predefined contact with identity ${contactModelData.identity}")
            }
            if (contactModelData.verificationLevel != VerificationLevel.FULLY_VERIFIED) {
                throw InvalidContactException(
                    "Invalid verification level for predefined contact with identity ${contactModelData.identity}: ${contactModelData.verificationLevel}",
                )
            }
        }
    }

    /**
     * Return the contact model for the specified identity.
     */
    @Synchronized
    fun getByIdentity(identity: IdentityString): ContactModel? = cache.getOrCreate(identity) {
        databaseBackend.getContactByIdentity(identity)?.toModel()
    }

    @Synchronized
    fun getByIdentity(identity: Identity): ContactModel? = getByIdentity(identity.value)

    /**
     * Tries to read the requested contact models from cache first, and adds missing models from database (if existing). If one or all identit(y/ies)
     * could not be found, there will be no error. In this case the result list will just miss these models.
     *
     * **Order:** The result list guarantees to be in the same order as the given set of identities
     *
     * **Own user:** Requesting the own users identity will never yield a result for this identity
     */
    @Synchronized
    fun getByIdentities(identities: Set<IdentityString>): List<ContactModel> {
        // Store results in a map to preserve the input order
        val resultsMap: MutableMap<IdentityString, ContactModel?> = identities.associateWithTo(mutableMapOf()) { null }
        val cacheMisses = mutableSetOf<IdentityString>()
        // Try to find all in cache
        for (identity in identities) {
            val cachedContactModel: ContactModel? = cache[identity]
            if (cachedContactModel != null) {
                resultsMap[identity] = cachedContactModel
            } else {
                cacheMisses.add(identity)
            }
        }
        // Happy case: All models found in cache
        if (cacheMisses.isEmpty()) {
            return resultsMap.values.filterNotNull()
        }
        // Search all missing identities in database and add found models to the model cache
        databaseBackend.getContactsByIdentities(identities = cacheMisses)
            .map { dbContact -> dbContact.toModel() }
            .forEach { contactModel ->
                cache.putIfAbsent(contactModel.identity, contactModel)
                resultsMap[contactModel.identity] = contactModel
            }
        return resultsMap.values.filterNotNull()
    }

    /**
     *  Returns `true` if all [identities] exist and have the given [acquaintanceLevel].
     */
    @Synchronized
    fun allExistWithAcquaintanceLevel(identities: Set<IdentityString>, acquaintanceLevel: AcquaintanceLevel): Boolean {
        if (identities.isEmpty()) {
            return true
        }
        // Try to find all in cache
        val cacheMisses = buildSet {
            for (identity in identities) {
                val cachedContactModelData = cache[identity]?.data
                if (cachedContactModelData == null) {
                    add(identity)
                    continue
                }
                if (cachedContactModelData.acquaintanceLevel != acquaintanceLevel) {
                    return false
                }
            }
        }
        // Happy case: All models found in cache
        if (cacheMisses.isEmpty()) {
            return true
        }
        // Check all missing identities in database
        return databaseBackend.allContactsExistWithAcquaintanceLevel(
            identities = cacheMisses,
            acquaintanceLevel = acquaintanceLevel,
        )
    }

    /**
     * Returns all contact models by loading them from the database.
     */
    @Synchronized
    fun getAll(): List<ContactModel> = databaseBackend.getAllContacts().mapNotNull { dbContact ->
        cache.getOrCreate(dbContact.identity) {
            dbContact.toModel()
        }
    }

    /**
     * Get all contact's identities. Note that this includes all contacts.
     */
    @Synchronized
    fun getAllContactIdentities(): Set<IdentityString> =
        databaseBackend.getAllContactIdentities()

    @Synchronized
    fun existsByIdentity(identity: IdentityString): Boolean =
        cache.exists(identity) || databaseBackend.contactExists(identity)

    @Synchronized
    fun existsByIdentity(identity: Identity): Boolean =
        existsByIdentity(identity.value)

    @Synchronized
    fun countExisting(identities: Set<IdentityString>): UInt {
        var count = 0U
        val cacheMisses = buildSet {
            identities.forEach { identity ->
                if (cache.exists(identity)) {
                    count++
                } else {
                    add(identity)
                }
            }
        }
        return databaseBackend.countExistingContacts(cacheMisses) + count
    }

    /**
     *  Note that this function does **not emit** any [ContactEvent.ContactUpdated] events.
     */
    @Synchronized
    fun persistWorkLastFullSyncAtTimestampUpdates(
        workLastFullSyncAtTimestamps: Map<IdentityString, Instant>,
    ) {
        databaseBackend.updateContactWorkLastFullSyncAtTimestamps(workLastFullSyncAtTimestamps)
        // Remove now outdated models from cache
        cache.remove(workLastFullSyncAtTimestamps.keys)
    }

    fun persistAvailabilityStatuses(availabilityStatuses: Map<IdentityString, AvailabilityStatus>) {
        if (!BuildConfig.AVAILABILITY_STATUS_ENABLED) {
            logger.error("Cannot persist availability statuses because this build does not support this feature")
            return
        }
        if (availabilityStatuses.isEmpty()) {
            return
        }
        val effectivelyChangedContacts = synchronized(this) {
            // Capture original contacts before change
            val originalContacts = getByIdentities(availabilityStatuses.keys)

            // Determine contacts that will change effectively
            val effectivelyChangedContacts = originalContacts
                .filter { originalContact ->
                    val originalAvailabilityStatus = originalContact.data?.availabilityStatus ?: return@filter false
                    val newAvailabilityStatus = availabilityStatuses[originalContact.identity] ?: return@filter false
                    newAvailabilityStatus != originalAvailabilityStatus
                }
                .map(ContactModel::identity)
                .toSet()

            // Change contacts in database
            databaseBackend.persistAvailabilityStatuses(availabilityStatuses)

            // Invalidate cache for changed contacts
            cache.remove(effectivelyChangedContacts)
            effectivelyChangedContacts.forEach(deprecatedContactService::invalidateCache)

            return@synchronized effectivelyChangedContacts
        }
        // Emit contacts updated events
        effectivelyChangedContacts.forEach { identityString ->
            globalEventBuses.contacts.emit(ContactEvent.ContactUpdated(Identity(identityString)))
        }
    }

    private fun DbContact.toModel(): ContactModel = ContactModel(
        identity = this.identity,
        data = ContactModelDataFactory.toDataType(this),
        databaseBackend = databaseBackend,
        multiDeviceManager = multiDeviceManager,
        taskManager = taskManager,
        identityProvider = identityProvider,
        globalEventBuses = globalEventBuses,
    )

    fun destroy() {
        coroutineScope.cancel()
    }
}

/**
 * This exception is thrown if the contact could not be added. This is either due to a failure
 * reflecting ([ContactReflectException]), storing ([ContactStoreException]), or validating
 * ([UnexpectedContactException]) the contact.
 */
sealed class ContactCreateException(msg: String, e: Exception? = null) : ThreemaException(msg, e)

/**
 * This exception is thrown if the contact could not be added because reflecting it failed.
 */
class ContactReflectException(e: TransactionScope.TransactionException) :
    ContactCreateException("Failed to reflect the contact", e)

/**
 * This exception is thrown if the contact could not be added. A corrupt database or a contact with
 * the same identity already exists.
 */
class ContactStoreException(e: Exception) :
    ContactCreateException("Failed to store the contact", e)

/**
 * This exception is thrown if the contact could not be added due to an invalid identity or public key.
 */
class InvalidContactException(message: String = "Invalid contact", e: Exception? = null) : ContactCreateException(message, e)

/**
 * This exception is thrown if an unexpected contact should have been added.
 */
class UnexpectedContactException(msg: String) : ContactCreateException(msg)
