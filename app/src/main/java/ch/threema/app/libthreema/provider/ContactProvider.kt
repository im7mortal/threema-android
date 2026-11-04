package ch.threema.app.libthreema.provider

import ch.threema.app.libthreema.conversion.toContactModelData
import ch.threema.app.libthreema.conversion.toLibthreemaContact
import ch.threema.app.libthreema.extensions.getContactDefinedLibthreemaProfilePicture
import ch.threema.app.libthreema.extensions.getUserDefinedLibthreemaProfilePicture
import ch.threema.app.services.BlockedIdentitiesService
import ch.threema.app.services.FileService
import ch.threema.base.utils.getThreemaLogger
import ch.threema.base.utils.withTimeMeasurement
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.libthreema.Contact as LibthreemaContact
import ch.threema.libthreema.ContactProvider as LibthreemaContactProvider
import ch.threema.libthreema.ContactUpdate as LibthreemaContactUpdate
import ch.threema.libthreema.ContactWithProfilePictures as LibthreemaContactWithProfilePictures
import ch.threema.libthreema.ProfilePicture as LibthreemaProfilePicture
import ch.threema.libthreema.ProviderException as LibthreemaProviderException
import ch.threema.libthreema.ThreemaId as LibthreemaIdentity

private val logger = getThreemaLogger("ContactProvider")

/**
 * The contact provider is used to provide app data to libthreema. Additionally, libthreema can write app data via this provider.
 */
class ContactProvider(
    private val fileService: FileService,
    private val contactModelRepository: ContactModelRepository,
    private val blockedIdentitiesService: BlockedIdentitiesService,
    override val guaranteedIdentityProvider: GuaranteedIdentityProvider,
) : LibthreemaContactProvider, IdentityAwareProvider() {

    override fun isExplicitlyBlocked(identity: LibthreemaIdentity): Boolean =
        withTimeMeasurement(logger, "isExplicitlyBlocked") {
            requireIdentityIsNotUserIdentity(identity)
            blockedIdentitiesService.isBlocked(identity)
        }

    override fun has(identity: LibthreemaIdentity): Boolean =
        withTimeMeasurement(logger, "has") {
            requireIdentityIsNotUserIdentity(identity)

            contactModelRepository.existsByIdentity(identity)
        }

    override fun countExisting(identities: List<LibthreemaIdentity>): UInt =
        withTimeMeasurement(logger, "countExisting", identities.size) {
            requireUserIdentityIsNotIn(identities)
            contactModelRepository.countExisting(identities.toSet())
        }

    override fun getIdentities(): Set<LibthreemaIdentity> =
        withTimeMeasurement(logger, "getIdentities") {
            contactModelRepository
                .getAllContactIdentities()
                .toSet()
        }

    override fun get(identity: LibthreemaIdentity): LibthreemaContact? =
        withTimeMeasurement(logger, "get") {
            requireIdentityIsNotUserIdentity(identity)

            return contactModelRepository
                .getByIdentity(identity)
                ?.data
                ?.toLibthreemaContact()
        }

    override fun getWithProfilePictures(identity: LibthreemaIdentity): LibthreemaContactWithProfilePictures? =
        withTimeMeasurement(logger, "getWithProfilePictures") {
            requireIdentityIsNotUserIdentity(identity)

            val contact = contactModelRepository
                .getByIdentity(identity)
                ?.data
                ?.toLibthreemaContact()
                ?: return null

            LibthreemaContactWithProfilePictures(
                contact = contact,
                contactDefinedProfilePicture = fileService.getContactDefinedLibthreemaProfilePicture(identity),
                userDefinedProfilePicture = fileService.getUserDefinedLibthreemaProfilePicture(identity),
            )
        }

    override fun add(contacts: List<LibthreemaContact>): Unit =
        withTimeMeasurement(logger, "add", contacts.size) {
            val identities = contacts.map(LibthreemaContact::identity).toSet()
            requireUserIdentityIsNotIn(identities)

            val alreadyExisting = contactModelRepository.countExisting(identities)
            if (alreadyExisting > 0u) {
                throw LibthreemaProviderException.ContractViolation(
                    "Can not add ${contacts.size} contact(s) as $alreadyExisting of them already exist(e)",
                )
            }

            val contactModelData = contacts
                .map { contact -> contact.toContactModelData() }
                .toSet()
            contactModelRepository.persistNewContacts(contactModelData)
        }

    override fun addWithProfilePictures(contacts: List<LibthreemaContactWithProfilePictures>): Unit =
        withTimeMeasurement(logger, "addWithProfilePictures", contacts.size) {
            val identities = contacts.map { (contact, _, _) ->
                contact.identity
            }.toSet()
            requireUserIdentityIsNotIn(identities)

            val alreadyExisting = contactModelRepository.countExisting(identities)
            if (alreadyExisting > 0u) {
                throw LibthreemaProviderException.ContractViolation(
                    "Can not add ${contacts.size} contact(s) with profile picture(s) as $alreadyExisting of them already exist(s)",
                )
            }

            // Add the contacts to the database
            val contactModelData = contacts
                .map { (contact, _, _) ->
                    contact.toContactModelData()
                }
                .toSet()
            contactModelRepository.persistNewContacts(contactModelData)

            // Write the profile pictures to the files
            contacts.forEach { (contact, contactDefinedProfilePicture, userDefinedProfilePicture) ->
                val identity = contact.identity

                if (contactDefinedProfilePicture != null) {
                    fileService.writeContactDefinedProfilePicture(
                        identity,
                        contactDefinedProfilePicture.data,
                    )
                }

                if (userDefinedProfilePicture != null) {
                    fileService.writeUserDefinedProfilePicture(
                        identity,
                        userDefinedProfilePicture.data,
                    )
                }
            }
        }

    override fun update(updates: List<LibthreemaContactUpdate>): Unit =
        withTimeMeasurement(logger, "update", updates.size) {
            val identities = updates.map(LibthreemaContactUpdate::identity).toSet()
            requireUserIdentityIsNotIn(identities)

            val alreadyExisting = contactModelRepository.countExisting(identities)
            if (alreadyExisting != identities.size.toUInt()) {
                throw LibthreemaProviderException.ContractViolation("Cannot update ${identities.size} contact(s) when only $alreadyExisting exist(s)")
            }

            throw NotImplementedError("Currently this is not needed")
        }

    override fun setContactDefinedProfilePicture(identity: LibthreemaIdentity, profilePicture: LibthreemaProfilePicture?): Unit =
        withTimeMeasurement(logger, "setContactDefinedProfilePicture") {
            requireIdentityIsNotUserIdentity(identity)

            if (!contactModelRepository.existsByIdentity(identity)) {
                throw LibthreemaProviderException.ContractViolation("Contact does not exist")
            }

            if (profilePicture != null) {
                fileService.writeContactDefinedProfilePicture(identity, profilePicture.data)
            } else {
                fileService.removeContactDefinedProfilePicture(identity)
            }
        }

    override fun setUserDefinedProfilePicture(identity: LibthreemaIdentity, profilePicture: LibthreemaProfilePicture?): Unit =
        withTimeMeasurement(logger, "setUserDefinedProfilePicture") {
            requireIdentityIsNotUserIdentity(identity)

            if (!contactModelRepository.existsByIdentity(identity)) {
                throw LibthreemaProviderException.ContractViolation("Contact does not exist")
            }

            if (profilePicture != null) {
                fileService.writeUserDefinedProfilePicture(identity, profilePicture.data)
            } else {
                fileService.removeUserDefinedProfilePicture(identity)
            }
        }
}
