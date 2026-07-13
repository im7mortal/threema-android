package ch.threema.data.models

import ch.threema.app.utils.ContactUtil
import ch.threema.base.crypto.NaCl
import ch.threema.common.isNotNullOrBlank
import ch.threema.common.plus
import ch.threema.data.datatypes.AndroidContactLookupInfo
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ContactNameFormat
import ch.threema.data.datatypes.ContactNotificationTriggerPolicyOverride
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.datatypes.IdColor
import ch.threema.data.models.ContactModelData.Companion.DISPLAY_NAME_INVALID_CONTACT
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.BasicContact
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.domain.types.IdentityString
import java.math.BigInteger
import java.time.Instant
import kotlin.time.Duration

/**
 * Immutable contact model data.
 *
 * TODO(ANDR-2998): Notification sound policy override
 */
data class ContactModelData(
    /** The contact identity string. Must be 8 characters long. */
    @JvmField val identity: IdentityString,
    /** The 32-byte public key of the contact. */
    @JvmField val publicKey: ByteArray,
    /** Timestamp when this contact was added to the contact list. */
    @JvmField val createdAt: Instant,
    /** Timestamp when this contact was last updated. Also known as _last update flag_. */
    @JvmField val lastUpdateAt: Instant?,
    /** First name. */
    @JvmField val firstName: String,
    /** Last name. */
    @JvmField val lastName: String,
    /** Public nickname. */
    @JvmField val nickname: String?,
    /** Id color. */
    val idColor: IdColor = IdColor.ofIdentity(identity),
    /** Verification level. */
    @JvmField val verificationLevel: VerificationLevel,
    /** Threema Work verification level. */
    @JvmField val workVerificationLevel: WorkVerificationLevel,
    /** Identity type (regular / work). */
    @JvmField val identityType: IdentityType,
    /** Acquaintance level (direct / group). */
    @JvmField val acquaintanceLevel: AcquaintanceLevel,
    /** Activity state (active / inactive / invalid). */
    @JvmField val activityState: IdentityState,
    /** Contact sync state. */
    @JvmField val syncState: ContactSyncState,
    /** Feature mask. */
    val featureMask: ULong,
    /** Read receipt policy. */
    @JvmField val readReceiptPolicy: ReadReceiptPolicy,
    /** Typing indicator policy. */
    @JvmField val typingIndicatorPolicy: TypingIndicatorPolicy,
    /** The conversation visibility of the 1:1 chat with this contact. */
    @JvmField val conversationVisibility: ConversationVisibility,
    /** Android contact lookup key. */
    @JvmField val androidContactLookupInfo: AndroidContactLookupInfo?,
    /**
     * Local avatar expiration date.
     *
     * For gateway contacts, this is used to determine when to refresh the avatar from the server.
     *
     * For contacts linked to an Android system contact, this is used to determine when to refresh
     * the avatar from the system address book.
     *
     * For other contacts, this is always set to null.
     */
    @JvmField val localAvatarExpires: Instant?,
    /**
     * Whether this contact has been restored from backup.
     */
    @JvmField val isRestored: Boolean,
    /**
     * BlobId of the latest profile picture that was sent to this contact.
     */
    @JvmField val profilePictureBlobId: ByteArray?,
    @JvmField val jobTitle: String?,
    @JvmField val department: String?,
    @JvmField val notificationTriggerPolicyOverride: ContactNotificationTriggerPolicyOverride?,
    /**
     *  In work builds, work contacts can have an optional [AvailabilityStatus].
     *
     *  If not a work build or it was never set, it will be [AvailabilityStatus.None].
     */
    @JvmField val availabilityStatus: AvailabilityStatus,
    @JvmField val workLastFullSyncAt: Instant?,
) {
    companion object {

        const val DISPLAY_NAME_INVALID_CONTACT = "invalid contact"

        /**
         * Factory function using only Java-compatible types.
         *
         * @throws IllegalArgumentException the feature mask is negative or more than 64 bits,
         * or the public key is not [NaCl.PUBLIC_KEY_BYTES] long.
         */
        @JvmStatic
        fun javaCreate(
            identity: IdentityString,
            publicKey: ByteArray,
            createdAt: Instant,
            lastUpdateAt: Instant?,
            firstName: String,
            lastName: String,
            nickname: String?,
            idColor: IdColor,
            verificationLevel: VerificationLevel,
            workVerificationLevel: WorkVerificationLevel,
            identityType: IdentityType,
            acquaintanceLevel: AcquaintanceLevel,
            activityState: IdentityState,
            featureMask: BigInteger,
            syncState: ContactSyncState,
            readReceiptPolicy: ReadReceiptPolicy,
            typingIndicatorPolicy: TypingIndicatorPolicy,
            conversationVisibility: ConversationVisibility,
            androidContactLookupInfo: AndroidContactLookupInfo?,
            localAvatarExpires: Instant?,
            isRestored: Boolean,
            profilePictureBlobId: ByteArray?,
            jobTitle: String?,
            department: String?,
            notificationTriggerPolicyOverride: ContactNotificationTriggerPolicyOverride?,
            availabilityStatus: AvailabilityStatus,
            workLastFullSyncAt: Instant?,
        ): ContactModelData {
            require(featureMask.signum() >= 0 && featureMask.bitLength() <= 64) { "featureMask must be between 0 and 2^64" }
            require(publicKey.size == NaCl.PUBLIC_KEY_BYTES) { "public key must be ${NaCl.PUBLIC_KEY_BYTES} long" }
            return ContactModelData(
                identity = identity,
                publicKey = publicKey,
                createdAt = createdAt,
                lastUpdateAt = lastUpdateAt,
                firstName = firstName,
                lastName = lastName,
                nickname = nickname,
                idColor = idColor,
                verificationLevel = verificationLevel,
                workVerificationLevel = workVerificationLevel,
                identityType = identityType,
                acquaintanceLevel = acquaintanceLevel,
                activityState = activityState,
                syncState = syncState,
                featureMask = featureMask.toLong().toULong(),
                readReceiptPolicy = readReceiptPolicy,
                typingIndicatorPolicy = typingIndicatorPolicy,
                conversationVisibility = conversationVisibility,
                androidContactLookupInfo = androidContactLookupInfo,
                localAvatarExpires = localAvatarExpires,
                isRestored = isRestored,
                profilePictureBlobId = profilePictureBlobId,
                jobTitle = jobTitle,
                department = department,
                notificationTriggerPolicyOverride = notificationTriggerPolicyOverride,
                availabilityStatus = availabilityStatus,
                workLastFullSyncAt = workLastFullSyncAt,
            )
        }
    }

    /**
     * Return the [featureMask] as positive [Long].
     *
     * Throws [IllegalArgumentException] if value does not fit in a [Long].
     */
    fun featureMaskLong(): Long {
        val long = featureMask.toLong()
        if (long < 0) {
            throw IllegalArgumentException("Feature mask does not fit in a signed long")
        }
        return long
    }

    fun showIdentityTypeBadge(isWorkBuild: Boolean): Boolean =
        if (isWorkBuild) {
            identityType == IdentityType.REGULAR && !ContactUtil.isEchoEchoOrGatewayContact(identity)
        } else {
            identityType == IdentityType.WORK
        }

    /**
     * Return the display name for this contact.
     *
     * Priority:
     * 1. First- and/or last-name also depending on [contactNameFormat]
     * 2. Nickname with or without the `~` prefix depending on [nicknameHasPrefix]
     * 3. Identity
     * 4. Fallback to [DISPLAY_NAME_INVALID_CONTACT]
     */
    fun getDisplayName(
        contactNameFormat: ContactNameFormat,
        nicknameHasPrefix: Boolean = true,
    ): String {
        val hasFirstName = firstName.isNotBlank()
        val hasLastName = lastName.isNotBlank()
        val hasNickname = !nickname.isNullOrBlank() && nickname.trim() != identity
        val hasIdentity = identity.isNotBlank()
        if (hasFirstName && hasLastName) {
            return when (contactNameFormat) {
                ContactNameFormat.FIRSTNAME_LASTNAME -> "${firstName.trim()} ${lastName.trim()}"
                ContactNameFormat.LASTNAME_FIRSTNAME -> "${lastName.trim()} ${firstName.trim()}"
            }
        }
        return if (hasFirstName) {
            firstName.trim()
        } else if (hasLastName) {
            lastName.trim()
        } else if (hasNickname) {
            if (nicknameHasPrefix) "~${nickname.trim()}" else nickname.trim()
        } else if (hasIdentity) {
            identity
        } else {
            DISPLAY_NAME_INVALID_CONTACT
        }
    }

    fun getShortName(): String = when {
        firstName.isNotBlank() -> firstName
        lastName.isNotBlank() -> lastName
        nickname.isNotNullOrBlank() && nickname != identity -> "~$nickname"
        else -> identity
    }

    /**
     * Return whether this contact is linked to an Android contact.
     */
    fun isLinkedToAndroidContact(): Boolean = this.androidContactLookupInfo != null

    /**
     * Check if the avatar expires within the given [tolerance]. If no [localAvatarExpires] is set, the avatar is also
     * considered as expired.
     */
    @JvmOverloads
    fun isAvatarExpired(now: Instant = Instant.now(), tolerance: Duration = Duration.ZERO): Boolean =
        localAvatarExpires?.isBefore((now + tolerance)) ?: true

    /**
     * Check if the contact is a gateway contact.
     */
    fun isGatewayContact(): Boolean = ContactUtil.isGatewayContact(identity)

    /**
     * Get the contact model data as basic contact.
     */
    fun toBasicContact(): BasicContact = BasicContact(
        identity = identity,
        publicKey = publicKey,
        featureMask = featureMask,
        identityState = activityState,
        identityType = identityType,
        verificationLevel = verificationLevel,
        workVerificationLevel = workVerificationLevel,
        firstName = firstName,
        lastName = lastName,
        jobTitle = jobTitle,
        department = department,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContactModelData

        if (identity != other.identity) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (createdAt != other.createdAt) return false
        if (lastUpdateAt != other.lastUpdateAt) return false
        if (firstName != other.firstName) return false
        if (lastName != other.lastName) return false
        if (nickname != other.nickname) return false
        if (idColor != other.idColor) return false
        if (verificationLevel != other.verificationLevel) return false
        if (workVerificationLevel != other.workVerificationLevel) return false
        if (identityType != other.identityType) return false
        if (acquaintanceLevel != other.acquaintanceLevel) return false
        if (activityState != other.activityState) return false
        if (syncState != other.syncState) return false
        if (featureMask != other.featureMask) return false
        if (readReceiptPolicy != other.readReceiptPolicy) return false
        if (typingIndicatorPolicy != other.typingIndicatorPolicy) return false
        if (conversationVisibility != other.conversationVisibility) return false
        if (androidContactLookupInfo != other.androidContactLookupInfo) return false
        if (localAvatarExpires != other.localAvatarExpires) return false
        if (isRestored != other.isRestored) return false
        if (profilePictureBlobId != null) {
            if (other.profilePictureBlobId == null) return false
            if (!profilePictureBlobId.contentEquals(other.profilePictureBlobId)) return false
        } else if (other.profilePictureBlobId != null) {
            return false
        }
        if (jobTitle != other.jobTitle) return false
        if (department != other.department) return false
        if (notificationTriggerPolicyOverride != other.notificationTriggerPolicyOverride) return false
        if (availabilityStatus != other.availabilityStatus) return false
        if (workLastFullSyncAt != other.workLastFullSyncAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastUpdateAt.hashCode()
        result = 31 * result + firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + idColor.hashCode()
        result = 31 * result + verificationLevel.hashCode()
        result = 31 * result + workVerificationLevel.hashCode()
        result = 31 * result + identityType.hashCode()
        result = 31 * result + acquaintanceLevel.hashCode()
        result = 31 * result + activityState.hashCode()
        result = 31 * result + syncState.hashCode()
        result = 31 * result + featureMask.hashCode()
        result = 31 * result + readReceiptPolicy.hashCode()
        result = 31 * result + typingIndicatorPolicy.hashCode()
        result = 31 * result + conversationVisibility.hashCode()
        result = 31 * result + androidContactLookupInfo.hashCode()
        result = 31 * result + localAvatarExpires.hashCode()
        result = 31 * result + isRestored.hashCode()
        result = 31 * result + profilePictureBlobId.contentHashCode()
        result = 31 * result + notificationTriggerPolicyOverride.hashCode()
        result = 31 * result + availabilityStatus.hashCode()
        result = 31 * result + workLastFullSyncAt.hashCode()
        return result
    }
}
