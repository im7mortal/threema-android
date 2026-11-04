package ch.threema.app.libthreema.conversion

import ch.threema.base.crypto.NaCl
import ch.threema.data.datatypes.AvailabilityStatus
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.datatypes.ConversationVisibility
import ch.threema.data.models.ContactModelData
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.ContactSyncState
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.IdentityType
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel
import ch.threema.libthreema.ActivityState as LibthreemaIdentityState
import ch.threema.libthreema.Contact as LibthreemaContact
import ch.threema.libthreema.ConversationCategory as LibthreemaConversationCategory
import ch.threema.libthreema.ConversationVisibility as LibthreemaConversationVisibility
import ch.threema.libthreema.IdentityType as LibthreemaIdentityType
import ch.threema.libthreema.SyncState as LibthreemaSyncState
import ch.threema.libthreema.VerificationLevel as LibthreemaVerificationLevel
import ch.threema.libthreema.WorkAvailabilityStatus as LibthreemaWorkAvailabilityStatus
import ch.threema.libthreema.WorkVerificationLevel as LibthreemaWorkVerificationLevel
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import testdata.TestData

class ContactConversionTest {
    private val baseContactModelData = ContactModelData(
        identity = TestData.Identities.OTHER_1.value,
        publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES) { index -> index.toByte() },
        createdAt = Instant.ofEpochMilli(42),
        lastUpdateAt = Instant.ofEpochMilli(43),
        firstName = "FirstName",
        lastName = "LastName",
        nickname = "Nickname",
        verificationLevel = VerificationLevel.FULLY_VERIFIED,
        workVerificationLevel = WorkVerificationLevel.NONE,
        identityType = IdentityType.REGULAR,
        acquaintanceLevel = AcquaintanceLevel.DIRECT,
        activityState = IdentityState.ACTIVE,
        syncState = ContactSyncState.INITIAL,
        featureMask = 0u,
        readReceiptPolicy = ReadReceiptPolicy.DEFAULT,
        typingIndicatorPolicy = TypingIndicatorPolicy.DEFAULT,
        conversationVisibility = ConversationVisibility.ARCHIVED,
        conversationCategory = ConversationCategory.PROTECTED,
        androidContactLookupInfo = null,
        localAvatarExpires = null,
        isRestored = false,
        profilePictureBlobId = null,
        jobTitle = null,
        department = null,
        notificationTriggerPolicyOverride = null,
        availabilityStatus = AvailabilityStatus.None,
        workLastFullSyncAt = null,
    )

    private val baseLibthreemaContact = LibthreemaContact(
        identity = TestData.Identities.OTHER_1.value,
        publicKey = ByteArray(NaCl.PUBLIC_KEY_BYTES) { index -> index.toByte() },
        createdAt = 42u,
        lastUpdateAt = 43u,
        firstName = "FirstName",
        lastName = "LastName",
        nickname = "Nickname",
        verificationLevel = LibthreemaVerificationLevel.FULLY_VERIFIED,
        workVerificationLevel = LibthreemaWorkVerificationLevel.NONE,
        identityType = LibthreemaIdentityType.REGULAR,
        acquaintanceLevel = ch.threema.libthreema.AcquaintanceLevel.DIRECT,
        activityState = LibthreemaIdentityState.ACTIVE,
        featureMask = 0u,
        syncState = LibthreemaSyncState.INITIAL,
        readReceiptPolicyOverride = null,
        typingIndicatorPolicyOverride = null,
        notificationTriggerPolicyOverride = null,
        conversationCategory = LibthreemaConversationCategory.PROTECTED,
        conversationVisibility = LibthreemaConversationVisibility.ARCHIVED,
        workLastFullSyncAt = null,
        workAvailabilityStatus = LibthreemaWorkAvailabilityStatus.None,
    )

    @Test
    fun `base contact model data can be converted to libthreema contact`() {
        val actualLibthreemaContact = baseContactModelData.toLibthreemaContact()

        // Because uniffi doesn't create an equality implementation of the contact data class, we compare the public key manually.
        assertContentEquals(baseLibthreemaContact.publicKey, actualLibthreemaContact.publicKey)
        // If the public keys are equal, we copy it to be able to check for equality without manually comparing every field.
        val actualLibthreemaContactWithSamePublicKey = actualLibthreemaContact.copy(
            publicKey = baseLibthreemaContact.publicKey,
        )
        assertEquals(baseLibthreemaContact, actualLibthreemaContactWithSamePublicKey)
    }

    @Test
    fun `base libthreema contact can be converted to base contact model data`() {
        val actualContactModelData = baseLibthreemaContact.toContactModelData()

        assertEquals(baseContactModelData, actualContactModelData)
    }
}
