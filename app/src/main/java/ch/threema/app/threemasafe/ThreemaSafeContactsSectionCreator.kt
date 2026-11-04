package ch.threema.app.threemasafe

import ch.threema.app.services.ContactService
import ch.threema.app.threemasafe.ThreemaSafeContactsSection.ThreemaSafeContact
import ch.threema.app.threemasafe.ThreemaSafeContactsSection.ThreemaSafeSendPolicy
import ch.threema.app.threemasafe.ThreemaSafeContactsSection.ThreemaSafeVerificationLevel
import ch.threema.common.Base64
import ch.threema.common.toNonNegativeTimestamp
import ch.threema.data.datatypes.ConversationCategory
import ch.threema.data.models.ContactModel
import ch.threema.data.models.ContactModelData
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.models.AcquaintanceLevel
import ch.threema.domain.models.IdentityState
import ch.threema.domain.models.ReadReceiptPolicy
import ch.threema.domain.models.TypingIndicatorPolicy
import ch.threema.domain.models.VerificationLevel
import ch.threema.domain.models.WorkVerificationLevel

class ThreemaSafeContactsSectionCreator(
    private val contactModelRepository: ContactModelRepository,
    private val contactService: ContactService,
) {
    fun create(): ThreemaSafeContactsSection {
        val removedIdentities = contactService.removedContacts
        val threemaSafeContacts = contactModelRepository
            .getAll()
            .filter { it.identity !in removedIdentities }
            .mapNotNull(ContactModel::data)
            .map(::toThreemaSafeContact)

        return ThreemaSafeContactsSection.create(threemaSafeContacts)
    }

    private fun toThreemaSafeContact(contactModelData: ContactModelData): ThreemaSafeContact =
        ThreemaSafeContact(
            identity = contactModelData.identity,
            publicKey = Base64.encode(contactModelData.publicKey),
            createdAt = contactModelData.createdAt.toEpochMilli().toNonNegativeTimestamp(),
            isRevoked = contactModelData.activityState == IdentityState.INVALID,
            verificationLevel = contactModelData.verificationLevel.toThreemaSafeVerificationLevel(),
            workVerified = contactModelData.workVerificationLevel == WorkVerificationLevel.WORK_SUBSCRIPTION_VERIFIED,
            hidden = contactModelData.isHidden(),
            firstname = contactModelData.firstName,
            lastname = contactModelData.lastName,
            nickname = contactModelData.nickname ?: "",
            lastUpdate = contactModelData.lastUpdateAt?.toEpochMilli()?.toNonNegativeTimestamp(),
            private = contactModelData.isPrivate(),
            readReceipts = contactModelData.readReceiptPolicy.toThreemaSafeSendPolicy(),
            typingIndicators = contactModelData.typingIndicatorPolicy.toThreemaSafeSendPolicy(),
        )

    private fun ContactModelData.isHidden() = when (acquaintanceLevel) {
        AcquaintanceLevel.DIRECT -> false
        AcquaintanceLevel.GROUP_OR_DELETED -> true
    }

    private fun ContactModelData.isPrivate() = when (conversationCategory) {
        ConversationCategory.DEFAULT -> false
        ConversationCategory.PROTECTED -> true
    }

    private fun VerificationLevel.toThreemaSafeVerificationLevel(): ThreemaSafeVerificationLevel = when (this) {
        VerificationLevel.UNVERIFIED -> ThreemaSafeVerificationLevel.UNVERIFIED
        VerificationLevel.SERVER_VERIFIED -> ThreemaSafeVerificationLevel.SERVER_VERIFIED
        VerificationLevel.FULLY_VERIFIED -> ThreemaSafeVerificationLevel.FULLY_VERIFIED
    }

    private fun ReadReceiptPolicy.toThreemaSafeSendPolicy(): ThreemaSafeSendPolicy = when (this) {
        ReadReceiptPolicy.DEFAULT -> ThreemaSafeSendPolicy.DEFAULT
        ReadReceiptPolicy.SEND -> ThreemaSafeSendPolicy.SEND
        ReadReceiptPolicy.DONT_SEND -> ThreemaSafeSendPolicy.DONT_SEND
    }

    private fun TypingIndicatorPolicy.toThreemaSafeSendPolicy(): ThreemaSafeSendPolicy = when (this) {
        TypingIndicatorPolicy.DEFAULT -> ThreemaSafeSendPolicy.DEFAULT
        TypingIndicatorPolicy.SEND -> ThreemaSafeSendPolicy.SEND
        TypingIndicatorPolicy.DONT_SEND -> ThreemaSafeSendPolicy.DONT_SEND
    }
}
