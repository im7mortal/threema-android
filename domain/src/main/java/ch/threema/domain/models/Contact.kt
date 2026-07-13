package ch.threema.domain.models

import ch.threema.common.toHexString
import ch.threema.common.truncateUTF8String
import ch.threema.domain.types.IdentityString
import java.util.Objects
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CONTACT_NAME_MAX_LENGTH_BYTES = 256

/**
 * Base class for contacts.
 */
open class Contact(
    val identity: IdentityString,
    val publicKey: ByteArray,
    @JvmField var verificationLevel: VerificationLevel = VerificationLevel.UNVERIFIED,
) {
    var firstName: String? = null
        set(value) {
            field = value?.truncateUTF8String(CONTACT_NAME_MAX_LENGTH_BYTES)
        }
    var lastName: String? = null
        set(value) {
            field = value?.truncateUTF8String(CONTACT_NAME_MAX_LENGTH_BYTES)
        }

    val hasFirstOrLastName: Boolean
        get() = !firstName.isNullOrBlank() || !lastName.isNullOrBlank()

    override fun toString(): String = buildString {
        append(identity)
        append(" (")
        append(publicKey.toHexString())
        append(")")
        if (firstName != null || lastName != null) {
            append(": ")
            append(firstName)
            append(" ")
            append(lastName)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return identity == other.identity && publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int {
        var result = Objects.hash(identity)
        result = 31 * result + publicKey.contentHashCode()
        return result
    }
}

/**
 * This represents a contact with reduced properties. Note that this is mainly used for caching. A
 * basic contact may be a contact that is not present in the database. The existence of a
 * [BasicContact] does therefore not mean that it is a known contact.
 *
 * The [BasicContact] contains attributes that are usually fetched from the directory and/or work server and is therefore well suited for caching
 * those values.
 */
class BasicContact(
    identity: IdentityString,
    publicKey: ByteArray,
    val featureMask: ULong,
    val identityState: IdentityState,
    val identityType: IdentityType,
    verificationLevel: VerificationLevel,
    val workVerificationLevel: WorkVerificationLevel,
    firstName: String? = null,
    lastName: String? = null,
    val jobTitle: String? = null,
    val department: String? = null,
) : Contact(identity, publicKey, verificationLevel) {
    init {
        this.firstName = firstName
        this.lastName = lastName
    }

    companion object {
        @Deprecated("Do not use. This only exists for background compatibility in tests")
        @JvmStatic
        fun javaCreate(
            identity: IdentityString,
            publicKey: ByteArray,
            featureMask: Long,
            identityState: IdentityState,
            identityType: IdentityType,
            verificationLevel: VerificationLevel,
            workVerificationLevel: WorkVerificationLevel,
            jobTitle: String?,
            department: String?,
        ): BasicContact = BasicContact(
            identity = identity,
            publicKey = publicKey,
            featureMask = featureMask.toULong(),
            identityState = identityState,
            identityType = identityType,
            verificationLevel = verificationLevel,
            workVerificationLevel = workVerificationLevel,
            jobTitle = jobTitle,
            department = department,
        )
    }
}

@Serializable
enum class IdentityType {
    /**
     * A regular Threema identity.
     *
     * Note that it should be serialized with 'NORMAL' as some tasks persist it like this.
     */
    @SerialName("NORMAL")
    REGULAR,

    /**
     * An identity using Threema Work.
     */
    @SerialName("WORK")
    WORK,
}

/**
 * This represents the identity state. Note that the variants must not be renamed as they are stored
 * as string in the database.
 */
enum class IdentityState(val value: Int) {
    /**
     * Contact is active.
     */
    ACTIVE(0),

    /**
     * Contact is inactive.
     */
    INACTIVE(1),

    /**
     * Contact does not have a valid Threema-ID, or the ID was revoked.
     */
    INVALID(2),
}

enum class WorkVerificationLevel {
    NONE,

    /**
     * Contact is "work verified", i.e. has been added to the contact list in the management
     * cockpit. These contacts are symbolized by a blue verification level.
     */
    WORK_SUBSCRIPTION_VERIFIED,
}

enum class ContactSyncState {
    /**
     * The contact data has not been imported and has not been edited by the user either.
     */
    INITIAL,

    /**
     * The contact data has been imported (e.g. via a local address book and an identity link).
     * In this state, subsequent contact synchronisations must not alter the contact's data.
     */
    IMPORTED,

    /**
     * The contact data has been edited by the user.
     * In this state, subsequent contact synchronisations must not alter the contact's data.
     */
    CUSTOM,
}

enum class ReadReceiptPolicy {
    /**
     * Default behavior (as configured in the global settings).
     */
    DEFAULT,

    /**
     * Send read receipt when an unread message has been read.
     */
    SEND,

    /**
     * Don't send read receipts.
     */
    DONT_SEND,
}

enum class TypingIndicatorPolicy {
    /**
     * Default behavior (as configured in the global settings).
     */
    DEFAULT,

    /**
     * Send typing indicator when a message is being composed.
     */
    SEND,

    /**
     * Don't send typing indicators.
     */
    DONT_SEND,
}

@Serializable
enum class AcquaintanceLevel {
    /**
     * The contact was explicitly added by the user or a 1:1 conversation with the contact
     * has been initiated.
     */
    @SerialName("DIRECT")
    DIRECT,

    /**
     * This level covers the following cases:
     * - The contact is part of a group the user is also part of. The contact was not explicitly added and no 1:1 conversation has been initiated.
     * - The contact was part of a group the user was/is also part of before but either of them has since been removed from all common groups.
     * - The contact has been explicitly removed by the user.
     *
     * Note that it should be serialized with 'GROUP' as some tasks persist it like this.
     */
    @SerialName("GROUP")
    GROUP_OR_DELETED,
}
