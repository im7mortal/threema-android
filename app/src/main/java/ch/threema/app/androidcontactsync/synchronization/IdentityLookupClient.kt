package ch.threema.app.androidcontactsync.synchronization

import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.LocaleService
import ch.threema.app.stores.MatchTokenStore
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.protocol.api.APIConnector
import ch.threema.domain.stores.IdentityStore
import ch.threema.domain.types.Identity
import kotlin.collections.component1
import kotlin.collections.component2

private val logger = getThreemaLogger("IdentityLookupClient")

/**
 * Looks up identities on the server for a given set of phone numbers and email addresses.
 */
class IdentityLookupClient(
    private val apiConnector: APIConnector,
    private val identityStore: IdentityStore,
    private val localeService: LocaleService,
    private val preferenceService: PreferenceService,
) {
    /** Result of an identity lookup, keyed by the original phone number / email address. */
    class IdentityLookup(
        val phoneNumberToIdentity: Map<PhoneNumber, Identity>,
        val emailAddressToIdentity: Map<EmailAddress, Identity>,
        val identityToPublicKey: Map<Identity, ByteArray>,
    )

    /**
     * Queries the server for identities matching the given phone numbers and email addresses.
     * Entries with an ambiguous (non-unique) identity match are dropped and logged.
     *
     * @throws Exception if the api call fails
     */
    fun lookupPhoneAndEmail(
        phoneNumbers: Set<PhoneNumber>,
        emailAddresses: Set<EmailAddress>,
    ): IdentityLookup {
        logger.info("Looking up {} phone numbers and {} email addresses", phoneNumbers.size, emailAddresses.size)

        val phoneToIdentities = mutableMapOf<String, MutableSet<Identity>>()
        val emailToIdentities = mutableMapOf<String, MutableSet<Identity>>()

        val identityToPublicKey = mutableMapOf<Identity, ByteArray>()

        apiConnector.matchIdentities(
            emailAddresses.map(EmailAddress::emailAddress).toSet(),
            phoneNumbers.map(PhoneNumber::phoneNumber).toSet(),
            localeService.countryIsoCode,
            false,
            identityStore,
            MatchTokenStore(preferenceService),
        ).forEach { (identityString, result) ->
            val identity = Identity(identityString)

            result.phoneNumber?.let { phoneNumber ->
                phoneToIdentities.getOrPut(phoneNumber) { mutableSetOf() }.add(identity)
            }

            result.emailAddress?.let { emailAddress ->
                emailToIdentities.getOrPut(emailAddress) { mutableSetOf() }.add(identity)
            }

            identityToPublicKey[identity]?.let { publicKey ->
                check(publicKey.contentEquals(result.publicKey)) {
                    "Api returned different public keys for the same identity"
                }
            }
            identityToPublicKey[identity] = result.publicKey
        }

        val phoneToIdentity = resolveUniqueMatches(phoneToIdentities, ::PhoneNumber, "phone number")
        val emailToIdentity = resolveUniqueMatches(emailToIdentities, ::EmailAddress, "email address")

        logger.info("Received {} phone and {} email matches", phoneToIdentity.size, emailToIdentity.size)

        return IdentityLookup(
            phoneNumberToIdentity = phoneToIdentity,
            emailAddressToIdentity = emailToIdentity,
            identityToPublicKey = identityToPublicKey,
        )
    }

    private fun <T> resolveUniqueMatches(
        rawMatches: Map<String, MutableSet<Identity>>,
        keyFactory: (String) -> T,
        label: String,
    ): Map<T, Identity> = buildMap {
        rawMatches.forEach { (rawKey, identities) ->
            when (identities.size) {
                1 -> put(keyFactory(rawKey), identities.first())
                else -> logger.warn("Received {} identities for the same {}", identities.size, label)
            }
        }
    }
}
