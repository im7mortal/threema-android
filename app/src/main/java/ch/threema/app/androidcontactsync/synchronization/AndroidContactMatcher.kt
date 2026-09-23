package ch.threema.app.androidcontactsync.synchronization

import ch.threema.app.androidcontactsync.types.AndroidContact
import ch.threema.app.androidcontactsync.types.EmailAddress
import ch.threema.app.androidcontactsync.types.PhoneNumber
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.types.Identity
import ch.threema.logging.logAndReportError

private val logger = getThreemaLogger("AndroidContactMatcher")

/**
 * Matches android contacts to Threema identities based on phone numbers and email addresses.
 */
class AndroidContactMatcher(
    private val identityLookupClient: IdentityLookupClient,
) {
    /** The value that provided the match for an identity. */
    sealed class MatchSource {
        data class Phone(val phoneNumber: PhoneNumber) : MatchSource()
        data class Email(val emailAddress: EmailAddress) : MatchSource()
    }

    data class MatchedIdentity(
        val identity: Identity,
        val matchSource: MatchSource,
    )

    class AndroidContactMatch(
        val androidContactMatches: Map<AndroidContact, MatchedIdentity>,
        val identityToPublicKey: Map<Identity, ByteArray>,
    )

    /**
     * Matches the given android contacts to Threema identities via phone number and email address lookup.
     *
     * Only 1:1 matches are returned: an android contact is dropped if it matches zero or more than one identity, and an identity is dropped if it is
     * matched by more than one android contact.
     *
     * @throws Exception if the identity lookup api call fails
     */
    fun matchContacts(androidContacts: Set<AndroidContact>): AndroidContactMatch {
        logger.info("Trying to match {} android contacts to an identity", androidContacts.size)

        val identityLookup = identityLookupClient.lookupPhoneAndEmail(
            phoneNumbers = androidContacts.getAllPhoneNumbers(),
            emailAddresses = androidContacts.getAllEmailAddresses(),
        )

        // We associate every android contact with the identities it matches, together with the phone number or email address that provided each
        // match. It is possible that an android contact maps to zero identities, to exactly one or to more than one.
        val androidContactToMatches = androidContacts.associateWith { androidContact ->
            androidContact.findMatchingIdentities(identityLookup)
        }

        // Store all android contacts by identity, so we can later check how many contacts claim the same identity.
        val identityToAndroidContacts = mutableMapOf<Identity, MutableSet<AndroidContact>>()
        androidContactToMatches.forEach { (contact, matches) ->
            matches.keys.forEach { identity ->
                identityToAndroidContacts.getOrPut(identity) { mutableSetOf() }.add(contact)
            }
        }

        // We only keep contacts that match exactly one identity, and only if that identity is not also matched by another contact.
        val androidContactToMatchedIdentity = buildMap {
            var skippedAndroidContacts = 0
            androidContactToMatches.forEach { (androidContact, matches) ->
                val (identity, matchSource) = when (matches.size) {
                    0 -> return@forEach
                    1 -> matches.entries.first()
                    else -> {
                        skippedAndroidContacts++
                        return@forEach
                    }
                }

                val numberOfClaimingContacts = identityToAndroidContacts[identity]?.size ?: 0
                when (numberOfClaimingContacts) {
                    0 -> logger.logAndReportError("Identity is claimed by zero contacts despite being present in set")
                    1 -> put(androidContact, MatchedIdentity(identity, matchSource))
                    else -> logger.info("Identity {} is claimed by {} contacts", identity, numberOfClaimingContacts)
                }
            }
            if (skippedAndroidContacts > 0) {
                logger.info("Skipped {} android contact(s) that matched several identities", skippedAndroidContacts)
            }
        }

        logger.info("Matched {} out of {} android contacts to an identity", androidContactToMatchedIdentity.size, androidContacts.size)

        return AndroidContactMatch(
            androidContactMatches = androidContactToMatchedIdentity,
            identityToPublicKey = identityLookup.identityToPublicKey,
        )
    }

    private fun Set<AndroidContact>.getAllPhoneNumbers(): Set<PhoneNumber> =
        flatMap { androidContact -> androidContact.getAllPhoneNumbers() }
            .toSet()

    private fun Set<AndroidContact>.getAllEmailAddresses(): Set<EmailAddress> =
        flatMap { androidContact -> androidContact.getAllEmailAddresses() }
            .toSet()

    /**
     * Finds identities matching this android contact's phone numbers and email addresses, together with the value that provided each match. If
     * both a phone number and an email address match the same identity, the phone number is preferred.
     */
    private fun AndroidContact.findMatchingIdentities(identityLookup: IdentityLookupClient.IdentityLookup): Map<Identity, MatchSource> = buildMap {
        getAllEmailAddresses().forEach { emailAddress ->
            identityLookup.emailAddressToIdentity[emailAddress]?.let { identity ->
                put(identity, MatchSource.Email(emailAddress))
            }
        }
        getAllPhoneNumbers().forEach { phoneNumber ->
            identityLookup.phoneNumberToIdentity[phoneNumber]?.let { identity ->
                put(identity, MatchSource.Phone(phoneNumber)) // a phone match always takes precedence over an email match
            }
        }
    }
}
